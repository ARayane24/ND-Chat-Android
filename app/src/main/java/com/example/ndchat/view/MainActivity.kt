package com.example.ndchat.view

import Host
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ndchat.model.Message
import com.example.ndchat.model.PearManager
import com.example.ndchat.ui.theme.NDChatTheme
import com.example.ndchat.ui_elements.ChatScreen
import com.example.ndchat.ui_elements.HostInputForm

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NDChatTheme {
                // State to hold the final MyHost object
                var myHost by remember { mutableStateOf<Host?>(null) }

                // State for the setup screen inputs
                var tempName by remember { mutableStateOf("") }
                var tempHost by remember { mutableStateOf("") }
                var tempPort by remember { mutableStateOf("") }

                val remotePeers = remember { mutableStateListOf<Host>() }
                var messages by remember { mutableStateOf(listOf<Message>()) }

                if (myHost == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Setup Your Identity", style = MaterialTheme.typography.headlineMedium)
                            Spacer(modifier = Modifier.height(16.dp))

                            HostInputForm(
                                pearName = tempName,
                                onPearNameChange = { tempName = it },
                                hostName = tempHost,
                                onHostNameChange = { tempHost = it },
                                port = tempPort.toIntOrNull(),
                                onPortChange = { tempPort = it }
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    val p = tempPort.toIntOrNull()
                                    if (tempName.isNotBlank() && tempHost.isNotBlank() && p != null) {
                                        // Create the Host object and switch screens
                                        myHost = Host(tempName, tempHost, p)
                                    }
                                },
                                enabled = tempName.isNotBlank() && tempHost.isNotBlank() && tempPort.isNotBlank()
                            ) {
                                Text("Start Chatting")
                            }
                        }
                    }
                }
                else {
                    // === CHAT SCREEN ===
                    // Initialize PearManager only when myHost is ready
                    val pearManager = remember(myHost) {
                        PearManager(
                            myHost = myHost!!,
                            remotePeers = remotePeers,
                            onMessageReceived = { msg, sender ->
                                messages = messages + Message(msg, false, sender)
                            }
                        ).apply { start() }
                    }

                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF0F0F0))
                    ) {
                        ChatScreen(
                            myHost = myHost!!,
                            messages = messages,
                            pears = remotePeers,
                            onAddPeer = { peer -> pearManager.apply {   pearManager.addPeerToList(peer) }},
                            onSend = { text, isBroadcast, peer ->
                                if (text.isNotBlank()) {
                                    messages = messages + Message(text, true, myHost!!)
                                    if (isBroadcast) pearManager.broadcast(text)
                                    else peer?.let { pearManager.sendToPeer(it, text) }
                                }
                            },
                            onClearMessages = { messages = listOf() },
                            // Logic to update local host
                            onEditMyHost = { newName, newHost, newPort ->
                                myHost?.apply {
                                    pearName = newName
                                    hostName = newHost
                                    portNumber = newPort
                                }
                                // Force recomposition by toggling a dummy state or relying on MutableState if Host properties are observable
                            },
                            onDelete = {
                                p -> remotePeers.remove(p)
                            },
                            onEdit = {
                                p ->
                                {
                                    remotePeers.remove(p)
                                    remotePeers.add(p)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}