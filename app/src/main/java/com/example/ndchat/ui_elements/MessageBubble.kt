package com.example.ndchat.ui_elements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ndchat.model.Message
import java.time.format.DateTimeFormatter


@Composable
fun MessageBubble(message: Message) {
    val isBroadcast = message.message.contains("Broadcast")
    val backgroundColor = if (message.isSentByMe) Color(0xFFC6E4F8) else if(isBroadcast) Color(0xFFDCF8C6) else Color.White
    val alignment = if (message.isSentByMe) Alignment.TopStart else Alignment.TopEnd
    val formatter = DateTimeFormatter.ofPattern("dd MMM, HH:mm")


    Box(
        contentAlignment = alignment,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .background(backgroundColor, shape = RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .widthIn(max = 280.dp)
            ) {
                if(message.isSentByMe)
                    Text(
                        text = message.message,
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                else
                    Column (){
                        Text(
                            text = message.message,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                        Text(
                            text = message.sender.toString(),
                            fontSize = 5.sp,
                            color = Color.Black
                        )
                    }
            }
            Text(
                text =  message.dateTime.format(formatter) ,
                fontSize = 13.sp,
                color = Color.Gray
            )
        }

    }
}



@Composable
fun ConnectionStateMessage(message: Message) {
    val formatter = DateTimeFormatter.ofPattern("dd MMM, HH:mm")

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .padding(12.dp)
                .widthIn(max = 280.dp)
        ) {
            Text(
                text = message.message,
                fontSize = 10.sp,
                color = Color.Gray
            )
            Text(
                text =  message.dateTime.format(formatter) ,
                fontSize = 10.sp,
                color = Color.Gray
            )
        }
    }
}