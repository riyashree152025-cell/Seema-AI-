package com.raju.aiassistant.domain.ai

import com.raju.aiassistant.domain.model.ConnectionTest

/**
 * What the AI Brain screen shows for one provider. CONNECTED only after a real successful API
 * response for the key that is saved now; a saved but untested key is KEY_SAVED.
 */
enum class ProviderStatus(val label: String, val help: String) {
    NOT_CONFIGURED("NOT CONFIGURED", "No API key saved."),
    KEY_SAVED("KEY SAVED", "Key saved but not tested yet. Tap Test connection."),
    TESTING("TESTING CONNECTION", "Contacting the provider…"),
    CONNECTED("CONNECTED", "The provider answered a real request with this key."),
    AUTH_FAILED("AUTHENTICATION FAILED", "The provider rejected the request (no permission for this key or model)."),
    INVALID_KEY("INVALID API KEY", "Invalid API key. Please check your key."),
    RATE_LIMITED("RATE LIMIT EXCEEDED", "Rate limit exceeded. Try again later."),
    QUOTA_EXCEEDED("QUOTA EXCEEDED", "The free quota or credits of this key are used up."),
    NETWORK_ERROR("NETWORK ERROR", "Network connection unavailable or too slow."),
    UNAVAILABLE("PROVIDER UNAVAILABLE", "Provider service is temporarily unavailable."),
    FAILED("REQUEST FAILED", "The provider returned an unexpected error."),
    ;

    val isError: Boolean get() = this !in setOf(NOT_CONFIGURED, KEY_SAVED, TESTING, CONNECTED)
}

private val HTTP_CODE = Regex("""\(HTTP (\d{3})\)""")

/** HTTP status code inside a failure message made by `httpFailure` ("… (HTTP 401): …"). */
fun httpCodeOf(message: String?): Int? = message?.let { HTTP_CODE.find(it)?.groupValues?.get(1)?.toIntOrNull() }

fun statusFor(kind: FailureKind?, httpCode: Int?): ProviderStatus = when (kind) {
    FailureKind.NOT_CONFIGURED -> ProviderStatus.NOT_CONFIGURED
    FailureKind.INVALID_KEY -> if (httpCode == 403) ProviderStatus.AUTH_FAILED else ProviderStatus.INVALID_KEY
    FailureKind.RATE_LIMITED -> ProviderStatus.RATE_LIMITED
    FailureKind.QUOTA_EXCEEDED -> ProviderStatus.QUOTA_EXCEEDED
    FailureKind.NETWORK, FailureKind.TIMEOUT -> ProviderStatus.NETWORK_ERROR
    FailureKind.SERVER -> ProviderStatus.UNAVAILABLE
    // null = a result saved by an older version: only its HTTP code is known.
    null, FailureKind.BLOCKED, FailureKind.UNKNOWN -> when (httpCode) {
        401 -> ProviderStatus.INVALID_KEY
        403 -> ProviderStatus.AUTH_FAILED
        429 -> ProviderStatus.RATE_LIMITED
        in 500..599 -> ProviderStatus.UNAVAILABLE
        else -> ProviderStatus.FAILED
    }
}

/** Status of one provider from what is really known: key saved?, test running?, last real test. */
fun providerStatus(keySaved: Boolean, testing: Boolean, lastTest: ConnectionTest?): ProviderStatus = when {
    !keySaved -> ProviderStatus.NOT_CONFIGURED
    testing -> ProviderStatus.TESTING
    lastTest == null -> ProviderStatus.KEY_SAVED
    lastTest.success -> ProviderStatus.CONNECTED
    else -> statusFor(lastTest.kind, httpCodeOf(lastTest.message))
}

// Token-like strings of the providers this app talks to (Gemini AIza…, Groq gsk_…, Cerebras csk-…,
// OpenAI-style sk-…, Tavily tvly-…) and any long opaque token.
private val TOKEN = Regex("""\b(AIza[0-9A-Za-z_\-]{20,}|gsk_[0-9A-Za-z]{16,}|csk-[0-9A-Za-z]{16,}|sk-[0-9A-Za-z_\-]{16,}|tvly-[0-9A-Za-z_\-]{12,}|[0-9A-Za-z_\-]{40,})\b""")

/**
 * Removes the API key (and anything that looks like a key) from text shown in the UI or stored as
 * the last error, in case a provider echoes the key back in an error message.
 */
fun redactSecrets(text: String, key: String? = null): String {
    var out = text
    if (!key.isNullOrBlank() && key.length >= 8) out = out.replace(key, "[key hidden]")
    return out.replace(TOKEN, "[key hidden]")
}
