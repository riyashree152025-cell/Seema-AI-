package com.raju.aiassistant.domain.assistant

import java.net.URLEncoder

/**
 * "Chrome kholo aur ChatGPT search karo", "YouTube pe Kishore Kumar ke gaane dhundo", "Play Store me Paytm
 * search karo": a search inside an app opens straight on its results through the app's own link — no
 * buttons to find on the screen (that needed Accessibility and broke with every app update).
 */
private fun enc(q: String) = URLEncoder.encode(q.trim(), "UTF-8")

/** The link that opens [query]'s results in [app] (null / unknown app: Google in the browser). */
fun appSearchUrl(app: String?, query: String): String {
    val a = app?.lowercase()?.replace(" ", "").orEmpty()
    return when {
        a.contains("youtube") || a.contains("यूट्यूब") -> "https://www.youtube.com/results?search_query=${enc(query)}"
        a.contains("map") || a.contains("मैप") -> "geo:0,0?q=${enc(query)}"
        a.contains("playstore") || a.contains("प्लेस्टोर") || a == "play" -> "market://search?q=${enc(query)}"
        a.contains("amazon") || a.contains("अमेज़न") || a.contains("अमेजन") -> "https://www.amazon.in/s?k=${enc(query)}"
        a.contains("flipkart") || a.contains("फ्लिपकार्ट") -> "https://www.flipkart.com/search?q=${enc(query)}"
        a.contains("spotify") || a.contains("स्पॉटिफाई") -> "spotify:search:${enc(query)}"
        else -> "https://www.google.com/search?q=${enc(query)}"
    }
}

private const val APP_WORD = "chrome|browser|google|youtube|you tube|maps?|google maps|play ?store|amazon|flipkart|spotify|" +
    "क्रोम|ब्राउज़र|ब्राउजर|गूगल|यूट्यूब|मैप्स?|प्ले ?स्टोर|अमेज़न|अमेजन|फ्लिपकार्ट|स्पॉटिफाई"
private const val OPEN = "(?:khol(?:o|kar|ke)?|open(?:\\s+kar(?:o|ke)?)?|खोल(?:ो|कर|के)?)(?:\\s+(?:kar|ke|कर|के))?"
private const val SEARCH = "(?:search|सर्च|dhundo|dhoondo|dhundho|ढूंढो|ढूँढो|khojo|खोजो)(?:\\s+(?:karo|kar do|kijiye|करो|कर दो|कीजिए))?"
private const val IN = "(?:me|mein|में|मे|par|pe|पर|पे)"

// "<app> (kholo) (aur) (usme) <query> search karo"
private val APP_FIRST = Regex("^($APP_WORD)\\s+(?:$OPEN\\s+)?(?:(?:aur|और|phir|फिर)\\s+)?(?:(?:us|उस)\\s*$IN\\s+|$IN\\s+)?(.{1,80}?)\\s+$SEARCH$", RegexOption.IGNORE_CASE)
// "<query> (ko) <app> me search karo"
private val QUERY_FIRST = Regex("^(.{1,80}?)\\s+(?:(?:ko|को)\\s+)?($APP_WORD)\\s+$IN\\s+$SEARCH$", RegexOption.IGNORE_CASE)

data class AppSearchRequest(val app: String, val query: String)

fun parseAppSearch(text: String): AppSearchRequest? {
    val t = text.trim().trimEnd('.', '!', '?', '।', ' ').replace(Regex("\\s+"), " ")
    APP_FIRST.matchEntire(t)?.let { m -> return m.groupValues[2].trim().takeIf { it.isNotEmpty() }?.let { AppSearchRequest(m.groupValues[1], it) } }
    QUERY_FIRST.matchEntire(t)?.let { m -> return m.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let { AppSearchRequest(m.groupValues[2], it) } }
    return null
}
