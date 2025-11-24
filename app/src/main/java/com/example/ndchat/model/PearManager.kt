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

    // Map to link Host UUID to its active client Socket (for established connections)
    private val peerSocketMap = ConcurrentHashMap<UUID, Socket>()
    private var serverSocket: ServerSocket? = null

    // Map to temporarily track sockets accepted by the server, pending a handshake (UUID identification)
    private val incomingSockets = ConcurrentHashMap<Socket, Boolean>()

    // ---------------------------
    // START / STOP
    // ---------------------------
    fun start() {
        Log.d(TAG, "MANAGER: Starting PearManager for Host ${myHost.pearName} [${myHost.uuid}] on Port ${myHost.portNumber}")
        Thread { startServer() }.start()

        remotePeers.forEach { peer ->
            Thread { connectToPeer(peer) }.start()
        }
    }

    fun stop() {
        Log.d(TAG, "MANAGER: Stopping PearManager. Closing all connections.")

        // Send DISCONNECT signal to all peers before closing the sockets
        peerSocketMap.forEach { (uuid, socket) ->
            if (socket.isConnected && !socket.isClosed) {
                // Use a non-blocking raw send for shutdown
                sendRawMessage(socket, "DISCONNECT|${myHost.uuid}")
            }
        }

        serverSocket?.close()
        peerSocketMap.values.forEach { it.close() }
        incomingSockets.keys.forEach { it.close() }
        peerSocketMap.clear()
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
                // Track the incoming socket temporarily until handshake
                incomingSockets[client] = true
                Log.d(TAG, "SERVER: Incoming connection accepted from ${client.inetAddress.hostAddress}:${client.port}")
                Thread { handleIncoming(client) }.start()
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
    // CLIENT CONNECTION (Outgoing)
    // ---------------------------
    private fun connectToPeer(peer: Host) {
        val uuid = peer.uuid
        Log.d(TAG, "CLIENT: Attempting connection to ${peer.pearName} [${uuid}] at ${peer.hostName}:${peer.portNumber}")

        while (true) {
            try {
                val socket = Socket(peer.hostName, peer.portNumber)
                Log.d(TAG, "CLIENT: Connected to ${peer.pearName}")

                // 1. Register socket immediately for outgoing connections (for sendToPeer lookup)
                peerSocketMap[uuid] = socket

                // 2. Send handshake to announce self
                sendHandshake(socket)

                // 3. Start listening for incoming data from this peer
                Thread { handleIncoming(socket) }.start()

                break
            } catch (e: IOException) {
                Log.w(TAG, "CLIENT: Failed to connect to ${peer.pearName}, retrying in 5s...")
                Thread.sleep(5000)
            }
        }
    }

    // ---------------------------
    // HANDSHAKE PROTOCOL
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
            val pearName = parts[2]
            val hostName = parts[3]
            val port = parts[4].toInt()

            // 1. Create the Host object using the provided UUID
            val newPeer = Host(pearName, hostName, port, uuid)

            // 2. Update remotePeers list and ensure proper socket registration/cleanup
            updatePeerConnection(newPeer, socket)

            // 3. Remove the socket from the temporary incoming list if it was a server accept
            incomingSockets.remove(socket)

            Log.i(TAG, "HANDSHAKE: Peer ${pearName} [${uuid}] connected/updated.")
            onMessageReceived("$pearName connected", null)
        } catch (e: Exception) {
            Log.e(TAG, "HANDSHAKE: Error parsing handshake: ${e.message}")
        }
    }

    // Centralized function to manage adding/updating peers and their sockets
    private fun updatePeerConnection(updatedPeer: Host, socket: Socket) {
        // Find existing Host in the remotePeers list
        val oldPeer = remotePeers.find { it.uuid == updatedPeer.uuid }

        synchronized(remotePeers) {
            if (oldPeer != null) {
                // Update case: Peer exists, replace data in remotePeers list
                val index = remotePeers.indexOfFirst { it.uuid == updatedPeer.uuid }
                if (index != -1) {
                    remotePeers[index] = updatedPeer
                    Log.d(TAG, "PEER_MGT: Host details updated for ${updatedPeer.pearName}.")
                }
            } else {
                // New Peer case: Add to the list
                remotePeers.add(updatedPeer)
                Log.d(TAG, "PEER_MGT: Added new host ${updatedPeer.pearName} to remotePeers list.")
            }
        }

        // If an old socket existed for this UUID, close it (handles reconnection/update)
        val oldSocket = peerSocketMap.remove(updatedPeer.uuid)
        oldSocket?.let {
            try {
                if (it != socket) { // Don't close the current, active socket
                    it.close()
                    Log.d(TAG, "PEER_MGT: Closed old socket for ${updatedPeer.pearName}.")
                }
            } catch (_: IOException) {}
        }

        // Register the new/current active socket with the Peer's UUID
        peerSocketMap[updatedPeer.uuid] = socket
    }

    // ---------------------------
    // DISCONNECT HANDLER
    // ---------------------------

    private fun handleDisconnect(parts: List<String>) {
        if (parts.size != 2) return

        try {
            val peerUuid = UUID.fromString(parts[1])
            val peer = remotePeers.find { it.uuid == peerUuid }

            if (peer != null) {
                Log.i(TAG, "DISCONNECT_REC: Received explicit disconnect from ${peer.pearName}.")
                // Use internal cleanup function to remove from list and close socket without sending reply
                cleanUpPeerConnection(peer)
            } else {
                Log.w(TAG, "DISCONNECT_REC: Received disconnect signal for unknown UUID: $peerUuid")
            }
        } catch (e: Exception) {
            Log.e(TAG, "DISCONNECT_REC: Error parsing disconnect message: ${e.message}")
        }
    }

    // Internal function to clean up socket and host without sending a disconnect signal
    private fun cleanUpPeerConnection(peer: Host) {
        // 1. Remove from remotePeers list
        remotePeers.remove(peer)

        // 2. Close and remove the socket from peerSocketMap
        val socket = peerSocketMap.remove(peer.uuid)
        socket?.let {
            try {
                it.close()
                Log.d(TAG, "CLEANUP: Closed socket for peer ${peer.pearName}.")
            } catch (_: IOException) {}
        }

        // 3. Notify UI
        onMessageReceived("${peer.pearName} disconnected", null)
    }

    // ---------------------------
    // HANDLE INCOMING MESSAGES
    // ---------------------------
    private fun handleIncoming(socket: Socket) {
        val remoteAddress = socket.inetAddress.hostAddress!!
        val remotePort = socket.port
        val connectionId = "$remoteAddress:$remotePort"

        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            var line: String?

            Log.d(TAG, "HANDLER: Starting listener for $connectionId.")
            while (reader.readLine().also { line = it } != null) {
                val msg = line!!
                val parts = msg.split("|", limit = 2)
                val command = parts[0]

                when (command) {
                    "HANDSHAKE" -> handleHandshake(msg.split("|"), socket)
                    "DISCONNECT" -> handleDisconnect(msg.split("|"))
                    else -> {
                        // Regular message handling
                        val sender = peerSocketMap.entries.find { it.value == socket }?.key?.let { uuid ->
                            synchronized(remotePeers) { remotePeers.find { it.uuid == uuid } }
                        }

                        if (sender != null) {
                            Log.d(TAG, "HANDLER: Received message from ${sender.pearName}: ${msg.take(30)}...")
                            onMessageReceived(msg, sender)
                        } else {
                            // Message arrived before successful handshake or from unknown peer
                            Log.w(TAG, "HANDLER: Received message from unknown peer on $connectionId: ${msg.take(30)}...")
                            onMessageReceived(msg, null)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.i(TAG, "HANDLER: Connection closed for $connectionId. Error: ${e.message}")
        } finally {
            // Passive cleanup: remove socket if it was never formally disconnected
            peerSocketMap.entries.removeIf { it.value == socket }
            incomingSockets.remove(socket)
            try { socket.close() } catch (_: IOException) {}
            Log.d(TAG, "HANDLER: Socket cleaned up and closed for $connectionId.")
        }
    }

    // ---------------------------
    // SEND MESSAGES
    // ---------------------------
    private fun sendRawMessage(socket: Socket, message: String) {
        Thread {
            try {
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                writer.write(message)
                writer.newLine()
                writer.flush()
            } catch (e: IOException) {
                // Log.e(TAG, "SENDER: Error sending raw message: ${e.message}. Peer socket may be closed.")
            }
        }.start()
    }

    private fun sendMessage(socket: Socket, message: String) {
        Thread {
            try {
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                writer.write(message)
                writer.newLine()
                writer.flush()
                Log.d(TAG, "SENDER: Sent message to ${socket.inetAddress.hostAddress}:${socket.port}: ${message.take(30)}...")
            } catch (e: IOException) {
                Log.e(TAG, "SENDER: Error sending message: ${e.message}. Peer socket may be closed.")
            }
        }.start()
    }

    fun broadcast(message: String) {
        Log.d(TAG, "BROADCAST: Broadcasting message: ${message.take(30)}...")
        Thread {
            peerSocketMap.values.forEach { socket ->
                sendMessage(socket, "[Broadcast] $message")
            }
            Log.d(TAG, "BROADCAST: Broadcast complete to ${peerSocketMap.size} peers.")
        }.start()
    }

    fun sendToPeer(target: Host, text: String) {
        Log.d(TAG, "MANAGER: Attempting to send message to ${target.pearName} [${target.uuid}]: ${text.take(30)}...")
        Thread {
            val socket = peerSocketMap[target.uuid]

            if (socket != null && socket.isConnected && !socket.isClosed) {
                sendMessage(socket, "[${target.pearName}] $text")
            } else {
                Log.w(TAG, "MANAGER: Cannot send to ${target.pearName}: socket not connected or found.")
            }
        }.start()
    }

    // ---------------------------
    // PEER MANAGEMENT
    // ---------------------------
    fun addPeerToList(peer: Host) {
        Log.d(TAG, "MANAGER: Adding new peer to list: ${peer.pearName}")
        remotePeers.add(peer)
        Thread { connectToPeer(peer) }.start()
    }

    fun removePeerFromList(peer: Host) {
        Log.d(TAG, "MANAGER: Removing peer from list and disconnecting: ${peer.pearName}")

        // Find socket associated with the host
        val socket = peerSocketMap[peer.uuid]

        // 1. Send explicit disconnect signal before local cleanup
        if (socket != null && !socket.isClosed) {
            sendRawMessage(socket, "DISCONNECT|${myHost.uuid}")
        }

        // 2. Perform local cleanup (removes from remotePeers, closes socket, notifies UI)
        cleanUpPeerConnection(peer)
    }

    fun updatePeer(updated: Host) {
        val oldPeer = remotePeers.find { it.uuid == updated.uuid }

        if (oldPeer != null) {
            Log.d(TAG, "MANAGER: Attempting to update peer ${oldPeer.pearName} -> ${updated.pearName}")

            // 1. Update the Host object in the list
            synchronized(remotePeers) {
                val index = remotePeers.indexOf(oldPeer)
                if (index != -1) {
                    remotePeers[index] = updated
                }
            }

            // 2. Disconnect and remove the old socket (if it exists).
            // We DO NOT send a DISCONNECT message here, as the peer is not actively stopping, just changing location/port.
            val oldSocket = peerSocketMap.remove(updated.uuid)
            if (oldSocket != null) {
                try {
                    oldSocket.close()
                    Log.d(TAG, "MANAGER: Successfully closed old socket during update for ${oldPeer.pearName}.")
                } catch (_: IOException) {}
            }

            // 3. Reconnect using updated info
            Thread { connectToPeer(updated) }.start()

            onMessageReceived("Peer updated: ${updated.pearName}", null)
        } else {
            Log.w(TAG, "MANAGER: Peer not found to update: ${updated.pearName}. Adding as new.")
            addPeerToList(updated)
        }
    }
}