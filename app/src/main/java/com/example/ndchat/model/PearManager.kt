package com.example.ndchat.model

import Host
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PearManager(
    private val myHost: Host,
    private val remotePeers: MutableList<Host>,
    private val onMessageReceived: (String, Host?) -> Unit
) {
    private val TAG = "PearManager"

    private val peerSocketMap = ConcurrentHashMap<UUID, Socket>()
    private val peerWriterMap = ConcurrentHashMap<UUID, BufferedWriter>()
    private var serverSocket: ServerSocket? = null
    private val incomingSockets = ConcurrentHashMap<Socket, Boolean>()

    // ---------------------------
    // START / STOP
    // ---------------------------
    fun start() {
        Log.d(TAG, "MANAGER: Starting PearManager for Host ${myHost.pearName} [${myHost.uuid}] on Port ${myHost.portNumber}")
        Thread { startServer() }.start()
        remotePeers.forEach { peer -> connectToPeer(peer) }
    }

    fun stop() {
        Log.d(TAG, "MANAGER: Stopping PearManager. Closing all connections.")

        peerSocketMap.forEach { (_, socket) ->
            if (socket.isConnected && !socket.isClosed) sendRawMessage(socket, "DISCONNECT|${myHost.uuid}")
        }

        serverSocket?.close()
        peerSocketMap.values.forEach { it.close() }
        peerWriterMap.values.forEach { it.close() }
        incomingSockets.keys.forEach { it.close() }

        peerSocketMap.clear()
        peerWriterMap.clear()
        incomingSockets.clear()
    }

    // ---------------------------
    // SERVER
    // ---------------------------
    private fun startServer() {
        try {
            serverSocket = ServerSocket(myHost.portNumber, 10)
            Log.d(TAG, "SERVER: Listening on port ${myHost.portNumber}...")
            while (true) {
                val client = serverSocket!!.accept()
                incomingSockets[client] = true
                Log.d(TAG, "SERVER: Incoming connection accepted from ${client.inetAddress.hostAddress}:${client.port}")
                Thread {
                    sendHandshake(client)
                    handleIncoming(client)
                }.start()
            }
        } catch (e: IOException) {
            if (serverSocket != null && !serverSocket!!.isClosed) {
                Log.e(TAG, "SERVER: Error during server operation: ${e.message}", e)
            } else {
                Log.d(TAG, "SERVER: Server socket closed.")
            }
        }
    }

    // ---------------------------
    // CLIENT CONNECTION (Outgoing) with auto-reconnect
    // ---------------------------
    private fun connectToPeer(peer: Host) {
        Thread {
            while (true) {
                try {
                    Log.d(TAG, "CLIENT: Attempting connection to ${peer.pearName} [${peer.uuid}] at ${peer.hostName}:${peer.portNumber}")
                    val socket = Socket(peer.hostName, peer.portNumber)
                    Log.d(TAG, "CLIENT: Connected to ${peer.pearName}")

                    val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                    peerWriterMap[peer.uuid] = writer
                    peerSocketMap[peer.uuid] = socket

                    sendHandshake(socket)
                    handleIncomingWithReconnect(peer, socket)
                    break
                } catch (e: IOException) {
                    Log.w(TAG, "CLIENT: Failed to connect to ${peer.pearName}, retrying in 5s...")
                    Thread.sleep(5000)
                }
            }
        }.start()
    }

    // ---------------------------
    // HANDSHAKE
    // ---------------------------
    private fun sendHandshake(socket: Socket) {
        val handshakeMsg = "HANDSHAKE|${myHost.uuid}|${myHost.pearName}|${myHost.hostName}|${myHost.portNumber}"
        Log.d(TAG, "SENDER: Sending handshake to ${socket.inetAddress.hostAddress}:${socket.port}")
        sendRawMessage(socket, handshakeMsg)
    }

    private fun handleHandshake(parts: List<String>, socket: Socket) {
        if (parts.size != 5) return
        try {
            val uuid = UUID.fromString(parts[1])
            val newPeer = Host(parts[2], parts[3], parts[4].toInt(), uuid)
            updatePeerConnection(newPeer, socket)
            incomingSockets.remove(socket)
            Log.i(TAG, "HANDSHAKE: Peer ${newPeer.pearName} [${uuid}] connected/updated.")
            onMessageReceived("${newPeer.pearName} connected", null)
        } catch (e: Exception) {
            Log.e(TAG, "HANDSHAKE: Error parsing handshake: ${e.message}")
        }
    }

    private fun updatePeerConnection(updatedPeer: Host, socket: Socket) {
        val oldPeer = remotePeers.find { it.uuid == updatedPeer.uuid }
        synchronized(remotePeers) {
            if (oldPeer != null) {
                val index = remotePeers.indexOfFirst { it.uuid == updatedPeer.uuid }
                if (index != -1) remotePeers[index] = updatedPeer
                Log.d(TAG, "PEER_MGT: Host details updated for ${updatedPeer.pearName}.")
            } else {
                remotePeers.add(updatedPeer)
                Log.d(TAG, "PEER_MGT: Added new host ${updatedPeer.pearName} to remotePeers list.")
            }
        }

        peerSocketMap[updatedPeer.uuid]?.takeIf { it != socket }?.close().also {
            Log.d(TAG, "PEER_MGT: Closed old socket for ${updatedPeer.pearName}.")
        }
        peerSocketMap[updatedPeer.uuid] = socket
        peerWriterMap[updatedPeer.uuid] = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
    }

    // ---------------------------
    // DISCONNECT HANDLER
    // ---------------------------
    private fun handleDisconnect(parts: List<String>) {
        if (parts.size != 2) return
        try {
            val peerUuid = UUID.fromString(parts[1])
            val peer = remotePeers.find { it.uuid == peerUuid }
            if (peer != null) cleanUpPeerConnection(peer, shouldReconnect = true)
            else Log.w(TAG, "DISCONNECT_REC: Unknown UUID: $peerUuid")
        } catch (e: Exception) {
            Log.e(TAG, "DISCONNECT_REC: Error parsing disconnect message: ${e.message}")
        }
    }

    private fun cleanUpPeerConnection(peer: Host, shouldReconnect: Boolean = false) {
        remotePeers.remove(peer)
        peerSocketMap.remove(peer.uuid)?.close()
        peerWriterMap.remove(peer.uuid)?.close()
        onMessageReceived("${peer.pearName} disconnected", null)

        if (shouldReconnect) {
            Log.d(TAG, "MANAGER: Scheduling automatic reconnect to ${peer.pearName} in 5s...")
            Thread.sleep(5000)
            connectToPeer(peer)
        }
    }

    // ---------------------------
    // HANDLE INCOMING (Server/Client)
    // ---------------------------
    private fun handleIncoming(socket: Socket) {
        val remoteAddr = socket.inetAddress.hostAddress
        val remotePort = socket.port
        val connectionId = "$remoteAddr:$remotePort"
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            var line: String?
            Log.d(TAG, "HANDLER: Starting listener for $connectionId.")
            while (reader.readLine().also { line = it } != null) {
                val msg = line!!
                val parts = msg.split("|")
                when (parts[0]) {
                    "HANDSHAKE" -> handleHandshake(parts, socket)
                    "DISCONNECT" -> handleDisconnect(parts)
                    else -> {
                        val sender = peerSocketMap.entries.find { it.value == socket }?.key
                            ?.let { uuid -> synchronized(remotePeers) { remotePeers.find { it.uuid == uuid } } }
                        onMessageReceived(msg, sender)
                    }
                }
            }
        } catch (e: IOException) {
            Log.i(TAG, "HANDLER: Connection closed for $connectionId.")
        } finally {
            peerSocketMap.entries.removeIf { it.value == socket }
            incomingSockets.remove(socket)
            try { socket.close() } catch (_: IOException) {}
            Log.d(TAG, "HANDLER: Socket cleaned up and closed for $connectionId.")
        }
    }

    // ---------------------------
    // HANDLE INCOMING WITH AUTO-RECONNECT (Client)
    // ---------------------------
    private fun handleIncomingWithReconnect(peer: Host, socket: Socket) {
        Thread {
            val remoteAddr = socket.inetAddress.hostAddress
            val remotePort = socket.port
            val connectionId = "$remoteAddr:$remotePort"

            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                var line: String?
                Log.d(TAG, "HANDLER: Starting listener for $connectionId.")
                while (reader.readLine().also { line = it } != null) {
                    val msg = line!!
                    val parts = msg.split("|")
                    when (parts[0]) {
                        "HANDSHAKE" -> handleHandshake(parts, socket)
                        "DISCONNECT" -> handleDisconnect(parts)
                        else -> {
                            val sender = peerSocketMap.entries.find { it.value == socket }?.key
                                ?.let { uuid -> synchronized(remotePeers) { remotePeers.find { it.uuid == uuid } } }
                            onMessageReceived(msg, sender)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.i(TAG, "HANDLER: Connection lost for $connectionId. Attempting reconnect...")
            } finally {
                peerSocketMap.remove(peer.uuid)
                peerWriterMap.remove(peer.uuid)
                incomingSockets.remove(socket)
                try { socket.close() } catch (_: IOException) {}
                Log.d(TAG, "HANDLER: Socket closed for $connectionId. Reconnecting in 5s...")
                Thread.sleep(5000)
                connectToPeer(peer)
            }
        }.start()
    }

    // ---------------------------
    // SEND MESSAGES
    // ---------------------------
    private fun sendRawMessage(socket: Socket, message: String) {
        Thread {
            try {
                BufferedWriter(OutputStreamWriter(socket.getOutputStream())).use { writer ->
                    writer.write(message)
                    writer.newLine()
                    writer.flush()
                }
            } catch (_: IOException) {}
        }.start()
    }

    private fun sendMessage(socket: Socket, message: String, peerUuid: UUID) {
        Thread {
            try {
                val writer = peerWriterMap[peerUuid] ?: return@Thread
                synchronized(writer) {
                    writer.write(message)
                    writer.newLine()
                    writer.flush()
                }
                Log.d(TAG, "SENDER: Sent message to ${socket.inetAddress.hostAddress}:${socket.port}: ${message.take(30)}...")
            } catch (e: IOException) {
                Log.e(TAG, "SENDER: Error sending message: ${e.message}. Peer socket may be closed.")
            }
        }.start()
    }

    fun sendToPeer(target: Host, text: String) {
        val socket = peerSocketMap[target.uuid]
        if (socket != null && socket.isConnected && !socket.isClosed) {
            sendMessage(socket, "[${target.pearName}] $text", target.uuid)
        } else {
            Log.w(TAG, "MANAGER: Cannot send to ${target.pearName}: socket not connected or found.")
        }
    }

    fun broadcast(message: String) {
        Log.d(TAG, "BROADCAST: Broadcasting message: ${message.take(30)}...")
        peerSocketMap.forEach { (uuid, socket) ->
            if (socket.isConnected && !socket.isClosed) sendMessage(socket, "[Broadcast] $message", uuid)
        }
    }

    // ---------------------------
    // PEER MANAGEMENT
    // ---------------------------
    fun addPeerToList(peer: Host) {
        Log.d(TAG, "MANAGER: Adding new peer to list: ${peer.pearName}")
        remotePeers.add(peer)
        connectToPeer(peer)
    }

    fun removePeerFromList(peer: Host) {
        Log.d(TAG, "MANAGER: Removing peer from list and disconnecting: ${peer.pearName}")
        peerSocketMap[peer.uuid]?.takeIf { !it.isClosed }?.let { sendRawMessage(it, "DISCONNECT|${myHost.uuid}") }
        cleanUpPeerConnection(peer, shouldReconnect = false)
    }

    fun updatePeer(updated: Host) {
        val oldPeer = remotePeers.find { it.uuid == updated.uuid }
        if (oldPeer != null) {
            Log.d(TAG, "MANAGER: Updating peer ${oldPeer.pearName} -> ${updated.pearName}")
            synchronized(remotePeers) {
                val index = remotePeers.indexOf(oldPeer)
                if (index != -1) remotePeers[index] = updated
            }
            peerSocketMap.remove(updated.uuid)?.close()
            peerWriterMap.remove(updated.uuid)
            connectToPeer(updated)
            onMessageReceived("Peer updated: ${updated.pearName}", null)
        } else addPeerToList(updated)
    }
}
