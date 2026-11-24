package com.raju.aiassistant.domain.assistant

/**
 * Real-time Indian Quick Info:
 * Recognizes everyday queries about live cricket scores, gold/silver rates, petrol/diesel prices,
 * and rain/weather radar, routing them directly to live search or real-time AI grounding.
 */
object IndianLiveInfo {

    private val CRICKET = Regex(
        "(?:cricket|match|score|क्रिकेट|मैच|स्कोर|স্কোর|ম্যাচ).{0,15}?(?:score|update|haal|kya hai|kya hua|kitna hua|batao|chal raha|চলছে|হলো|কত|কি|हाल|अपडेट|बताओ)|" +
            "(?:india|bharat|भारत|ইন্ডিয়া|ভারত).{0,15}?(?:match|score|मैच|स्कोर|স্কোর)",
        RegexOption.IGNORE_CASE,
    )

    private val GOLD_SILVER = Regex(
        "(?:sone|sona|gold|chandi|silver|चांदी|चाँदी|सोना|सोने|সোনা|সোনার|রুপো|রুপোর|রূপা).{0,15}?(?:bhav|rate|price|kimat|daam|kya hai|kitna hai|batao|भाव|रेट|दाम|कीमत|দাম|কত|বলো)",
        RegexOption.IGNORE_CASE,
    )

    private val FUEL = Regex(
        "(?:petrol|diesel|fuel|पेट्रोल|डीजल|পেট্রোল|পেট্রোলের|ডিজেল|ডিজেলের).{0,15}?(?:bhav|rate|price|kimat|daam|kya hai|kitna hai|batao|भाव|रेट|दाम|कीमत|দাম|কত|বলো)",
        RegexOption.IGNORE_CASE,
    )

    private val RAIN = Regex(
        "(?:barish|barsaat|baaris|বৃষ্টি|বৃষ্টির|बारिश|बरसात).{0,12}?(?:hogi|aaegi|aayegi|hobe|kya|kaisa|হবে|কি|होगी|आएगी|मौसम)|" +
            "^(?:seema[, ]+)?(?:kya\\s+)?(?:aaj|आज|আজকে)?\\s*(?:barish|barsaat|বৃষ্টি|बारिश)\\s*(?:hogi|hobe|होगी|হবে)[?।.!]*$",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Parses real-time queries for Cricket, Gold/Silver, Petrol/Diesel, or Rain Radar.
     * Returns an AssistantAction.SearchWeb action, or null if not a real-time query.
     */
    fun parse(text: String): AssistantAction? {
        val t = text.trim()
        if (t.length < 5 || t.length > 120) return null
        return when {
            CRICKET.containsMatchIn(t) ->
                AssistantAction.SearchWeb("live cricket score match today India", topic = "news")
            GOLD_SILVER.containsMatchIn(t) ->
                AssistantAction.SearchWeb("today 24k 22k gold price India silver rate per 10 gram", topic = "finance")
            FUEL.containsMatchIn(t) ->
                AssistantAction.SearchWeb("current petrol diesel price today India", topic = "general")
            RAIN.containsMatchIn(t) ->
                AssistantAction.SearchWeb("rain forecast today India live weather radar", topic = "weather")
            else -> null
        }
    }
}
