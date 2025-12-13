package com.example.ndchat.ui_elements
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ndchat.model.Voting
import com.example.ndchat.model.VotingOption

@Composable
fun VotingMessage(
    voting: Voting,
    onVote: (VotingOption) -> Unit,
    userHasVoted: Boolean = false // disable voting if the user already voted
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFEFEF))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(voting.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (voting.description.isNotBlank()) {
                Text(voting.description, fontSize = 14.sp, color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(12.dp))

            val totalVotes = voting.options.sumOf { it.hostsList.size }.coerceAtLeast(1)

            voting.options.forEach { option ->
                var animatedProgress by remember { mutableStateOf(0f) }

                // Animate progress
                LaunchedEffect(option.hostsList.size) {
                    animatedProgress = option.hostsList.size.toFloat() / totalVotes
                }

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(option.optionName, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("${option.hostsList.size}", fontSize = 12.sp, color = Color.Gray)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LinearProgressIndicator(
                        progress = animatedProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF4CAF50),
                        trackColor = Color(0xFFD6D6D6)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (!userHasVoted) {
                        Button(
                            onClick = { onVote(option) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                        ) {
                            Text("Vote", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}




@Composable
fun VotingView(
    voting: Voting,
    onVote: (VotingOption) -> Unit
) {
    var selectedOptionId by remember { mutableStateOf<String?>(null) }
    val totalVotes = voting.options.size.coerceAtLeast(1)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Text(
                text = voting.title,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = voting.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            voting.options.forEach { option ->
                VotingOptionRow(
                    option = option,
                    totalVotes = totalVotes,
                    selected = selectedOptionId == option.optionName,
                    onClick = {
                        if (selectedOptionId == null) {
                            selectedOptionId = option.optionName
                            onVote(option)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}


@Composable
fun VotingOptionRow(
    option: VotingOption,
    totalVotes: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val percentage = option.hostsList.size.toFloat() / totalVotes
    var animatedProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(option.hostsList.size) {
        animatedProgress = percentage
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !selected, onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = option.optionName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = "${(percentage * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LinearProgressIndicator(
            progress = animatedProgress,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        )
    }
}

