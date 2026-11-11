package com.raju.aiassistant.domain.vision

/** What the user wants done with the chosen pictures. */
enum class VisionTask { ASK, READ_TEXT, COMPARE }

/**
 * The instruction sent with the pictures. It always asks the model to separate what it sees from what it
 * guesses, and to answer in the user's language.
 */
fun visionPrompt(task: VisionTask, question: String, imageCount: Int, hindi: Boolean): String {
    val q = question.trim()
    val language = if (hindi) "Answer in simple, natural Hindi (Devanagari)." else "Answer in Bengali."
    val honesty = "Say only what is visible; if something is unclear or a guess, say so."
    val body = when (task) {
        VisionTask.ASK ->
            if (q.isNotEmpty()) "Question about the picture${if (imageCount > 1) "s" else ""}: $q"
            else "Describe what is in the picture${if (imageCount > 1) "s" else ""}: the main objects and where they are, the place or scene, and any important text."
        VisionTask.READ_TEXT ->
            "Copy out all the text in the picture${if (imageCount > 1) "s, one picture after another" else ""}, exactly as written, in its own language and script (Hindi, English, Bengali …), keeping lines and order. Do not translate, correct or add anything. If there is no readable text, say so." +
                if (q.isNotEmpty()) " Then answer: $q" else ""
        VisionTask.COMPARE ->
            "Compare the $imageCount pictures. List what is the same, then every difference you can see: objects added, missing or moved, changed text or numbers, layout and colour." +
                if (q.isNotEmpty()) " Pay special attention to: $q" else ""
    }
    return "$body $honesty $language"
}

/**
 * For the Tatkal passenger card: one line per identity card. The ID number is not asked for and must
 * not come back; the age is never asked for either — the app works it out from the year.
 */
const val ID_PASSENGER_PROMPT =
    "These are photos of identity cards (Aadhaar, PAN, voter ID, driving licence). For each person write exactly one line: " +
        "FULL NAME in English capital letters | date of birth as DD/MM/YYYY (or only the 4-digit year of birth when that is all that is printed) | M or F as printed. " +
        "Example: RAJESH SINGH | 15/08/1986 | M. Leave the gender empty when it is not printed. " +
        "Skip a card whose name or year of birth cannot be read clearly — do not guess. " +
        "Do not write the ID number, the address, an age or any other text."
