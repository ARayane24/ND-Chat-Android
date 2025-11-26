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

        // Connect to existing peers
        remotePeers.forEach { peer ->
            scope.launch { connectToPeer(peer) }
        }
    }

    fun stop() {
        Log.d(TAG, "MANAGER: Stopping. Closing all connections.")
        scope.cancel() // Cancels all active coroutines (server loop, client loops)

        // Send disconnect to everyone
        peerSocketMap.forEach { (uuid, socket) ->
            if (socket.isConnected) {
                // We use a separate thread/scope here because the main scope is cancelled
                GlobalScope.launch(Dispatchers.IO) {
                    sendRawMessage(socket, "DISCONNECT|${myHost.uuid}")
                }
            }
        }

        try {
            serverSocket?.close()
            peerSocketMap.values.forEach { it.close() }
            peerWriterMap.values.forEach { it.close() }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing resources: ${e.message}")
        }

        peerSocketMap.clear()
        peerWriterMap.clear()
    }

    // ---------------------------
    // SERVER (Listening for incoming)
    // ---------------------------
    private suspend fun startServer() {
        withContext(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(myHost.portNumber)
                Log.d(TAG, "SERVER: Listening on port ${myHost.portNumber}...")

                while (isActive) { // Coroutine check
                    val client = serverSocket!!.accept()
                    Log.d(TAG, "SERVER: Accepted connection from ${client.inetAddress.hostAddress}")

                    // Handle each client in a new coroutine
                    launch {
                        try {
                            // Immediately send handshake. No Thread.sleep needed.
                            sendHandshake(client)
                            handleConnection(client, isInitiator = false, isActive = isActive)
                        } catch (e: Exception) {
                            Log.e(TAG, "SERVER: Client error: ${e.message}")
                            client.close()
                        }
                    }
                }
            } catch (e: IOException) {
                if (isActive) Log.e(TAG, "SERVER: Error: ${e.message}")
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
                    Log.d(TAG, "CLIENT: Connecting to ${peer.pearName} at ${peer.hostName}:${peer.portNumber}")
                    // 1. Attempt connection
                    val socket = Socket(peer.hostName, peer.portNumber)

                    if (socket.isConnected) {
                        Log.d(TAG, "CLIENT: Connected to ${peer.pearName}")

                        // 2. Send Handshake immediately
                        sendHandshake(socket)

                        // 3. Pass control to the handler.
                        // This line BLOCKS until the connection is lost or closed.
                        handleConnection(socket, isInitiator = true, associatedPeer = peer , isActive)

                        // 4. If code reaches here, it means handleConnection finished (socket died)
                        Log.w(TAG, "CLIENT: Connection to ${peer.pearName} ended. Reconnecting in 5s...")
                    }
                } catch (e: IOException) {
                    // Connection failed (refused, timeout, etc.)
                    Log.w(TAG, "CLIENT: Failed to connect to ${peer.pearName}. Retrying in 5s...")
                }

                // 5. Wait before trying again (prevents CPU spam)
                delay(5000)
            }
        }
    }

    // ---------------------------
    // UNIFIED CONNECTION HANDLER
    // ---------------------------
    // Handles reading lines for both Server and Client sockets
    private suspend fun handleConnection(
        socket: Socket,
        isInitiator: Boolean,
        associatedPeer: Host? = null,
        isActive: Boolean
    ) {
        val remoteAddr = socket.inetAddress.hostAddress
        // We use a local variable for the UUID to track who we are talking to
        var currentPeerUuid: UUID? = associatedPeer?.uuid

        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

            // 1. If we are the client, register the writer immediately so we can send messages
            if (isInitiator && currentPeerUuid != null) {
                peerSocketMap[currentPeerUuid] = socket
                peerWriterMap[currentPeerUuid] = writer
            }

            // 2. Listen Loop
            var line: String?
            while (isActive) {
                line = reader.readLine()
                if (line == null) break // Connection closed by other side

                val parts = line.split("|")
                when (parts[0]) {
                    "HANDSHAKE" -> {
                        try {
                            val uuid = UUID.fromString(parts[1])
                            val name = parts[2]
                            currentPeerUuid = uuid // Update our local tracker

                            Log.i(TAG, "HANDSHAKE: Handshake valid from $name ($uuid)")

                            // Safe Map Update
                            peerSocketMap[uuid] = socket
                            peerWriterMap[uuid] = writer

                            // Safe List Update (Synchronized)
                            synchronized(remotePeers) {
                                val existingPeer = remotePeers.find { it.uuid == uuid }
                                if (existingPeer != null) {
                                    // Update existing (e.g., IP might have changed)
                                    val index = remotePeers.indexOf(existingPeer)
                                    remotePeers[index] = Host(name, socket.inetAddress.hostAddress, socket.port, uuid)
                                } else {
                                    // Add new
                                    remotePeers.add(Host(name, socket.inetAddress.hostAddress, socket.port, uuid))
                                }
                            }

                            onMessageReceived("$name joined the chat", null)
                        } catch (e: Exception) {
                            Log.e(TAG, "HANDSHAKE: Malformed data received: ${e.message}")
                        }
                    }
                    "DISCONNECT" -> {
                        Log.i(TAG, "DISCONNECT: Received disconnect signal.")
                        break // Break loop to close socket
                    }
                    else -> {
                        // Regular message
                        val senderHost = currentPeerUuid?.let { uuid ->
                            synchronized(remotePeers) { remotePeers.find { it.uuid == uuid } }
                        }
                        onMessageReceived(line, senderHost)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "HANDLER: Connection Error: ${e.message}")
        } finally {
            // Cleanup resources
            Log.d(TAG, "HANDLER: Cleaning up connection for $remoteAddr")
            currentPeerUuid?.let {
                peerSocketMap.remove(it)
                peerWriterMap.remove(it)
            }
            try { socket.close() } catch (_: Exception) {}

            // NOTE: We do NOT call connectToPeer here.
            // The function simply ends, returns to connectToPeer, which triggers the retry logic.
        }
    }
    // ---------------------------
    // MESSAGING
    // ---------------------------
    private fun sendHandshake(socket: Socket) {
        val msg = "HANDSHAKE|${myHost.uuid}|${myHost.pearName}|${myHost.hostName}|${myHost.portNumber}"
        sendRawMessage(socket, msg)
    }

    private fun sendRawMessage(socket: Socket, message: String) {
        scope.launch(Dispatchers.IO) {
            try {
                // Do NOT use 'use' block or close the writer here!
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                writer.write(message)
                writer.newLine()
                writer.flush() // Crucial! Push data immediately
            } catch (e: Exception) {
                Log.e(TAG, "SEND: Error sending raw message: ${e.message}")
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
                } catch (e: Exception) {
                    Log.e(TAG, "SEND: Failed to send to ${target.pearName}")
                }
            }
        } else {
            Log.w(TAG, "SEND: No active connection to ${target.pearName}")
        }
    }


    fun addPeerToList(peer: Host) {
        Log.d(TAG, "MANAGER: Adding new peer to list: ${peer.pearName}")

        // 1. Add to the list (Access shared mutable state safely)
        synchronized(remotePeers) {
            if (!remotePeers.any { it.uuid == peer.uuid }) {
                remotePeers.add(peer)
            }
        }

        // 2. Start connection attempt in a coroutine
        scope.launch { connectToPeer(peer) }
    }

    fun removePeerFromList(peer: Host) {
        Log.d(TAG, "MANAGER: Removing peer from list and disconnecting: ${peer.pearName}")

        scope.launch(Dispatchers.IO) {
            // 1. Send the DISCONNECT signal gracefully
            peerSocketMap[peer.uuid]?.takeIf { !it.isClosed }?.let { socket ->
                sendRawMessage(socket, "DISCONNECT|${myHost.uuid}")
            }

            // 2. Remove from the central list (Access shared mutable state safely)
            synchronized(remotePeers) {
                remotePeers.removeIf { it.uuid == peer.uuid }
            }

            // 3. Clean up the connection resources (Socket/Writer)
            cleanUpPeerConnection(peer)

            onMessageReceived("${peer.pearName} removed", null)
        }
    }

    // Helper function for cleanup (Updated from original to be suspendable)
    private fun cleanUpPeerConnection(peer: Host) {
        peerSocketMap.remove(peer.uuid)?.close()
        peerWriterMap.remove(peer.uuid)?.close()
    }

    fun updatePeer(updated: Host) {
        Log.d(TAG, "MANAGER: Attempting to update peer: ${updated.pearName}")

        // Ensure this runs off the main thread
        scope.launch {
            var existingHost: Host? = null

            // 1. Find and update the host in the main list
            synchronized(remotePeers) {
                existingHost = remotePeers.find { it.uuid == updated.uuid }
                if (existingHost != null) {
                    val index = remotePeers.indexOfFirst { it.uuid == updated.uuid }
                    if (index != -1) remotePeers[index] = updated
                    Log.d(TAG, "MANAGER: Successfully updated details for ${updated.pearName}.")
                }
            }

            if (existingHost != null) {
                // 2. Clean up old connection maps
                peerSocketMap.remove(updated.uuid)?.close()
                peerWriterMap.remove(updated.uuid)

                // 3. Initiate reconnection using the new details
                connectToPeer(updated)
                onMessageReceived("Peer details updated: ${updated.pearName}", null)
            } else {
                // If the peer wasn't found, treat it as a new peer to add
                addPeerToList(updated)
            }
        }
    }

    fun broadcast(message: String) {

        Log.d(TAG, "BROADCAST: Broadcasting message: ${message.take(30)}...")

        peerSocketMap.forEach { (uuid, socket) ->

            if (socket.isConnected && !socket.isClosed) sendRawMessage(socket, "[Broadcast] $message")

        }

    }
}