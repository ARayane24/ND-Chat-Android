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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.ndchat.model.Message

@Composable
fun ChatScreen(
    myHost: Host,
    messages: List<Message>,
    pears: MutableList<Host>, // ✅ FIXED: Changed to MutableList
    onSend: (String, Boolean, Host?) -> Unit,
    onClearMessages: () -> Unit,
    onAddPeer: (Host) -> Unit,
    onEdit: (Host) -> Unit,
    onDelete: (Host) -> Unit,
    onEditMyHost: (String, String, Int) -> Unit
) {
    // 1. State
    var isMenuOpen by remember { mutableStateOf(false) }

    // Chat Logic State
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var isBroadcast by remember { mutableStateOf(true) }
    var selectedPeer by remember { mutableStateOf<Host?>(null) }
    var showEditMyHostDialog by remember { mutableStateOf(false) }

    // 2. Animation Logic
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val menuWidth = screenWidth * 0.85f

    val chatScreenOffset by animateDpAsState(
        targetValue = if (isMenuOpen) -menuWidth else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "SlideAnimation"
    )

    BackHandler(enabled = isMenuOpen) {
        isMenuOpen = false
    }

    // --- ROOT CONTAINER ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF202225))
    ) {

        // LAYER A: Peers Menu (Underneath - Right Side)
        Box(
            modifier = Modifier
                .width(menuWidth)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
        ) {
            PeersContent(
                pears = pears,
                onAddPeer = onAddPeer,
                onPeerSelected = { peer ->
                    selectedPeer = peer
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
                currentPeer = selectedPeer, // ✅ FIXED: Removed TODO
                isBroadcast = isBroadcast   // ✅ FIXED: Removed TODO
            )
        }

        // LAYER B: Chat Screen (Top Layer)
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
                onSend = { txt, b, p -> onSend(txt, b, p); inputText = TextFieldValue("") },
                onPeersClick = { isMenuOpen = !isMenuOpen },
                onEditHostClick = { showEditMyHostDialog = true }
            )

            if (isMenuOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = { isMenuOpen = false })
                        .background(Color.Black.copy(alpha = 0.1f))
                )
            }
        }
    }

    if (showEditMyHostDialog) {
        EditHostDialog(myHost, { showEditMyHostDialog = false }, onEditMyHost)
    }
}

// --- SUB COMPONENT: Peers List ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeersContent(
    pears: MutableList<Host>, // ✅ FIXED: MutableList
    onAddPeer: (Host) -> Unit,
    onPeerSelected: (Host) -> Unit,
    onBroadcastSelected: () -> Unit,
    currentPeer: Host?,
    isBroadcast: Boolean,
    onEdit: (Host) -> Unit,
    onDelete: (Host) -> Unit
) {
    var pearName by remember { mutableStateOf("") }
    var hostName by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F3F5))
            .padding(16.dp)
    ) {
        Text("Peers", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // Add Form
        Text("Add Connection", fontSize = TextUnit.Unspecified, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = pearName, onValueChange = { pearName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color(red = 0, green = 200, blue = 20), unfocusedTextColor = Color(red = 0, green = 200, blue = 20)),
            )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = hostName, onValueChange = { hostName = it }, label = { Text("IP") }, modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.Blue, unfocusedTextColor = Color.Blue),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") }, modifier = Modifier.width(80.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.Blue, unfocusedTextColor = Color.Blue),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))

        }
        Button(
            onClick = {
                if (pearName.isNotBlank() && port.toIntOrNull() != null) {
                    onAddPeer(Host(pearName, hostName, port.toInt()))
                    pearName = ""; hostName = ""; port = ""
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text("Add") }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        // List
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 1. Broadcast Option
            item {
                Card(
                    onClick = onBroadcastSelected,
                    // ✅ FIXED: Highlight color if selected
                    colors = CardDefaults.cardColors(
                        containerColor = if (isBroadcast) Color(0xFFFFD180) else Color(0xFFFFF3E0)
                    ),
                    border = if (isBroadcast) BorderStroke(2.dp, Color(0xFFE65100)) else null
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📢 Broadcast (All)", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        RadioButton(selected = isBroadcast, onClick = null)
                    }
                }
            }

            // 2. Peer List Options
            items(pears) { peer ->
                // ✅ FIXED: Correct selection logic
                val isSelected = (peer == currentPeer) && !isBroadcast

                PeerListItem(
                    peer = peer,
                    isSelected = isSelected, // Pass the boolean
                    onSelect = onPeerSelected,
                    onEdit = onEdit,
                    onDelete = { peerToDelete ->
                        pears.remove(peerToDelete) // ✅ FIXED: Actually removes item
                    }
                )
            }
        }
    }
}


// --- SUB COMPONENT: Chat UI ---
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
    onEditHostClick: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Color(0xFFFAFAFA))) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(Color.White)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).clickable { onEditHostClick() }) {
                Text(
                    if (isBroadcast) "Global Chat" else selectedPeer?.pearName ?: "Chat",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text("Me: ${myHost.pearName}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onPeersClick) {
                Text("👥", fontSize = TextUnit.Unspecified)
            }
        }
        Divider()

        // Messages Area
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            reverseLayout = true
        ) {
            items(messages.reversed()) { msg ->
                // Assuming MessageBubble is defined elsewhere or uses basic Text
                MessageBubble(msg)

            }
        }

        // Input Area
        Row(
            Modifier.background(Color.White).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                placeholder = { Text("Message...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF0F0F0),
                    unfocusedContainerColor = Color(0xFFF0F0F0),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                ),
                shape = MaterialTheme.shapes.extraLarge,
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onSend(inputText.text, isBroadcast, selectedPeer) }) {
                Text("Send")
            }
        }
    }
}

@Composable
fun EditHostDialog(myHost: Host, onDismiss: () -> Unit, onSave: (String, String, Int) -> Unit) {
    var editName by remember { mutableStateOf(myHost.pearName) }
    var editHost by remember { mutableStateOf(myHost.hostName) }
    // Convert Int to String for the TextField, handle default if needed
    var editPort by remember { mutableStateOf(myHost.portNumber.toString()) }

    HostDialog(
        title = "Edit my host",
        onSave = {
            // Safely convert port back to Int
            val portInt = editPort.toIntOrNull() ?: myHost.portNumber
            onSave(editName, editHost, portInt)
        },
        pearName = editName,
        hostName = editHost,
        port = editPort.toIntOrNull() ?: 0, // Pass int to the UI
        onPearNameChange = { v -> editName = v },
        onHostNameChange = { v -> editHost = v }, // ✅ FIXED: updates editHost
        onPortChange = { v -> editPort = v },     // ✅ FIXED: updates editPort
        onDismiss = onDismiss
    )
}