package com.example.ndchat.model

import Host
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    // Scope for all background network operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val peerSocketMap = ConcurrentHashMap<UUID, Socket>()
    private val peerWriterMap = ConcurrentHashMap<UUID, BufferedWriter>()
    private var serverSocket: ServerSocket? = null

    // ---------------------------
    // START / STOP
    // ---------------------------
    fun start() {
        Log.d(TAG, "MANAGER: Starting PearManager for ${myHost.pearName} on Port ${myHost.portNumber}")

        // Start Server
        scope.launch { startServer() }
        Log.d(TAG, "MANAGER: Server coroutine launched.")

        // Connect to existing peers
        remotePeers.forEach { peer ->
            scope.launch { connectToPeer(peer) }
            Log.d(TAG, "MANAGER: Launched connection attempt for peer: ${peer.pearName}")
        }
        Log.i(TAG, "MANAGER: Initialization complete. Waiting for connections.")
    }

    fun stop() {
        Log.d(TAG, "MANAGER: Stopping. Closing all connections.")
        scope.cancel() // Cancels all active coroutines (server loop, client loops)
        Log.d(TAG, "MANAGER: Coroutine scope cancelled.")

        // Send disconnect to everyone
        peerSocketMap.forEach { (uuid, socket) ->
            if (socket.isConnected) {
                // We use a separate thread/scope here because the main scope is cancelled
                GlobalScope.launch(Dispatchers.IO) {
                    sendRawMessage(socket, "DISCONNECT|${myHost.uuid}")
                    Log.d(TAG, "MANAGER: Sent DISCONNECT signal to $uuid.")
                }
            }
        }

        try {
            serverSocket?.close()
            peerSocketMap.values.forEach { it.close() }
            peerWriterMap.values.forEach { it.close() }
            Log.d(TAG, "MANAGER: All sockets and streams explicitly closed.")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing resources during stop: ${e.message}")
        }

        peerSocketMap.clear()
        peerWriterMap.clear()
        Log.i(TAG, "MANAGER: PearManager stopped and resources released.")
    }

    // ---------------------------
    // SERVER (Listening for incoming)
    // ---------------------------
    private suspend fun startServer() {
        withContext(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(myHost.portNumber)
                Log.i(TAG, "SERVER: Successfully bound to port ${myHost.portNumber}. Awaiting connections...")

                while (isActive) { // Coroutine check
                    val client = serverSocket!!.accept()
                    Log.i(TAG, "SERVER: Incoming connection accepted from ${client.inetAddress.hostAddress}:${client.port}")

                    // Handle each client in a new coroutine
                    launch {
                        try {
                            Log.d(TAG, "SERVER: Starting handshake and handler for new client.")
                            sendHandshake(client)
                            handleConnection(client, isInitiator = false, isActive = isActive)
                        } catch (e: Exception) {
                            Log.e(TAG, "SERVER: Error handling client from ${client.inetAddress.hostAddress}: ${e.message}", e)
                            client.close()
                        }
                    }
                }
            } catch (e: IOException) {
                if (isActive) Log.e(TAG, "SERVER: Error during server loop (Socket closed unexpectedly?): ${e.message}", e)
                else Log.d(TAG, "SERVER: Loop terminated due to scope cancellation.")
            }
        }
    }

    // ---------------------------
    // CLIENT (Outgoing Connection)
    // ---------------------------
    private suspend fun connectToPeer(peer: Host) {
        withContext(Dispatchers.IO) {
            while (isActive) {
                try {
                    Log.d(TAG, "CLIENT: Attempting connection to ${peer.pearName} at ${peer.hostName}:${peer.portNumber}")
                    val socket = Socket(peer.hostName, peer.portNumber)

                    if (socket.isConnected) {
                        Log.i(TAG, "CLIENT: Successfully connected to ${peer.pearName}")

                        sendHandshake(socket)

                        handleConnection(socket, isInitiator = true, associatedPeer = peer , isActive)

                        Log.w(TAG, "CLIENT: Handler for ${peer.pearName} returned. Connection lost or closed.")
                    } else {
                        // This case is rare if the Socket constructor succeeds but it ensures we retry.
                        Log.w(TAG, "CLIENT: Connection failed (socket isn't connected after constructor). Retrying in 5s...")
                    }

                } catch (e: IOException) {
                    Log.w(TAG, "CLIENT: Failed to connect to ${peer.pearName}. Retrying in 5s... Error: ${e.message}")
                }

                // Only delay if we need to retry
                delay(5000)
            }
        }
    }

    // ---------------------------
    // UNIFIED CONNECTION HANDLER
    // ---------------------------
    private suspend fun handleConnection(
        socket: Socket,
        isInitiator: Boolean,
        associatedPeer: Host? = null,
        isActive: Boolean
    ) {
        val remoteAddr = socket.inetAddress.hostAddress
        var currentPeerUuid: UUID? = associatedPeer?.uuid
        val connectionType = if (isInitiator) "OUTGOING" else "INCOMING"
        Log.d(TAG, "HANDLER ($connectionType): Starting listener for $remoteAddr")

        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

            if (isInitiator && currentPeerUuid != null) {
                peerSocketMap[currentPeerUuid] = socket
                peerWriterMap[currentPeerUuid] = writer
                Log.d(TAG, "HANDLER ($connectionType): Registered maps for expected peer $currentPeerUuid.")
            }

            var line: String?
            while (isActive) {
                line = reader.readLine()
                if (line == null) {
                    Log.i(TAG, "HANDLER ($connectionType): EOF reached. Peer closed the connection.")
                    break
                }

                val parts = line.split("|")
                when (parts[0]) {
                    "HANDSHAKE" -> {
                        try {
                            val uuid = UUID.fromString(parts[1])
                            val name = parts[2]
                            currentPeerUuid = uuid
                            Log.i(TAG, "HANDSHAKE: Valid handshake received from $name ($uuid).")

                            peerSocketMap[uuid] = socket
                            peerWriterMap[uuid] = writer

                            synchronized(remotePeers) {
                                val existingPeer = remotePeers.find { it.uuid == uuid }
                                if (existingPeer != null) {
                                    Log.d(TAG, "HANDSHAKE: Peer $name already known. Details updated.")
                                    val index = remotePeers.indexOf(existingPeer)
                                    remotePeers[index] = Host(name, socket.inetAddress.hostAddress, socket.port, uuid)
                                } else {
                                    Log.d(TAG, "HANDSHAKE: New peer $name added to remotePeers list.")
                                    remotePeers.add(Host(name, socket.inetAddress.hostAddress, socket.port, uuid))
                                }
                            }
                            onMessageReceived("$name joined the chat", null)
                        } catch (e: Exception) {
                            Log.e(TAG, "HANDSHAKE: Malformed data received: ${line}. Error: ${e.message}")
                        }
                    }
                    "DISCONNECT" -> {
                        Log.i(TAG, "DISCONNECT: Peer $currentPeerUuid requested disconnect.")
                        break
                    }
                    else -> {
                        Log.d(TAG, "MESSAGE_REC: Received message (Type: ${parts[0]}).")
                        val senderHost = currentPeerUuid?.let { uuid ->
                            synchronized(remotePeers) { remotePeers.find { it.uuid == uuid } }
                        }
                        onMessageReceived(line, senderHost)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "HANDLER ($connectionType): Fatal IO/Network Error: ${e.message}", e)
        } finally {
            Log.w(TAG, "HANDLER ($connectionType): Listener stopped for $remoteAddr. Cleaning up...")
            currentPeerUuid?.let {
                peerSocketMap.remove(it)
                peerWriterMap.remove(it)
                Log.d(TAG, "HANDLER: Removed maps entries for UUID $it.")
            }
            try { socket.close() } catch (_: Exception) {}
            Log.d(TAG, "HANDLER: Socket closed.")
        }
    }

    // ---------------------------
    // MESSAGING
    // ---------------------------
    private fun sendHandshake(socket: Socket) {
        val msg = "HANDSHAKE|${myHost.uuid}|${myHost.pearName}|${myHost.hostName}|${myHost.portNumber}"
        sendRawMessage(socket, msg)
        Log.d(TAG, "SENDER: Sent handshake to ${socket.inetAddress.hostAddress}:${socket.port}.")
    }

    private fun sendRawMessage(socket: Socket, message: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                writer.write(message)
                writer.newLine()
                writer.flush()
                Log.v(TAG, "SENDER: Raw message sent successfully: ${message.take(20)}...")
            } catch (e: Exception) {
                Log.e(TAG, "SENDER: Error sending raw message to ${socket.inetAddress.hostAddress}: ${e.message}")
            }
        }
    }

    fun sendToPeer(target: Host, text: String) {
        val writer = peerWriterMap[target.uuid]
        if (writer != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    synchronized(writer) {
                        writer.write("[${myHost.pearName}] $text")
                        writer.newLine()
                        writer.flush()
                    }
                    Log.d(TAG, "SENDER: Message sent to ${target.pearName}: ${text.take(20)}...")
                } catch (e: Exception) {
                    Log.e(TAG, "SENDER: Failed to send to ${target.pearName}. Error: ${e.message}")
                }
            }
        } else {
            Log.w(TAG, "SENDER: Cannot send to ${target.pearName}. Writer not found or connection is inactive.")
        }
    }

    fun broadcast(message: String) {
        Log.i(TAG, "BROADCAST: Starting broadcast for message: ${message.take(20)}...")

        // Ensure all network write operations happen asynchronously on the IO dispatcher
        scope.launch(Dispatchers.IO) {
            val fullMessage = "[${myHost.pearName} | Broadcast] $message"
            var successCount = 0

            peerSocketMap.forEach { (uuid, socket) ->
                val writer = peerWriterMap[uuid]

                if (socket.isConnected && !socket.isClosed && writer != null) {
                    try {
                        synchronized(writer) {
                            writer.write(fullMessage)
                            writer.newLine()
                            writer.flush()
                        }
                        successCount++
                    } catch (e: IOException) {
                        Log.e(TAG, "BROADCAST: Error sending to peer $uuid. Connection likely broken. Error: ${e.message}")
                    }
                } else {
                    Log.w(TAG, "BROADCAST: Skipping peer $uuid (socket status: Connected=${socket.isConnected}, Closed=${socket.isClosed})")
                }
            }
            Log.i(TAG, "BROADCAST: Finished. Successfully sent to $successCount peer(s).")
        }
    }

    // ---------------------------
    // PEER MANAGEMENT
    // ---------------------------

    fun addPeerToList(peer: Host) {
        Log.d(TAG, "PEER_MGT: Request to add new peer: ${peer.pearName} [${peer.uuid}]")

        synchronized(remotePeers) {
            if (!remotePeers.any { it.uuid == peer.uuid }) {
                remotePeers.add(peer)
                Log.i(TAG, "PEER_MGT: Added ${peer.pearName} to remotePeers list.")
            } else {
                Log.w(TAG, "PEER_MGT: Peer ${peer.pearName} already exists in list. Skipping list add.")
            }
        }

        scope.launch { connectToPeer(peer) }
        Log.d(TAG, "PEER_MGT: Launched connect routine for ${peer.pearName}.")
    }

    fun removePeerFromList(peer: Host) {
        Log.d(TAG, "PEER_MGT: Request to remove peer: ${peer.pearName} [${peer.uuid}]")

        scope.launch(Dispatchers.IO) {
            peerSocketMap[peer.uuid]?.takeIf { !it.isClosed }?.let { socket ->
                sendRawMessage(socket, "DISCONNECT|${myHost.uuid}")
                Log.d(TAG, "PEER_MGT: Sent explicit DISCONNECT signal to ${peer.pearName}.")
            }

            synchronized(remotePeers) {
                if (remotePeers.removeIf { it.uuid == peer.uuid }) {
                    Log.i(TAG, "PEER_MGT: Successfully removed ${peer.pearName} from remotePeers list.")
                } else {
                    Log.w(TAG, "PEER_MGT: Peer ${peer.pearName} not found in remotePeers list for removal.")
                }
            }

            cleanUpPeerConnection(peer)

            onMessageReceived("${peer.pearName} removed", null)
        }
    }

    private fun cleanUpPeerConnection(peer: Host) {
        peerSocketMap.remove(peer.uuid)?.close().also {
            if (it != null) Log.d(TAG, "PEER_MGT: Closed socket for ${peer.pearName}.")
        }
        peerWriterMap.remove(peer.uuid)?.close().also {
            if (it != null) Log.d(TAG, "PEER_MGT: Closed writer for ${peer.pearName}.")
        }
    }

    fun updatePeer(updated: Host) {
        Log.d(TAG, "PEER_MGT: Request to update peer: ${updated.pearName} [${updated.uuid}]")

        scope.launch {
            var existingHost: Host? = null

            synchronized(remotePeers) {
                existingHost = remotePeers.find { it.uuid == updated.uuid }
                if (existingHost != null) {
                    val index = remotePeers.indexOfFirst { it.uuid == updated.uuid }
                    remotePeers[index] = updated
                    Log.i(TAG, "PEER_MGT: Updated details for existing peer ${updated.pearName}.")
                }
            }

            if (existingHost != null) {
                Log.d(TAG, "PEER_MGT: Closing old connection before reconnecting with new details.")
                peerSocketMap.remove(updated.uuid)?.close()
                peerWriterMap.remove(updated.uuid)

                connectToPeer(updated)
                onMessageReceived("Peer details updated: ${updated.pearName}", null)
            } else {
                Log.w(TAG, "PEER_MGT: Peer ${updated.pearName} not found for update. Treating as new peer.")
                addPeerToList(updated)
            }
        }
    }
}