package com.example.ndchat.model


data class Voting(
    var title : String,
    var description: String,
    var options: List<VotingOption>
) {
    override fun toString(): String {
        return "{$title,$description,$options}"
    }
}
