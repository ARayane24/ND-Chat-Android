package com.example.ndchat.model

import Host
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.LinkedList

class PearManager(
    private val myHost: Host,
    private val remotePeers: LinkedList<Host>,
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

    private fun startServer() {
        try {
            serverSocket = ServerSocket(myHost.portNumber, 10, InetAddress.getByName(myHost.hostName))
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

    fun sendMessage(socket: Socket, message: String) {
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
        synchronized(clientSockets) {
            clientSockets.forEach { socket -> sendMessage(socket, "[Broadcast] $message") }
        }
    }

    fun stop() {
        serverSocket?.close()
        clientSockets.forEach { it.close() }
    }

    fun sendToPeer(target: Host, text: String) {
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
    }

    fun disconnectFromPeer(target: Host) {
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
