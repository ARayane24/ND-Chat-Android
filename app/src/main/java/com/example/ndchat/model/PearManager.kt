package com.example.ndchat.model

import Host
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext


// ---------------- NETWORK PACKET ----------------
// Wrapper for all network messages (HANDSHAKE, MESSAGE, DISCONNECT)
data class NetworkPacket(
    val type: String,    // HANDSHAKE | MESSAGE | DISCONNECT
    val payload: String  // JSON representation of the actual data
)

// Represents the payload for a HANDSHAKE packet
data class HandshakePayload(
    val uuid: UUID,
    val name: String,
    val ip: String,
    val port: Int
)

// ---------------- PEER MANAGER ----------------
/**
 * Manages all peer-to-peer network connections.
 * It acts as both a server (listening for incoming connections) and a client (connecting to others).
 * Renamed from PearManager to PeerManager.
 */
class PeerManager(
    private val myHost: Host,
    // List of peers to connect to. Needs to be mutable and synchronized/thread-safe externally
    private val remotePeers: MutableList<Host>,
    private val onMessageReceived: (Message, Host?) -> Unit
) {

    private val TAG = "PeerManager"
    private val gson = Gson()

    // Background coroutine scope for all network operations.
    // Uses Dispatchers.IO for blocking I/O (sockets) and SupervisorJob for fault tolerance.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Thread-safe maps to store connection details, keyed by "IP:PORT"
    private val peerSocketMap = ConcurrentHashMap<String, Socket>()
    private val peerWriterMap = ConcurrentHashMap<String, BufferedWriter>()
    private val peerUuidMap = ConcurrentHashMap<String, UUID>()

    private var serverSocket: ServerSocket? = null

    // Helper function to create a unique key for a peer
    private fun peerKey(ip: String, port: Int) = "$ip:$port"

    // ---------------- START / STOP ----------------

    /**
     * Starts the PeerManager by initiating the server listener and connecting to known peers.
     */
    fun start() {
        Log.i(TAG, "Starting PeerManager...")
        // 1. Start the server in a coroutine
        scope.launch { startServer() }

        // 2. Connect to all initially configured remote peers
        remotePeers.forEach { peer ->
            scope.launch { connectToPeer(peer) }
        }
    }

    /**
     * Shuts down all connections and stops the server.
     */
    fun stop() {
        Log.i(TAG, "Stopping PeerManager...")
        // 1. Notify all connected peers of the disconnect
        val disconnectPacket = NetworkPacket("DISCONNECT", "")
        peerWriterMap.values.forEach { writer ->
            runCatching {
                // Synchronization is required because multiple threads could access the writer
                synchronized(writer) {
                    writer.write(gson.toJson(disconnectPacket))
                    writer.newLine()
                    writer.flush()
                }
            }
        }

        // 2. Cancel the coroutine scope, stopping all running jobs
        scope.cancel()

        // 3. Close all sockets and clear maps
        peerSocketMap.values.forEach { runCatching { it.close() } }
        peerSocketMap.clear()
        peerWriterMap.clear()
        peerUuidMap.clear()

        // 4. Close the server socket
        runCatching { serverSocket?.close() }
        Log.i(TAG, "PeerManager stopped.")
    }

    // ---------------- SERVER ----------------

    /**
     * Listens for incoming peer connections in a continuous loop.
     */
    private suspend fun startServer() {
        serverSocket = runCatching { ServerSocket(myHost.portNumber) }
            .getOrElse {
                Log.e(TAG, "Failed to start server on port ${myHost.portNumber}", it)
                return // Exit if server socket fails to initialize
            }

        Log.i(TAG, "Server listening on ${myHost.portNumber}")

        while (coroutineContext.isActive) {
            runCatching {
                // ServerSocket.accept() is a blocking call, which is fine inside Dispatchers.IO
                val socket = serverSocket!!.accept()
                // Handle the new connection in a separate coroutine
                scope.launch { handleConnection(socket, false) }
            }.onFailure {
                // If the context is cancelled, this is expected (e.g., in stop())
                if (coroutineContext.isActive) {
                    Log.e(TAG, "Server accept failed", it)
                }
            }
        }
        Log.i(TAG, "Server listener finished.")
    }

    // ---------------- CLIENT ----------------

    /**
     * Attempts to establish a connection to a remote peer.
     * Includes a retry mechanism (delay) for robustness.
     */
    private suspend fun connectToPeer(peer: Host) {
        val key = peerKey(peer.hostName, peer.portNumber)
        // Prevent connecting twice to the same peer (IP:PORT)
        if (peerSocketMap.containsKey(key)) return

        while (coroutineContext.isActive) {
            try {
                // Socket(host, port) is a blocking call (inside Dispatchers.IO)
                val socket = Socket(peer.hostName, peer.portNumber)
                Log.i(TAG, "Client connected to $key")
                // Handle the connection, marking this side as the initiator
                handleConnection(socket, true)
                break // Exit the retry loop upon successful connection
            } catch (e: Exception) {
                // Log and retry if connection fails (e.g., peer not yet listening)
                Log.w(TAG, "Connection failed to $key. Retrying in 3s...", e)
                delay(3000)
            }
        }
    }

    // ---------------- CONNECTION HANDLING ----------------

    /**
     * Reads and processes packets from a connected socket until the connection is closed.
     * @param socket The established socket connection.
     * @param isInitiator True if this instance initiated the connection (client side).
     */
    private suspend fun handleConnection(
        socket: Socket,
        isInitiator: Boolean
    ) {
        val remoteAddress = "${socket.inetAddress.hostAddress}:${socket.port}"
        Log.d(TAG, "Handling connection from $remoteAddress. Initiator: $isInitiator")

        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

        var key: String? = null
        var peerName: String? = null

        try {
            // Initiator sends the handshake first
            if (isInitiator) sendHandshake(writer)

            while (coroutineContext.isActive) {
                // reader.readLine() is a blocking call (inside Dispatchers.IO)
                val line = reader.readLine()
                if (line == null) {
                    // Stream closed gracefully by remote peer
                    Log.i(TAG, "$remoteAddress disconnected gracefully.")
                    break
                }

                // Parse the incoming packet
                val packet = gson.fromJson(line, NetworkPacket::class.java)

                when (packet.type) {

                    "HANDSHAKE" -> {
                        val payload = gson.fromJson(packet.payload, HandshakePayload::class.java)

                        val uuid = payload.uuid
                        peerName = payload.name
                        val ip = payload.ip
                        val port = payload.port

                        key = peerKey(ip, port)

                        // Register connection details
                        peerSocketMap[key!!] = socket
                        peerWriterMap[key!!] = writer
                        peerUuidMap[key!!] = uuid

                        // Create/update the Host object in the main peer list
                        val host = Host(peerName, ip, port, uuid)
                        synchronized(remotePeers) {
                            val index = remotePeers.indexOfFirst {
                                peerKey(it.hostName, it.portNumber) == key
                            }
                            if (index >= 0) remotePeers[index] = host
                            else remotePeers.add(host)
                        }

                        // If we are the server, we must acknowledge the connection with our own handshake
                        if (!isInitiator) sendHandshake(writer)

                        // Notify the UI that a peer has joined
                        withContext(Dispatchers.Main) {
                            onMessageReceived(
                                Message(sender = null, message = "$peerName joined the chat", isSentByMe = false),
                                null
                            )
                        }

                        Log.i(TAG, "Handshake complete with $peerName ($key)")
                    }

                    "MESSAGE" -> {
                        // Deserialize the Message object from the packet payload
                        val message = gson.fromJson(packet.payload, Message::class.java)

                        // Find the Host object corresponding to the current connection key
                        val sender = key?.let { k ->
                            synchronized(remotePeers) {
                                remotePeers.find { peerKey(it.hostName, it.portNumber) == k }
                            }
                        }

                        // Pass the message to the application callback
                        withContext(Dispatchers.Main) {
                            message.isSentByMe = false
                            onMessageReceived(message, sender)
                        }
                        Log.v(TAG, "Message received from ${sender?.pearName ?: key}: ${message.message}")
                    }

                    "DISCONNECT" -> {
                        Log.i(TAG, "Received DISCONNECT from ${peerName ?: remoteAddress}.")
                        break // Exit the reading loop
                    }
                }
            }
        } catch (e: Exception) {
            // Log unexpected connection errors
            Log.e(TAG, "Error in connection handler for ${peerName ?: remoteAddress}", e)
        } finally {
            // Always ensure resources are cleaned up
            key?.let { removePeer(it) }
            runCatching { socket.close() }

            if (peerName != null) {
                // Notify UI that a peer has left (if they completed handshake)
                withContext(Dispatchers.Main) {
                    onMessageReceived(
                        Message(sender = null, message = "$peerName left the chat", isSentByMe = false),
                        null
                    )
                }
            }
        }
    }

    /**
     * Helper to consistently remove a peer's connection data.
     */
    private fun removePeer(key: String) {
        // Close the socket and remove it from all tracking maps
        peerSocketMap.remove(key)?.close()
        peerWriterMap.remove(key)
        peerUuidMap.remove(key)

        // Remove from the list of known peers
        synchronized(remotePeers) {
            remotePeers.removeIf { peerKey(it.hostName, it.portNumber) == key }
        }
        Log.d(TAG, "Peer $key removed from active connections.")
    }

    // ---------------- HANDSHAKE ----------------

    /**
     * Sends the local Host information to the connected peer.
     * Uses JSON payload for robust data transfer.
     */
    private fun sendHandshake(writer: BufferedWriter) {
        val payload = HandshakePayload(
            uuid = myHost.uuid,
            name = myHost.pearName,
            ip = myHost.hostName, // Note: This IP is what the peer should use to identify us
            port = myHost.portNumber
        )

        val packet = NetworkPacket("HANDSHAKE", gson.toJson(payload))

        // Ensure thread safety when writing to the stream
        scope.launch { sendPacket(writer, packet) }
    }

    // ---------------- MESSAGING ----------------

    /**
     * Sends a chat message to a specific peer.
     */
    fun sendToPeer(peer: Host, message: Message) {
        scope.launch {
            val writer =
                peerWriterMap[peerKey(peer.hostName, peer.portNumber)]
            if (writer == null) {
                Log.w(TAG, "Attempted to send message to an unconnected peer: ${peer.hostName}")
                return@launch
            }

            val packet = NetworkPacket(
                type = "MESSAGE",
                payload = gson.toJson(message)
            )

            sendPacket(writer, packet)
        }
    }

    /**
     * Sends a chat message to all currently connected peers.
     */
    fun broadcast(message: Message) {
        scope.launch {
            val packet = NetworkPacket(
                type = "MESSAGE",
                payload = gson.toJson(message)
            )

            // Iterate through all connected writers and send the packet
            peerWriterMap.values.forEach { writer ->
                sendPacket(writer, packet)
            }
        }
    }

    /**
     * Low-level function to write and flush a NetworkPacket to a BufferedWriter.
     */
    private suspend fun sendPacket(writer: BufferedWriter, packet: NetworkPacket) =
        withContext(Dispatchers.IO) {
            // Synchronization is crucial here to prevent concurrent writes from corrupting the stream
            synchronized(writer) {
                runCatching {
                    writer.write(gson.toJson(packet))
                    writer.newLine()
                    writer.flush()
                }.onFailure {
                    Log.e(TAG, "Failed to send packet to peer.", it)
                    // Note: A more complete solution would identify the disconnected peer key
                    // and call removePeer(key) here.
                }
            }
        }

    // ---------------- PEER MANAGEMENT ----------------

    /**
     * Adds a new peer to the list and immediately attempts to connect to it.
     */
    fun addPeerToList(peer: Host) {
        val key = peerKey(peer.hostName, peer.portNumber)
        synchronized(remotePeers) {
            // Only add and connect if the peer is not already in the list
            if (remotePeers.none { peerKey(it.hostName, it.portNumber) == key }) {
                remotePeers.add(peer)
                scope.launch { connectToPeer(peer) }
            }
        }
    }

    /**
     * Explicitly removes a peer from the list and closes the active connection if one exists.
     */
    fun removePeerFromList(peer: Host) {
        val key = peerKey(peer.hostName, peer.portNumber)
        removePeer(key)
    }

    /**
     * Used when a peer's details (like name) are updated.
     * Implemented by removing the old entry and adding the new one, which also closes/re-opens the socket.
     */
    fun updatePeer(peer: Host) {
        // Close and remove the old connection details first
        removePeerFromList(peer)
        // Add the updated peer, which triggers a new connection attempt
        addPeerToList(peer)
    }
}
