package com.example.ndchat.view

import Host
import android.gesture.Gesture
import android.os.Bundle
import android.view.GestureDetector
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.ndchat.model.Message
import com.example.ndchat.model.PearManager
import com.example.ndchat.ui.theme.NDChatTheme
import com.example.ndchat.ui_elements.ChatInputField
import com.example.ndchat.ui_elements.ConnectionStateMessage
import com.example.ndchat.ui_elements.MessageBubble
import java.util.LinkedList

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NDChatTheme {
                var myHost by remember { mutableStateOf<Host?>(null) }
                val remotePeers = remember { LinkedList<Host>() }
                var messages by remember { mutableStateOf(listOf<Message>()) }

                if (myHost == null) {
                    HostSetupScreen { host ->
                        myHost = host
                    }
                } else {

                    val pearManager = remember {
                        PearManager(
                            myHost = myHost!!,
                            remotePeers = remotePeers,
                            onMessageReceived = { msg , sender ->
                                messages = messages + Message(msg,false,sender)
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
                            onAddPeer = { peer -> pearManager.addPeerToList(peer) },
                            onSend = { text, isBroadcast, peer ->
                                if (text.isNotBlank()) {
                                    messages = messages + Message(
                                        message = text,
                                        true,
                                        myHost!!
                                    )
                                    if (isBroadcast) pearManager.broadcast(text)
                                    else peer?.let { pearManager.sendToPeer(it, text) }
                                }
                            },
                            onClearMessages = {
                                messages = listOf()
                            },
                            onEditMyHost = { myNewHost ->
                                myHost = myNewHost
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HostSetupScreen(onSubmit: (Host) -> Unit) {
    var pearName by remember { mutableStateOf("") }
    var hostName by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFEFEFEF)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            Modifier.padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Setup Your Host", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                TextField(
                    value = pearName,
                    onValueChange = { pearName = it },
                    label = { Text("Your Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                TextField(
                    value = hostName,
                    onValueChange = { hostName = it },
                    label = { Text("IP Address") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                TextField(
                    value = port,
                    onValueChange = { port = it.filter { it.isDigit() } },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (pearName.isNotBlank() && hostName.isNotBlank() && port.isNotBlank()) {
                            onSubmit(Host(pearName, hostName, port.toInt()))
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Start Chat")
                }
            }
        }
    }
}



@Composable
fun ChatScreen(
    myHost: Host,
    messages: List<Message>,
    pears: LinkedList<Host>,
    onSend: (String, Boolean, Host?) -> Unit,
    onClearMessages: () -> Unit,
    onAddPeer: (Host) -> Unit,
    onEditMyHost: (Host) -> Unit // ✅ new callback for editing self info
) {
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var isBroadcast by remember { mutableStateOf(true) }
    var showPeersDialog by remember { mutableStateOf(false) }
    var showEditMyHostDialog by remember { mutableStateOf(false) } // ✅ new state
    var selectedPeer by remember { mutableStateOf<Host?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F0F0))
            .padding(8.dp)
    ) {

        // Broadcast toggle and Manage Peers
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current
                ) {
                    showEditMyHostDialog = true // ✅ open popup
                }
                .background(Color.White, shape = MaterialTheme.shapes.medium)
                .padding(12.dp)
        ) {


            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Broadcast", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = isBroadcast,
                    onCheckedChange = {
                        isBroadcast = it
                        if (!it) {
                            onClearMessages()
                            selectedPeer = null
                            showPeersDialog = true
                        } else {
                            selectedPeer = null
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4CAF50),
                        uncheckedThumbColor = Color.Gray
                    )
                )

                Spacer(Modifier.weight(1f))


                Button(onClick = { showPeersDialog = true }) {
                    Text("Manage Peers / Select")
                }
            }
        }

        // Messages list
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            reverseLayout = true
        ) {
            items(messages.reversed()) { msg ->
                if (msg.sender != null)
                    MessageBubble(message = msg)
                else
                    ConnectionStateMessage(message = msg)
            }
        }

        // ✅ Edit My Host popup
        if (showEditMyHostDialog) {
            Dialog(onDismissRequest = { showEditMyHostDialog = false }) {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    elevation = CardDefaults.cardElevation(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    var pearName by remember { mutableStateOf(myHost.pearName) }
                    var hostName by remember { mutableStateOf(myHost.hostName) }
                    var port by remember { mutableStateOf(myHost.portNumber.toString()) }

                    Column(Modifier.padding(16.dp)) {
                        Text("Edit My Host Information", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))

                        TextField(
                            value = pearName,
                            onValueChange = { pearName = it },
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))

                        TextField(
                            value = hostName,
                            onValueChange = { hostName = it },
                            label = { Text("Host (IP)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))

                        TextField(
                            value = port,
                            onValueChange = { port = it.filter { it.isDigit() } },
                            label = { Text("Port") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showEditMyHostDialog = false }) {
                                Text("Cancel")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                if (pearName.isNotBlank() && hostName.isNotBlank() && port.isNotBlank()) {
                                    val updated = Host(pearName, hostName, port.toInt())
                                    onEditMyHost(updated)
                                    showEditMyHostDialog = false
                                }
                            }) {
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }

        // Peer management / selection dialog
        if (showPeersDialog) {
            Dialog(onDismissRequest = { showPeersDialog = false }) {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    elevation = CardDefaults.cardElevation(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    var pearName by remember { mutableStateOf("") }
                    var hostName by remember { mutableStateOf("") }
                    var port by remember { mutableStateOf("") }
                    var editingIndex by remember { mutableStateOf<Int?>(null) }

                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (isBroadcast) "Manage Remote Peers"
                            else "Select Peer to Chat / Manage Peers",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(12.dp))

                        // Input fields for Add/Edit
                        TextField(
                            value = pearName,
                            onValueChange = { pearName = it },
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))

                        TextField(
                            value = hostName,
                            onValueChange = { hostName = it },
                            label = { Text("IP Address") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))

                        TextField(
                            value = port,
                            onValueChange = { port = it.filter { it.isDigit() } },
                            label = { Text("Port") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (pearName.isNotBlank() && hostName.isNotBlank() && port.isNotBlank()) {
                                    if (editingIndex != null) {
                                        val index = editingIndex!!
                                        pears[index] = Host(pearName, hostName, port.toInt())
                                        editingIndex = null
                                    } else {
                                        onAddPeer(Host(pearName, hostName, port.toInt()))
                                    }
                                    pearName = ""
                                    hostName = ""
                                    port = ""
                                }
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(if (editingIndex != null) "Save" else "Add Peer")
                        }

                        Spacer(Modifier.height(16.dp))
                        Text("Connected Peers:", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))

                        pears.forEachIndexed { index, peer ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (!isBroadcast) {
                                            RadioButton(
                                                selected = selectedPeer == peer,
                                                onClick = { selectedPeer = peer }
                                            )
                                        }
                                        Text("${peer.pearName} - ${peer.hostName}:${peer.portNumber}")
                                    }
                                }
                                Row {
                                    IconButton(onClick = {
                                        pearName = peer.pearName
                                        hostName = peer.hostName
                                        port = peer.portNumber.toString()
                                        editingIndex = index
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                                    }
                                    IconButton(onClick = {
                                        if (selectedPeer == peer) selectedPeer = null
                                        pears.removeAt(index)
                                        if (editingIndex == index) {
                                            pearName = ""
                                            hostName = ""
                                            port = ""
                                            editingIndex = null
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        if (!isBroadcast) {
                            Button(
                                onClick = {
                                    if (selectedPeer != null) showPeersDialog = false
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Select Peer")
                            }
                        }
                    }
                }
            }
        }

        // Input + Send
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChatInputField(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFFF0F0F0), shape = MaterialTheme.shapes.small),
                inputText = inputText,
                onValueChange = { inputText = it }
            )

            Button(
                onClick = {
                    if (inputText.text.isNotBlank()) {
                        onSend(inputText.text, isBroadcast, selectedPeer)
                        inputText = TextFieldValue("")
                    }
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Send")
            }
        }
    }
}


