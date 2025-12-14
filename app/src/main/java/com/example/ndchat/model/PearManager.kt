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

data class NetworkPacket(
    val type: String,    // HANDSHAKE | MESSAGE | DISCONNECT
    val payload: String  // JSON or raw string
)

// ---------------- PEAR MANAGER ----------------

class PearManager(
    private val myHost: Host,
    private val remotePeers: MutableList<Host>,
    private val onMessageReceived: (Message, Host?) -> Unit
) {

    private val TAG = "PearManager"
    private val gson = Gson()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val peerSocketMap = ConcurrentHashMap<String, Socket>()
    private val peerWriterMap = ConcurrentHashMap<String, BufferedWriter>()
    private val peerUuidMap = ConcurrentHashMap<String, UUID>()

    private var serverSocket: ServerSocket? = null

    private fun peerKey(ip: String, port: Int) = "$ip:$port"

    // ---------------- START / STOP ----------------

    fun start() {
        scope.launch { startServer() }
        remotePeers.forEach { peer ->
            scope.launch { connectToPeer(peer) }
        }
    }

    fun stop() {
        scope.cancel()
        peerSocketMap.values.forEach { runCatching { it.close() } }
        peerSocketMap.clear()
        peerWriterMap.clear()
        peerUuidMap.clear()
        runCatching { serverSocket?.close() }
    }

    // ---------------- SERVER ----------------

    private suspend fun startServer() {
        serverSocket = ServerSocket(myHost.portNumber)
        Log.i(TAG, "Listening on ${myHost.portNumber}")

        while (coroutineContext.isActive) {
            val socket = serverSocket!!.accept()
            scope.launch { handleConnection(socket, false) }
        }
    }

    // ---------------- CLIENT ----------------

    private suspend fun connectToPeer(peer: Host) {
        val key = peerKey(peer.hostName, peer.portNumber)
        if (peerSocketMap.containsKey(key)) return

        while (coroutineContext.isActive) {
            try {
                val socket = Socket(peer.hostName, peer.portNumber)
                sendHandshake(socket)
                handleConnection(socket, true)
                break
            } catch (_: Exception) {
                delay(3000)
            }
        }
    }

    // ---------------- CONNECTION ----------------

    private suspend fun handleConnection(
        socket: Socket,
        isInitiator: Boolean
    ) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

        var key: String? = null

        try {
            while (true) {
                val line = reader.readLine() ?: break
                val packet = gson.fromJson(line, NetworkPacket::class.java)

                when (packet.type) {

                    "HANDSHAKE" -> {
                        val parts = packet.payload.split("|")
                        val uuid = UUID.fromString(parts[0])
                        val name = parts[1]
                        val ip = parts[2]
                        val port = parts[3].toInt()

                        key = peerKey(ip, port)

                        peerSocketMap[key!!] = socket
                        peerWriterMap[key!!] = writer
                        peerUuidMap[key!!] = uuid

                        val host = Host(name, ip, port, uuid)

                        synchronized(remotePeers) {
                            val index = remotePeers.indexOfFirst {
                                it.hostName == ip && it.portNumber == port
                            }
                            if (index >= 0) remotePeers[index] = host
                            else remotePeers.add(host)
                        }

                        if (!isInitiator) sendHandshake(socket)

                        onMessageReceived(
                            Message(
                                sender = null,
                                message = "$name joined",
                                isSentByMe = false
                            ),
                            null
                        )
                    }

                    "MESSAGE" -> {
                        val message =
                            gson.fromJson(packet.payload, Message::class.java)

                        val sender = key?.let { k ->
                            synchronized(remotePeers) {
                                remotePeers.find {
                                    peerKey(it.hostName, it.portNumber) == k
                                }
                            }
                        }

                        onMessageReceived(message, sender)
                    }

                    "DISCONNECT" -> break
                }
            }
        } finally {
            key?.let {
                peerSocketMap.remove(it)
                peerWriterMap.remove(it)
                peerUuidMap.remove(it)
            }
            runCatching { socket.close() }
        }
    }

    // ---------------- HANDSHAKE ----------------

    private fun sendHandshake(socket: Socket) {
        val payload =
            "${myHost.uuid}|${myHost.pearName}|${myHost.hostName}|${myHost.portNumber}"

        val packet = NetworkPacket("HANDSHAKE", payload)

        socket.getOutputStream()
            .write((gson.toJson(packet) + "\n").toByteArray())
    }

    // ---------------- MESSAGING ----------------

    fun sendToPeer(peer: Host, message: Message) {
        scope.launch {
            val writer =
                peerWriterMap[peerKey(peer.hostName, peer.portNumber)] ?: return@launch

            val packet = NetworkPacket(
                type = "MESSAGE",
                payload = gson.toJson(message)
            )

            sendPacket(writer, packet)
        }
    }


    fun broadcast(message: Message) {
        scope.launch {
            val packet = NetworkPacket(
                type = "MESSAGE",
                payload = gson.toJson(message)
            )

            peerWriterMap.values.forEach { writer ->
                sendPacket(writer, packet)
            }
        }
    }


    private suspend fun sendPacket(writer: BufferedWriter, packet: NetworkPacket) =
        withContext(Dispatchers.IO) {
            synchronized(writer) {
                writer.write(gson.toJson(packet))
                writer.newLine()
                writer.flush()
            }
        }


    // ---------------- PEER MANAGEMENT ----------------

    fun addPeerToList(peer: Host) {
        synchronized(remotePeers) {
            if (remotePeers.none {
                    it.hostName == peer.hostName &&
                            it.portNumber == peer.portNumber
                }) {
                remotePeers.add(peer)
                scope.launch { connectToPeer(peer) }
            }
        }
    }

    fun removePeerFromList(peer: Host) {
        val key = peerKey(peer.hostName, peer.portNumber)

        peerSocketMap.remove(key)?.close()
        peerWriterMap.remove(key)
        peerUuidMap.remove(key)

        synchronized(remotePeers) {
            remotePeers.removeIf {
                it.hostName == peer.hostName &&
                        it.portNumber == peer.portNumber
            }
        }
    }

    fun updatePeer(peer: Host) {
        removePeerFromList(peer)
        addPeerToList(peer)
    }
}
