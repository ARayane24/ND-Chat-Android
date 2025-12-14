package com.example.ndchat.model

import Host

data class Message(
    var message: String,
    var isSentByMe: Boolean = true,
    var sender: Host? = null,
    var voting: Voting? = null,
    // Using LocalDateTime requires API Desugaring for support below Android O (API 26)
    var dateTime: Long = System.currentTimeMillis()
){
    override fun equals(other: Any?): Boolean {
        return if (other is Message)
            this.message == other.message &&
            this.sender == other.sender &&
            this.voting?.equals( other.voting) == true &&
            this.dateTime == other.dateTime
        else false

    }
}