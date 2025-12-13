package com.example.ndchat.ui_elements

import Host
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ndchat.model.Voting
import com.example.ndchat.model.VotingOption
import java.util.LinkedList

@Composable
fun CreateVotingDialog(
    onDismiss: () -> Unit,
    onCreate: (Voting) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {

            Text(
                "Create Voting",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Text("Options", fontWeight = FontWeight.Medium)

            options.forEachIndexed { index, optionText ->
                OutlinedTextField(
                    value = optionText,
                    onValueChange = { newText ->
                        options = options.toMutableList().apply {
                            this[index] = newText
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    label = { Text("Option ${index + 1}") }
                )
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = { options = options + "" }
            ) {
                Text("+ Add option")
            }

            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }

                Spacer(Modifier.width(8.dp))

                Button(
                    enabled = title.isNotBlank() && options.all { it.isNotBlank() },
                    onClick = {
                        val voting = Voting(
                            title = title,
                            description = description,
                            options = options.map { option ->
                                VotingOption(
                                    optionName = option,
                                    hostsList = LinkedList<Host>()
                                )
                            }
                        )
                        onCreate(voting)
                    }
                ) {
                    Text("Create")
                }
            }
        }
    }
}

