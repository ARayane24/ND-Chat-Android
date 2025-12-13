package com.example.ndchat.model

import Host
import java.util.LinkedList

data class VotingOption(
    var optionName : String,
    var hostsList: LinkedList<Host>
) {
    override fun toString(): String {
        return "{$optionName, $hostsList}"
    }
}
