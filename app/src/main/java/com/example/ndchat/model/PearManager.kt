package com.example.ndchat.model

import Host
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

class PearManager(
    private val myHost: Host,
    private val remotePeers: MutableList<Host>,
    private val onMessageReceived: (String, Host?) -> Unit
) {

    private val TAG = "PearManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val peerSocketMap = ConcurrentHashMap<String, Socket>()
    private val peerWriterMap = ConcurrentHashMap<String, BufferedWriter>()
    private val peerUuidMap   = ConcurrentHashMap<String, UUID>()

    private var serverSocket: ServerSocket? = null

    private fun peerKey(ip: String, port: Int) = "$ip:$port"

    /* ---------------- START / STOP ---------------- */

    fun start() {
        scope.launch { startServer() }
        remotePeers.forEach { scope.launch { connectToPeer(it) } }
    }

    fun stop() {
        scope.cancel()
        peerSocketMap.values.forEach { try { it.close() } catch (_: Exception) {} }
        peerSocketMap.clear()
        peerWriterMap.clear()
        peerUuidMap.clear()
        try { serverSocket?.close() } catch (_: Exception) {}
    }

    /* ---------------- SERVER ---------------- */

    private suspend fun startServer() {
        serverSocket = ServerSocket(myHost.portNumber)
        Log.i(TAG, "SERVER: Listening on ${myHost.portNumber}")

        while (coroutineContext.isActive) {
            val client = serverSocket!!.accept()
            scope.launch { handleConnection(client, false) }
        }
    }

    /* ---------------- CLIENT ---------------- */

    private suspend fun connectToPeer(peer: Host) {
        val key = peerKey(peer.hostName, peer.portNumber)
        if (peerSocketMap.containsKey(key)) return // FAST FIX

        while (coroutineContext.isActive) {
            try {
                val socket = Socket(peer.hostName, peer.portNumber)
                sendHandshake(socket)
                handleConnection(socket, true)
                break
            } catch (_: Exception) {
                delay(3000) // FAST FIX: shorter retry
            }
        }
    }

    /* ---------------- CONNECTION ---------------- */

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
                val parts = line.split("|")

                when (parts[0]) {

                    "HANDSHAKE" -> {
                        val uuid = UUID.fromString(parts[1])
                        val name = parts[2]
                        val ip = parts[3]
                        val port = parts[4].toInt()
                        key = peerKey(ip, port)

                        val existing = peerSocketMap[key]
                        if (existing != null && existing != socket) {
                            if (!isInitiator && myHost.uuid > uuid) {
                                socket.close(); return
                            }
                            existing.close()
                        }

                        peerSocketMap[key!!] = socket
                        peerWriterMap[key!!] = writer
                        peerUuidMap[key!!] = uuid

                        synchronized(remotePeers) {
                            val i = remotePeers.indexOfFirst {
                                it.hostName == ip && it.portNumber == port
                            }
                            val host = Host(name, ip, port, uuid)
                            if (i >= 0) remotePeers[i] = host else remotePeers.add(host)
                        }

                        if (!isInitiator) sendHandshake(socket)
                        onMessageReceived("$name joined", null)
                    }

                    "DISCONNECT" -> break

                    else -> {
                        val sender = key?.let { k ->
                            synchronized(remotePeers) {
                                remotePeers.find {
                                    peerKey(it.hostName, it.portNumber) == k
                                }
                            }
                        }
                        onMessageReceived(line, sender)
                    }
                }
            }
        } finally {
            key?.let {
                peerSocketMap.remove(it)
                peerWriterMap.remove(it)
                peerUuidMap.remove(it)
            }
            try { socket.close() } catch (_: Exception) {}
        }
    }

    /* ---------------- MESSAGING ---------------- */

    private fun sendHandshake(socket: Socket) {
        val msg = "HANDSHAKE|${myHost.uuid}|${myHost.pearName}|${myHost.hostName}|${myHost.portNumber}"
        socket.getOutputStream().write((msg + "\n").toByteArray()) // FAST FIX
    }

    fun sendToPeer(peer: Host, msg: String) {
        val writer = peerWriterMap[peerKey(peer.hostName, peer.portNumber)] ?: return
        synchronized(writer) {
            writer.write("[${myHost.pearName}] $msg\n")
            writer.flush()
        }
    }

    fun broadcast(msg: String) {
        val text = "[${myHost.pearName}] $msg\n"
        peerWriterMap.values.forEach { writer ->
            synchronized(writer) {
                writer.write(text)
                writer.flush()
            }
        }
    }

    /* ---------------- PEER MANAGEMENT ---------------- */

    fun addPeerToList(peer: Host) {
        synchronized(remotePeers) {
            if (remotePeers.none {
                    it.hostName == peer.hostName && it.portNumber == peer.portNumber
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
                it.hostName == peer.hostName && it.portNumber == peer.portNumber
            }
        }
    }

    fun updatePeer(peer: Host) {
        removePeerFromList(peer)
        addPeerToList(peer)
    }
}
