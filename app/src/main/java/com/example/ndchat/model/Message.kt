package com.example.ndchat.model

import Host

class Message(
    val message: String,
    val isSentByMe: Boolean,
    val sender: Host?
)