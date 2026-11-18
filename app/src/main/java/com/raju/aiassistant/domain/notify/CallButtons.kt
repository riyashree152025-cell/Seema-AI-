package com.raju.aiassistant.domain.notify

/**
 * The Answer / Decline buttons of a ringing call on the screen (the dialer's full-screen call UI or the
 * heads-up notification), found by what they say or are called. Used when Android's TelecomManager does not
 * answer or cut a call (some Samsung / Xiaomi / Oppo ROMs ignore acceptRingingCall() / endCall(), or the
 * Phone permission is missing): the accessibility service then presses the button itself.
 * [label] is the button's text, description and view id joined by spaces.
 */
object CallButtons {
    // "Answer with message" / "Reply with message" are quick replies, never the answer button.
    private val QUICK_REPLY = Regex("message|msg|संदेश|मैसेज|sms|reply|বার্তা", RegexOption.IGNORE_CASE)
    private val ANSWER = Regex(
        "answer|accept|pick[ _]?up|receive|उठाएं|उठाएँ|उठाओ|उठा|उत्तर|(?<!अ)स्वीकार|रिसीव|ধরুন|ধরো|রিসিভ|incoming_call_answer|btn_answer|accept_call",
        RegexOption.IGNORE_CASE,
    )
    private val DECLINE = Regex(
        "decline|reject|hang ?up|end[ _]?call|dismiss|अस्वीकार|अस्वीकृत|काटें|काटो|कट|नकार|বাতিল|কাটুন|btn_decline|decline_call|reject_call",
        RegexOption.IGNORE_CASE,
    )

    /** True for the button that picks the call up. */
    fun isAnswer(label: String): Boolean =
        label.isNotBlank() && ANSWER.containsMatchIn(label) && !DECLINE.containsMatchIn(label) && !QUICK_REPLY.containsMatchIn(label)

    /** True for the button that cuts the call. */
    fun isDecline(label: String): Boolean =
        label.isNotBlank() && DECLINE.containsMatchIn(label) && !ANSWER.containsMatchIn(label) && !QUICK_REPLY.containsMatchIn(label)
}
