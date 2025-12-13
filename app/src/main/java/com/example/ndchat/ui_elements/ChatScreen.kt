package com.example.ndchat.ui_elements

import Host
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ndchat.R
import com.example.ndchat.model.Message
import com.example.ndchat.model.Voting

// -------------------------
// PEERS PANEL
// -------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeersContent(
    pears: MutableList<Host>,
    onAddPeer: (Host) -> Unit,
    onPeerSelected: (Host) -> Unit,
    onBroadcastSelected: () -> Unit,
    currentPeer: Host?,
    isBroadcast: Boolean,
    onEdit: (Host) -> Unit,
    onDelete: (Host) -> Unit
) {
    // Local states for adding/editing a peer
    var pearName by remember { mutableStateOf("") }
    var hostName by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("55555") }
    var editingPeer by remember { mutableStateOf<Host?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F3F5))
            .padding(16.dp)
    ) {
        Text("Peers", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // Add/Edit form title
        Text(
            if (editingPeer == null) "Add Connection" else "Edit Connection",
            fontWeight = FontWeight.Bold
        )

        // Name field
        OutlinedTextField(
            value = pearName,
            onValueChange = { pearName = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth()
        )

        // Host + Port fields
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = hostName,
                onValueChange = { hostName = it },
                label = { Text("IP") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("Port") },
                modifier = Modifier.width(80.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }

        // Add / Save Button
        Button(
            onClick = {
                val portInt = port.toIntOrNull() ?: return@Button
                if (pearName.isBlank()) return@Button

                if (editingPeer == null) {
                    onAddPeer(Host(pearName, hostName, portInt))
                } else {
                    val updatedPeer = editingPeer!!.copy(
                        pearName = pearName,
                        hostName = hostName,
                        portNumber = portInt,
                        uuid = editingPeer!!.uuid
                    )
                    onEdit(updatedPeer)
                    editingPeer = null
                }

                pearName = ""
                hostName = ""
                port = "55555"
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text(if (editingPeer == null) "Add" else "Save") }

        // Cancel button when editing
        if (editingPeer != null) {
            OutlinedButton(
                onClick = {
                    editingPeer = null
                    pearName = ""
                    hostName = ""
                    port = "55555"
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("Cancel") }
        }

        HorizontalDivider(
            Modifier.padding(vertical = 16.dp),
            DividerDefaults.Thickness,
            DividerDefaults.color
        )

        // Broadcast item
        Card(
            onClick = onBroadcastSelected,
            colors = CardDefaults.cardColors(
                containerColor = if (isBroadcast) Color(0xFFFFD180) else Color(0xFFFFF3E0)
            ),
            border = if (isBroadcast) BorderStroke(2.dp, Color(0xFFE65100)) else null
        ) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = isBroadcast, onClick = null)
                Text("📢 Broadcast (All)", modifier = Modifier.padding(start = 8.dp).weight(1f), fontWeight = FontWeight.Bold)
            }
        }

        // Peers list
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(pears) { peer ->
                val isSelected = (peer == currentPeer) && !isBroadcast
                PeerListItem(
                    peer = peer,
                    isSelected = isSelected,
                    onSelect = onPeerSelected,
                    onEdit = {
                        editingPeer = it
                        pearName = it.pearName
                        hostName = it.hostName
                        port = it.portNumber.toString()
                    },
                    onDelete = onDelete
                )
            }
        }
    }
}

