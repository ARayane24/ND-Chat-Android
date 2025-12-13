package com.example.ndchat.model

import Host


data class Voting(
    var title : String,
    var description: String,
    var options: List<VotingOption>
) {
    override fun toString(): String {
        return "{$title,$description,$options}"
    }
    fun hasHostVoted(host: Host): Boolean {
        return options.any { option ->
            option.hostsList.any { it.uuid == host.uuid } // match by unique ID
        }
    }

    fun addVote(option: VotingOption, host: Host) {
        // Prevent duplicate votes
        if (!options.any { it.hostsList.contains(host) }) {
            option.hostsList.add(host)
        }
    }


}
