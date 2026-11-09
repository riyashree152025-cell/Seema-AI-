package com.raju.aiassistant.core.security

/**
 * Hosts that plain http:// may reach: this phone and the home network (the PC companion, Ollama on the
 * LAN). Everything else must be https://, so API keys, tokens and chats never cross the internet
 * unencrypted, even when an address is typed with http:// by mistake.
 */
fun isLocalNetworkHost(host: String): Boolean {
    val h = host.trim().lowercase().removePrefix("[").removeSuffix("]")
    // ".ts.net": a Tailscale name; the tunnel itself is encrypted (WireGuard), so http inside it is private.
    if (h == "localhost" || h.endsWith(".local") || h.endsWith(".ts.net") || h == "::1") return true
    if (':' in h) return h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe80") // IPv6 private / link-local
    val parts = h.split('.')
    if (parts.size != 4) return false
    val n = parts.map { it.toIntOrNull()?.takeIf { v -> v in 0..255 } ?: return false }
    return when {
        n[0] == 10 -> true
        n[0] == 127 -> true
        n[0] == 192 && n[1] == 168 -> true
        n[0] == 172 && n[1] in 16..31 -> true
        n[0] == 169 && n[1] == 254 -> true
        n[0] == 100 && n[1] in 64..127 -> true // carrier-grade NAT (some home routers, Tailscale)
        else -> false
    }
}
