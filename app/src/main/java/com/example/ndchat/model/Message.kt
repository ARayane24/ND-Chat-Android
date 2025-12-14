package com.example.ndchat.model

import Host // Assuming Host is defined elsewhere
import java.time.LocalDateTime

data class Message(
    var message: String,
    var isSentByMe: Boolean = true,
    var sender: Host? = null,
    var voting: Voting? = null,
    // Using LocalDateTime requires API Desugaring for support below Android O (API 26)
    var dateTime: LocalDateTime = LocalDateTime.now()
)