// -------------------------
// CHAT SCREEN
// -------------------------
@Composable
fun ChatScreen(
    myHost: Host,
    initialMessages: List<Message>,
    pears: MutableList<Host>,
    onSend: (String, Boolean, Host?) -> Unit,
    onClearMessages: () -> Unit,
    onAddPeer: (Host) -> Unit,
    onEdit: (Host) -> Unit,
    onDelete: (Host) -> Unit,
    onEditMyHost: (String, String, Int) -> Unit,
    onPoolCreated: (Boolean, Host?, Voting) -> Unit
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var isBroadcast by remember { mutableStateOf(true) }
    var selectedPeer by remember { mutableStateOf<Host?>(null) }
    var showEditMyHostDialog by remember { mutableStateOf(false) }
    var messages by remember { mutableStateOf(initialMessages) }
    var showAddPoolDialog by remember { mutableStateOf(false) }


    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val menuWidth = screenWidth * 0.85f
    val chatScreenOffset by animateDpAsState(
        targetValue = if (isMenuOpen) -menuWidth else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "SlideAnimation"
    )

    BackHandler(enabled = isMenuOpen) { isMenuOpen = false }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF202225))) {
        // Peers menu
        Box(modifier = Modifier.width(menuWidth).fillMaxHeight().align(Alignment.CenterEnd)) {
            PeersContent(
                pears = pears,
                onAddPeer = onAddPeer,
                onPeerSelected = {
                    selectedPeer = it
                    isBroadcast = false
                    isMenuOpen = false
                },
                onBroadcastSelected = {
                    selectedPeer = null
                    isBroadcast = true
                    isMenuOpen = false
                },
                onEdit = onEdit,
                onDelete = onDelete,
                currentPeer = selectedPeer,
                isBroadcast = isBroadcast
            )
        }

        // Chat screen content
        Box(
            modifier = Modifier
                .offset(x = chatScreenOffset)
                .fillMaxSize()
                .background(Color.White)
        ) {
            ChatContent(
                myHost = myHost,
                messages = messages,
                inputText = inputText,
                isBroadcast = isBroadcast,
                selectedPeer = selectedPeer,
                onInputChange = { inputText = it },
                onSend = { txt, b, p ->
                    if (txt.isNotBlank()) {
                        val newMsg = Message(sender = myHost, message = txt, isSentByMe = b)
                        messages = messages + newMsg
                        inputText = TextFieldValue("")
                        onSend(txt, b, p)
                    }
                },
                onPeersClick = { isMenuOpen = !isMenuOpen },
                onEditHostClick = { showEditMyHostDialog = true },
                onAddVotingClick = { showAddPoolDialog = true }
            )

            if (isMenuOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { isMenuOpen = false }
                        .background(Color.Black.copy(alpha = 0.1f))
                )
            }
        }
    }

    // Edit My Host dialog
    if (showEditMyHostDialog) {
        EditHostDialog(myHost, onDismiss = { showEditMyHostDialog = false }) { name, host, port ->
            onEditMyHost(name, host, port)
        }
    }

    if (showAddPoolDialog) {
        CreateVotingDialog(
            onDismiss = { showAddPoolDialog = false },
            onCreate = { voting ->
                messages = messages + Message(sender = myHost, message = "", voting = voting, isSentByMe = isBroadcast)
                onPoolCreated(isBroadcast, selectedPeer, voting)
                showAddPoolDialog = false

            }
        )
    }
}

// -------------------------
// CHAT CONTENT
// -------------------------
@Composable
fun ChatContent(
    myHost: Host,
    messages: List<Message>,
    inputText: TextFieldValue,
    isBroadcast: Boolean,
    selectedPeer: Host?,
    onInputChange: (TextFieldValue) -> Unit,
    onSend: (String, Boolean, Host?) -> Unit,
    onPeersClick: () -> Unit,
    onEditHostClick: () -> Unit,
    onAddVotingClick: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Color(0xFFFAFAFA))) {

        // ---------- TOP BAR ----------
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(Color.White)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier.weight(1f).clickable(onClick = onEditHostClick)
            ) {
                Text(
                    if (isBroadcast) "Global Chat" else selectedPeer?.pearName ?: "Chat",
                    fontWeight = FontWeight.Bold
                )
                Text("Me: ${myHost.pearName}", color = Color.Gray, fontSize = 12.sp)
            }

            IconButton(onClick = onPeersClick) {
                Text("👥")
            }
        }

        HorizontalDivider()

        // ---------- MESSAGES ----------
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            reverseLayout = true
        ) {
            items(messages.reversed()) { msg ->
                when {
                    msg.voting != null ->
                        VotingMessage(
                            voting = msg.voting!!,
                            userHasVoted = msg.voting!!.hasHostVoted(myHost),
                            onVote = { option ->
                                msg.voting!!.addVote(option, myHost)
                            }
                        )

                    msg.sender != null -> MessageBubble(msg)
                    else -> ConnectionStateMessage(msg)
                }
            }
        }

        // ---------- INPUT ----------
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {

            if (inputText.text.isBlank()) {
                IconButton(onClick = onAddVotingClick) {
                    Icon(
                        painterResource(R.drawable.add_button),
                        contentDescription = "Add voting",
                        tint = Color.Unspecified
                    )
                }
            }

            BasicTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .heightIn(min = 48.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF0F0F0), RoundedCornerShape(24.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        if (inputText.text.isEmpty()) {
                            Text("Message...", color = Color.Gray)
                        }
                        inner()
                    }
                }
            )

            if (inputText.text.isNotBlank()) {
                IconButton(
                    onClick = { onSend(inputText.text.trim(), isBroadcast, selectedPeer) }
                ) {
                    Icon(
                        painterResource(R.drawable.send_button),
                        contentDescription = "Send",
                        tint = Color.Unspecified
                    )
                }
            }
        }
    }
}


// -------------------------
// EDIT HOST DIALOG
// -------------------------
@Composable
fun EditHostDialog(myHost: Host, onDismiss: () -> Unit, onSave: (String, String, Int) -> Unit) {
    var editName by remember { mutableStateOf(myHost.pearName) }
    var editHost by remember { mutableStateOf(myHost.hostName) }
    var editPort by remember { mutableStateOf(myHost.portNumber.toString()) }

    HostDialog(
        title = "Edit my host",
        onSave = {
            val portInt = editPort.toIntOrNull() ?: myHost.portNumber
            onSave(editName, editHost, portInt)
        },
        pearName = editName,
        hostName = editHost,
        port = editPort.toIntOrNull() ?: 0,
        onPearNameChange = { editName = it },
        onHostNameChange = { editHost = it },
        onPortChange = { editPort = it },
        onDismiss = onDismiss
    )
}
