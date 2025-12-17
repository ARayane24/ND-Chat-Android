package com.example.ndchat.ui_elements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults.textFieldColors
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ndchat.utils.isPortAvailable
import com.example.ndchat.utils.isValidAndFreePort
import com.example.ndchat.utils.isValidIPv4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostInputForm(
    pearName: String,
    onPearNameChange: (String) -> Unit,

    hostName: String,
    onHostNameChange: (String) -> Unit,

    port: String,
    onPortChange: (String) -> Unit,

    modifier: Modifier = Modifier
) {
    val isNameError = pearName.isBlank()
    val isIpError = hostName.isNotBlank() && !isValidIPv4(hostName)
    val isPortError = port.isNotBlank() && !isValidAndFreePort(port)
    var port by remember { mutableStateOf("55555") }
    var portError by remember { mutableStateOf<String?>(null) }
    var ipError by remember { mutableStateOf<String?>(null) }


    Column(modifier = modifier.fillMaxWidth()) {

        // 🔹 NAME
        OutlinedTextField(
            value = pearName,
            onValueChange = onPearNameChange,
            label = { Text("Name") },
            isError = isNameError,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors( focusedContainerColor = Color.White, unfocusedContainerColor = Color.White ,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black),
            maxLines = 1
        )
        if (isNameError) {
            Text("Name is required", color = Color.Red)
        }

        Spacer(Modifier.height(8.dp))

        // 🔹 IP ADDRESS
        OutlinedTextField(
            value = hostName,
            onValueChange = onHostNameChange,
            label = { Text("IP Address") },
            isError = isIpError,
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors(focusedTextColor = Color(red = 0, green = 200, blue = 20),focusedContainerColor = Color.White, unfocusedContainerColor = Color.White , unfocusedTextColor = Color(red = 0, green = 200, blue = 20)),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            maxLines = 1
        )
        if (isIpError) {
            Text("Invalid IPv4 address", color = Color.Red)
        }

        Spacer(Modifier.height(8.dp))

        // 🔹 PORT
        OutlinedTextField(
            value = port,
            onValueChange = {
                port = it

                val p = port.toIntOrNull()

                portError = when {
                    port.isEmpty() -> null
                    p == null || p !in 1..65535 ->
                        "Port must be between 1 and 65535"
                    !isPortAvailable(p) ->
                        "Port $p is already in use"
                    else -> null
                }
            },
            label = { Text("Port") },
            isError = isPortError,
            colors = textFieldColors( focusedTextColor = Color.Blue,
                unfocusedTextColor = Color.Blue,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White     ),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            maxLines = 1,
            supportingText = {
                portError?.let {
                    Text(it, color = Color.Red)
                }
            }
        )
    }
}


