package com.example.ndchat.model

import Host

data class VotingOption(
    var optionName : String,
    var hostsList: List<Host>
) {
    override fun toString(): String {
        return "{$optionName, $hostsList}"
    }
}
