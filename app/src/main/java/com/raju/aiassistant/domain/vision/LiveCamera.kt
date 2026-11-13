package com.raju.aiassistant.domain.vision

/**
 * Live camera: "live camera chalu karo" — for the next few minutes every question that is not a phone
 * command is answered from a fresh photo of the same camera ("ye kya hai", "iska price tag padho"), so
 * the user can move the phone and keep asking. "Camera band karo" or the time running out ends it.
 * One photo per question, taken only when a question is asked; nothing is recorded in between.
 */
sealed interface LiveCameraCommand {
    data class Start(val front: Boolean) : LiveCameraCommand
    data object Stop : LiveCameraCommand
}

/** How long live camera stays on after the last question. */
const val LIVE_CAMERA_MS = 120_000L

private val CAMERA = "(?:camera|kamera|कैमरा|कैमरे)"
private val LIVE = Regex(
    "^(?:live $CAMERA|$CAMERA live|लाइव $CAMERA|$CAMERA लाइव|$CAMERA (?:mode|मोड))(?: (?:mode|मोड))?" +
        "(?: (?:chalu|chaalu|on|start|shuru|चालू|ऑन|शुरू|स्टार्ट))?(?: (?:karo|kar do|करो|कर दो))?$",
    RegexOption.IGNORE_CASE,
)
private val FRONT = Regex("front|selfie|aage|आगे|फ्रंट|सेल्फी", RegexOption.IGNORE_CASE)
private val STOP = Regex(
    "^(?:live )?(?:$CAMERA|लाइव $CAMERA)(?: (?:live|mode|लाइव|मोड))? (?:band|off|stop|bas|बंद|ऑफ|बस|रोको)(?: (?:karo|kar do|करो|कर दो))?$",
    RegexOption.IGNORE_CASE,
)

fun parseLiveCamera(text: String): LiveCameraCommand? {
    val t = text.trim().trimEnd('.', '!', '?', '।').replace(Regex("\\s+"), " ").lowercase()
    if (STOP.matches(t)) return LiveCameraCommand.Stop
    val withoutSide = t.replace(Regex("\\b(?:front|back|selfie|peeche|pichhe) (?:wala|wali|wale)?\\s*"), "")
        .replace(Regex("(?:आगे|पीछे|फ्रंट|सेल्फी) (?:वाला|वाली|वाले)?\\s*"), "").trim()
    if (LIVE.matches(withoutSide)) return LiveCameraCommand.Start(front = FRONT.containsMatchIn(t))
    return null
}

private val LOOK = Regex("dikh|dikha|dekh|dekho|batao|bata|kya hai|kaun hai|दिख|देख|बताओ|बता|क्या है|कौन है", RegexOption.IGNORE_CASE)
private val CAMERA_WORD = Regex("camera|kamera|कैमरा|कैमरे", RegexOption.IGNORE_CASE)
private val FRONT_SIDE = Regex("front|selfie|aage|आगे|फ्रंट|सेल्फी|samne|सामने", RegexOption.IGNORE_CASE)

/**
 * "Back camera chalu karo, peeche kya dikh raha hai batao" / "camera se dekh ke batao ye kya hai": one
 * photo now and an answer about it (front camera when "front / selfie / aage / samne" is said). Not
 * "camera kholo" (that opens the camera app) and not live camera (parsed first).
 */
fun parseLookCamera(text: String): Pair<Boolean, String>? {
    val t = text.trim()
    if (t.length > 160 || !CAMERA_WORD.containsMatchIn(t) || !LOOK.containsMatchIn(t)) return null
    if (parseLiveCamera(t) != null) return null
    return FRONT_SIDE.containsMatchIn(t) to t
}
