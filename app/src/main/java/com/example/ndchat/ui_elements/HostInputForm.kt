package com.example.ndchat.ui_elements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun HostInputForm(
    pearName: String,
    onPearNameChange: (String) -> Unit,
    hostName: String?,
    onHostNameChange: (String) -> Unit,
    port: Int?, // Changed to String for easier typing handling
    onPortChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = pearName,
            onValueChange = onPearNameChange,
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color(red = 0, green = 200, blue = 20), unfocusedTextColor = Color(red = 0, green = 200, blue = 20)),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = hostName ?: "",
            onValueChange = onHostNameChange,
            label = { Text("IP Address") },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.Blue, unfocusedTextColor = Color.Blue),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = port?.toString() ?: "",
            onValueChange = onPortChange,
            label = { Text("Port") },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.Blue, unfocusedTextColor = Color.Blue),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

