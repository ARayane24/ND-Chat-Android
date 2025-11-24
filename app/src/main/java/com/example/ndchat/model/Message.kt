package com.example.ndchat.model

import Host // Assuming Host is defined elsewhere
import java.time.LocalDateTime

data class Message(
    val message: String,
    val isSentByMe: Boolean,
    val sender: Host?,
    // Using LocalDateTime requires API Desugaring for support below Android O (API 26)
    val dateTime: LocalDateTime = LocalDateTime.now()
)