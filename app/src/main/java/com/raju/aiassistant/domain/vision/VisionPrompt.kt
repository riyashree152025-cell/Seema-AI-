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
