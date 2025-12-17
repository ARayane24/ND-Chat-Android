package com.example.ndchat.utils

import java.net.ServerSocket

fun isPortAvailable(port: Int): Boolean {
    return try {
        ServerSocket(port).use { true }
    } catch (e: Exception) {
        false
    }
}

fun isValidAndFreePort(text: String): Boolean {
    val port = text.toIntOrNull() ?: return false
    if (port !in 1..65535) return false
    return isPortAvailable(port)
}

fun isValidIPv4(ip: String): Boolean {
    val regex = Regex(
        "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}" +
                "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$"
    )
    return ip.matches(regex)
}

fun isIpInputAllowed(text: String): Boolean {
    return text.matches(Regex("^[0-9.]*$")) // digits + dots only
}
