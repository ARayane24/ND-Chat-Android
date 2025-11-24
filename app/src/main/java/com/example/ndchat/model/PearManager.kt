package com.example.ndchat.model

import Host
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket

class PearManager(
    private val myHost: Host,
    private val remotePeers: MutableList<Host>,
    private val onMessageReceived: (String, Host?) -> Unit
) {

    private val clientSockets = mutableListOf<Socket>()
    private var serverSocket: ServerSocket? = null

    fun start() {
        // Start server
        Thread { startServer() }.start()

        // Connect to peers
        remotePeers.forEach { h ->
            Thread { connectToPeer(h) }.start()
        }
    }

    fun addPeerToList(peer: Host){
        remotePeers.add(peer)
        Thread { connectToPeer(peer) }.start()
    }

    fun removePeerFromList (peer : Host){
        remotePeers.remove(peer)
        Thread { disconnectFromPeer(peer)}.start()
    }

    fun updatePeer(updated: Host) {
        synchronized(clientSockets) {
            // 1. Find the old peer in list
            val oldPeer = remotePeers.find { it.uuid == updated.uuid }

            if (oldPeer != null) {
                // 2. Replace peer in list
                val index = remotePeers.indexOf(oldPeer)
                remotePeers[index] = updated

                // 3. Disconnect old socket
                val oldSocket = clientSockets.find { s ->
                    s.inetAddress.hostAddress == oldPeer.hostName &&
                            s.port == oldPeer.portNumber
                }

                if (oldSocket != null) {
                    try {
                        oldSocket.close()
                    } catch (_: IOException) { }
                    clientSockets.remove(oldSocket)
                }

                // 4. Reconnect using updated info (new IP / port)
                Thread { connectToPeer(updated) }.start()

                // 5. Notify UI
                onMessageReceived("Peer updated: ${updated.pearName}", null)
            } else {
                println("Peer not found to update: ${updated.pearName}")
            }
        }
    }


    private fun startServer() {
        try {
            serverSocket = ServerSocket(myHost.portNumber, 10)
            while (true) {
                val client = serverSocket!!.accept()
                Thread { handleIncoming(client) }.start()
                addPeerToList(Host(client.inetAddress.hostName,client.inetAddress.hostName,client.port))
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun connectToPeer(peer: Host) {
        while (true) {
            try {
                val socket = Socket(peer.hostName, peer.portNumber)
                synchronized(clientSockets) { clientSockets.add(socket) }
                sendMessage(socket,"")
                break
            } catch (e: IOException) {
                Thread.sleep(5000)
            }
        }
    }

    private fun handleIncoming(socket: Socket) {
        try {
            onMessageReceived(Host(socket.inetAddress.hostName,socket.inetAddress.hostAddress!!,socket.port).toString() + " connected",null)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                onMessageReceived(line!!,Host(socket.inetAddress.hostName,socket.inetAddress.hostAddress!!,socket.port))
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun sendMessage(socket: Socket, message: String) {
        Thread {
            try {
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                writer.write(message)
                writer.newLine()
                writer.flush()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }.start()
    }

    fun broadcast(message: String) {
        Thread {
        synchronized(clientSockets) {
            clientSockets.forEach { socket -> sendMessage(socket, "[Broadcast] $message") }
        } }.start()
    }

    fun stop() {
        serverSocket?.close()
        clientSockets.forEach { it.close() }
    }

    fun sendToPeer(target: Host, text: String) {
        Thread {
            synchronized(clientSockets) {
                // Find the socket corresponding to the target peer
                val socket = clientSockets.find { s ->
                    s.inetAddress.hostAddress == target.hostName && s.port == target.portNumber
                }

                if (socket != null && socket.isConnected && !socket.isClosed) {
                    sendMessage(socket, "[${target.pearName}] $text")
                } else {
                    println("Cannot send to ${target.pearName}, socket not connected")
                }
            }
        }.start()
    }

    private fun disconnectFromPeer(target: Host) {
        synchronized(clientSockets) {
            // Find the socket corresponding to the target peer
            val socket = clientSockets.find { s ->
                s.inetAddress.hostAddress == target.hostName && s.port == target.portNumber
            }

            if (socket != null) {
                try {
                    socket.close() // close the socket
                    println("Disconnected from ${target.pearName}")
                } catch (e: IOException) {
                    e.printStackTrace()
                } finally {
                    clientSockets.remove(socket) // remove from the list
                }
            } else {
                println("No connection found for ${target.pearName}")
            }

            // Also remove from remotePeers if needed
            remotePeers.remove(target)

            onMessageReceived("$target disconnected",null)
        }
    }


}
