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
                // --------------------------
                // STATE MANAGEMENT
                // --------------------------

                var myHost by remember { mutableStateOf<Host?>(null) }

                var tempName by remember { mutableStateOf("") }
                var tempHost by remember { mutableStateOf("") }
                var tempPort by remember { mutableStateOf("55555") }

                val remotePeers = remember { mutableStateListOf<Host>() }
                var messages by remember { mutableStateOf(listOf<Message>()) }

                // --------------------------
                // SETUP SCREEN
                // --------------------------
                if (myHost == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Setup Your Identity",
                                style = MaterialTheme.typography.headlineMedium
                            )
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
                                        myHost = Host(tempName, tempHost, p)
                                    }
                                },
                                enabled = tempName.isNotBlank() && tempHost.isNotBlank() && tempPort.isNotBlank()
                            ) {
                                Text("Start Chatting")
                            }
                        }
                    }
                } else {
                    // --------------------------
                    // CHAT SCREEN
                    // --------------------------

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
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF0F0F0))
                    ) {
                        ChatScreen(
                            myHost = myHost!!,
                            initialMessages = messages,
                            pears = remotePeers,
                            onAddPeer = { peer -> pearManager.addPeerToList(peer) },
                            onSend = { text, isBroadcast, peer ->
                                if (text.isNotBlank()) {
                                    // Add the message locally
                                    messages = messages + Message(text, true, myHost!!)

                                    // Send message to peers
                                    if (isBroadcast) pearManager.broadcast(text)
                                    else peer?.let { pearManager.sendToPeer(it, text) }
                                }
                            },
                            onClearMessages = { messages = listOf() },
                            onEditMyHost = { newName, newHost, newPort ->
                                myHost?.apply {
                                    pearName = newName
                                    hostName = newHost
                                    portNumber = newPort
                                }
                                myHost = myHost // Force recomposition
                            },
                            onDelete = { peer -> pearManager.removePeerFromList(peer) },
                            onEdit = { updatedPeer -> pearManager.updatePeer(updatedPeer) },
                            // --------------------------
                            // VOTING HANDLER
                            // --------------------------
                            onPoolCreated = { isBroadcast, peer, voting ->
                                // Add voting message locally
                                messages = messages + Message(
                                    sender = myHost!!,
                                    message = "",
                                    voting = voting,
                                    isSentByMe = true
                                )

                                // Optionally broadcast the voting to peers
                                val votingText = "📊 Voting Created: ${voting.title}"
                                if (isBroadcast) pearManager.broadcast(votingText)
                                else peer?.let { pearManager.sendToPeer(it, votingText) }
                            },
                        )
                    }
                }
            }
        }
    }
}
