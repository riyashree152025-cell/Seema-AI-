package com.raju.aiassistant.domain.voice

/** How speech recognition writes "Seema" (Hindi and Latin script, common mishearings). */
private val WAKE_WORDS = listOf("seema", "sima", "shima", "seemaa", "sema", "सीमा", "सिमा", "शीमा", "सीमां", "सीमाजी", "सीमा जी")
private val LEADING_FILLERS = Regex("^(hey|hi|hello|ok|okay|oye|arre|are|suno|हे|हाय|हेलो|अरे|सुनो|ओए)\\s+", RegexOption.IGNORE_CASE)

/**
 * In sleep mode Seema only reacts when called by name. Returns what was said after "Seema"
 * ("" if only her name was said), or null if her name was not in [heard] at all.
 * Examples: "Seema, time kya hua?" → "time kya hua?"; "hey सीमा" → ""; "TV ki awaaz" → null.
 */
fun afterWakeWord(heard: String): String? {
    val text = heard.trim()
    val lower = text.lowercase()
    var best: IntRange? = null
    for (w in WAKE_WORDS) {
        val m = Regex("(^|[^\\p{L}\\p{M}])" + Regex.escape(w) + "(?=$|[^\\p{L}\\p{M}])").find(lower) ?: continue
        val range = (m.range.first + m.groupValues[1].length)..m.range.last
        if (best == null || range.first < best.first) best = range
    }
    val hit = best ?: return null
    // The name should open the sentence (allowing "hey"/"arre" before it), not be buried in other talk.
    val before = text.substring(0, hit.first).trim().replace(LEADING_FILLERS, "").trim().trimEnd(',', '!', '.')
    if (before.split(Regex("\\s+")).count { it.isNotBlank() } > 1) return null
    return text.substring(hit.last + 1).trim().trimStart(',', '!', '.', '?', '।', ' ').trim()
}

/** The offline "Hey Seema" detector's model (sherpa-onnx keyword spotting, English GigaSpeech, int8). */
data class WakeWordModel(
    override val id: String,
    override val title: String,
    override val url: String,
    override val sizeBytes: Long,
    override val sha256: String,
    override val dirName: String,
    override val modelFile: String,
    val decoderFile: String,
    val joinerFile: String,
    val license: String,
    override val version: Int = 1,
) : DownloadableModel

/** Checked against the real archive (size, SHA-256, files, licence) in Nov 2026. */
val WAKE_WORD_MODEL = WakeWordModel(
    id = "kws-gigaspeech-3.3M-int8",
    title = "Offline wake word \"Hey Seema\" (sherpa-onnx keyword spotter)",
    url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01.tar.bz2",
    sizeBytes = 17_626_723,
    sha256 = "f170013b4716e41b62b9bfd809687c207cef798ef9bc6534d524e17af9b6561a",
    dirName = "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01",
    modelFile = "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
    decoderFile = "decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
    joinerFile = "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
    license = "Apache-2.0",
)

/**
 * "Hey Seema" and close variants as the model's BPE tokens (made with the archive's bpe.model).
 * ":2.0" boosts the phrase. Tested on synthetic speech only; real-voice accuracy must be checked on
 * the phone.
 */
val WAKE_WORD_KEYWORDS: String = listOf(
    "▁HE Y ▁SEE MA", // HEY SEEMA
    "▁HE Y ▁S IM A", // HEY SIMA
    "▁HE Y ▁SE MA", // HEY SEMA
    "▁HI ▁SEE MA", // HI SEEMA
    "▁HA Y ▁SEE MA", // HAY SEEMA
    "▁O K ▁SEE MA", // OK SEEMA
).joinToString("/") { "$it :2.0 @HEY_SEEMA" }

/** How sure the spotter must be (0..1); lower wakes more easily. 0.1 caught 4 of 6 synthetic "Hey Seema" with 0 of 6 false wakes. */
const val WAKE_WORD_THRESHOLD = 0.1f
