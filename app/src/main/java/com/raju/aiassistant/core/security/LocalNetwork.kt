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

/**
 * True when what is sent to [url] cannot be read on the way: https://, an address on this phone itself
 * (localhost, 127.x, ::1), or a Tailscale name (*.ts.net — the tunnel is WireGuard-encrypted).
 * Plain http:// to a Wi-Fi address (192.168.x, 10.x …) is NOT encrypted: anyone on the same network can
 * read it. A Tailscale 100.x address alone does not count: the same range is also used by some routers.
 */
fun isEncryptedOrOnDevice(url: String): Boolean {
    val uri = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    val host = uri.host?.lowercase()?.removePrefix("[")?.removeSuffix("]") ?: return false
    if (scheme == "https") return true
    if (scheme != "http") return false
    if (host == "localhost" || host == "::1" || host == "0:0:0:0:0:0:0:1" || host.startsWith("127.")) return true
    return host.endsWith(".ts.net")
}

/** Where the PC companion may be reached, decided before anything (the token, memories) is sent. */
sealed interface PcSyncTarget {
    /** No address saved: PC sync is off. Nothing is sent anywhere. */
    data object NotSet : PcSyncTarget
    /** Plain http on the Wi-Fi: refused, the token and memories would be readable on the network. */
    data class Insecure(val baseUrl: String) : PcSyncTarget
    data class Ready(val baseUrl: String) : PcSyncTarget
}

/** "192.168.1.5:11435" → http://192.168.1.5:11435, then decides whether it may be used (see [PcSyncTarget]). */
fun pcSyncTarget(endpoint: String?): PcSyncTarget {
    val raw = endpoint?.trim().orEmpty()
    if (raw.isEmpty()) return PcSyncTarget.NotSet
    val withScheme = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw else "http://$raw"
    val base = withScheme.trimEnd('/')
    return if (isEncryptedOrOnDevice(base)) PcSyncTarget.Ready(base) else PcSyncTarget.Insecure(base)
}
