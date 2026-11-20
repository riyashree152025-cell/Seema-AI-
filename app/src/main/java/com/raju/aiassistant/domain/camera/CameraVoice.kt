package com.raju.aiassistant.domain.camera

/**
 * "Video recording chalu karo" … "theek hai band kar do", and every way of asking for a photo ("photo lo", "photo
 * click karo", "camera kholo photo kheencho", "ছবি তোলো"). Seema records with the phone's own camera and microphone and
 * saves into the gallery (Movies/Seema, Pictures/Seema).
 */
sealed interface VideoStart {
    data object Started : VideoStart
    data class NeedsPermission(val permission: String, val reason: String) : VideoStart
    data class Failed(val message: String) : VideoStart
}

sealed interface VideoStop {
    /** [savedAs]: the file's name in the gallery; [seconds]: how long it is. */
    data class Saved(val savedAs: String, val seconds: Int) : VideoStop
    data object NotRecording : VideoStop
    data class Failed(val message: String) : VideoStop
}

object CameraVoice {
    private fun norm(text: String) = text.trim().trimEnd('.', '!', '?', '।', ' ').replace(Regex("\\s+"), " ").lowercase()
        .replace(Regex("^(?:seema|सीमा|সীমা)[, ]+"), "")
        .replace(Regex("^(?:(?:theek hai|thik hai|ठीक है|ok|okay|ओके|haan|हाँ|ঠিক আছে)[, ]+)"), "")

    private const val VIDEO = "(?:video|vdo|वीडियो|विडियो|ভিডিও)"
    private const val REC = "(?:recording|record|rec|रिकॉर्डिंग|रिकॉर्ड|रिकार्डिंग|রেকর্ডিং|রেকর্ড)"
    private const val FRONT = "(?:front|aage|aage wale|selfie|सामने|आगे|फ्रंट|সামনের|ফ্রন্ট)"
    private const val ON = "(?:chalu karo|chalu kar do|shuru karo|shuru kar do|start karo|start kar do|on karo|on kar do|karo|kar do|banao|bana do|" +
        "चालू करो|चालू कर दो|शुरू करो|शुरू कर दो|स्टार्ट करो|स्टार्ट कर दो|ऑन करो|करो|कर दो|बनाओ|बना दो|" +
        "চালু করো|চালু কর|শুরু করো|শুরু কর|স্টার্ট করো|করো|কর|বানাও)"
    private const val OFF = "(?:band karo|band kar do|band kardo|rok do|roko|stop karo|stop kar do|stop|khatam karo|bas karo|" +
        "बंद करो|बंद कर दो|रोक दो|रोको|स्टॉप करो|स्टॉप कर दो|स्टॉप|ख़त्म करो|खत्म करो|बस करो|" +
        "বন্ধ করো|বন্ধ কর|বন্ধ করে দাও|থামাও|থামো|স্টপ করো|স্টপ)"

    private val START = Regex(
        "^(?:(?:$FRONT|back|peeche|पीछे|পেছনের) (?:camera|कैमरा|कैमरे|ক্যামেরা) (?:se|से|দিয়ে) )?(?:ek |एक |একটা )?$VIDEO(?: $REC)?(?: (?:ko|को))? $ON$|" +
            "^$REC $ON$",
    )
    private val STOP = Regex("^(?:(?:$VIDEO|$REC|$VIDEO $REC|camera|कैमरा|ক্যামেরা)(?: (?:ko|को))? )?$OFF$")
    private val FRONT_WORD = Regex("^$FRONT ")

    /** "Video recording chalu karo" (back camera), "front camera se video banao" (front): which camera, or null. */
    fun startRequest(text: String): Boolean? {
        val t = norm(text)
        if (!START.matches(t)) return null
        return FRONT_WORD.containsMatchIn(t)
    }

    /** "Band kar do", "video band karo", "recording rok do": only while a video is being recorded. */
    fun isStop(text: String): Boolean = STOP.matches(norm(text))

    /** Spoken after saving: "1 मिनट 5 सेकंड का वीडियो …". */
    fun duration(seconds: Int, bengali: Boolean): String {
        val m = seconds / 60
        val s = seconds % 60
        return if (bengali) listOfNotNull(m.takeIf { it > 0 }?.let { "$it মিনিট" }, s.takeIf { it > 0 || m == 0 }?.let { "$it সেকেন্ড" }).joinToString(" ")
        else listOfNotNull(m.takeIf { it > 0 }?.let { "$it मिनट" }, s.takeIf { it > 0 || m == 0 }?.let { "$it सेकंड" }).joinToString(" ")
    }

    /** Longest recording; then it is saved by itself (a forgotten recording does not fill the phone). */
    const val MAX_SECONDS = 10 * 60
}
