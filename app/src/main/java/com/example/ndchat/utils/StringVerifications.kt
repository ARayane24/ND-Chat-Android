package com.example.ndchat.utils

fun String.hasFormOf(regex: String): Boolean {
    return this.matches(regex.toRegex())
}
