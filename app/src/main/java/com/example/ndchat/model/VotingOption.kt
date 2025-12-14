package com.example.ndchat.model

import Host
import java.util.LinkedList

data class VotingOption(
    val optionName: String,
    val hostsList: LinkedList<Host> = LinkedList<Host>()
)
 {
    override fun toString(): String {
        return "{$optionName, $hostsList}"
    }

     override fun equals(other: Any?): Boolean {
         return if (other is VotingOption)
             this.optionName.equals( other.optionName)
         else false
     }


}
