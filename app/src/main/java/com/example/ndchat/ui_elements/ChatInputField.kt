package com.example.ndchat.ui_elements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatInputField(modifier: Modifier , inputText: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
    Box(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp)
    ) {
        BasicTextField(
            value = inputText,
            onValueChange = onValueChange,
            textStyle = TextStyle(fontSize = 16.sp, color = Color.Black),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (inputText.text.isEmpty()) {
            Text(
                text = "Type a message...",
                color = Color.Gray,
                fontSize = 16.sp
            )
        }
    }
}
