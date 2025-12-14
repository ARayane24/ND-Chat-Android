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

    override fun equals(other: Any?): Boolean {
        return if (other is Voting)
            this.title == other.title &&
            this.description == other.description &&
                    sameOptionsList(other)
        else false
    }

    fun sameOptionsList ( other: Voting) : Boolean {
        if (this.options.size != other.options.size) return false
        for (i in 0 until this.options.size)
            if (!this.options[i].equals( other.options[i])) return false
        return true
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
