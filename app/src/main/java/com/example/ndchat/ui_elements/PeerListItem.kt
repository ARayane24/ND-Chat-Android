package com.example.ndchat.ui_elements


import Host
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PeerListItem(
    peer: Host,
    isSelected: Boolean, // Receive the true/false
    onSelect: (Host) -> Unit,
    onEdit: (Host) -> Unit,
    onDelete: (Host) -> Unit
) {
    Card(
        onClick = { onSelect(peer) },
        colors = CardDefaults.cardColors(
            // Greenish if selected, White if not
            containerColor = if (isSelected) Color(0xFFDCF8C6) else Color.White
        ),
        border = if (isSelected) BorderStroke(2.dp, Color(0xFF075E54)) else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Show Radio Button to make it obvious
            RadioButton(
                selected = isSelected,
                onClick = null // null because Card click handles it
            )

            Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                Text(peer.pearName, fontWeight = FontWeight.Bold)
                Text("${peer.hostName}:${peer.portNumber}", style = MaterialTheme.typography.bodySmall)
            }


            // Only show Edit/Delete if NOT selected (optional UX choice)
            if (!isSelected) {
                IconButton(onClick = { onEdit(peer) }) { Icon(Icons.Default.Edit, "Edit") }
                IconButton(onClick = { onDelete(peer) }) { Icon(Icons.Default.Delete, "Delete") }
            }
        }
    }
}