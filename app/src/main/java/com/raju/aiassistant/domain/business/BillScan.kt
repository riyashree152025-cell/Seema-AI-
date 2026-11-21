package com.raju.aiassistant.domain.business

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * "Bill ki photo lo aur kharcha likho", "is bill ka kharcha khate mein daalo", "বিলের ছবি তুলে খরচ লেখো": one photo of a
 * bill / receipt, its total and shop read by Gemini, then "₹1,240 का खर्च (Sharma Store) खाते में लिख दूँ?" — saved only on
 * his yes. Nothing is guessed: no clear total, nothing saved.
 */
data class ScannedBill(val amountPaise: Long, val payee: String?, val category: String)

object BillScan {
    private val ASK = Regex(
        "^(?:seema[, ]+)?(?:(?:is|iss|ye|yeh|इस|ये|এই)\\s+)?(?:bill|receipt|parchi|बिल|रसीद|पर्ची|বিল|রসিদ)\\s*(?:ki|ka|की|का|এর|ের)?\\s*" +
            "(?:photo|foto|फ़ोटो|फोटो|ছবি)?\\s*(?:lo|le lo|leke|khinch kar|लो|ले लो|लेकर|তুলে)?\\s*(?:aur|और|আর)?\\s*" +
            "(?:kharcha|kharch|expense|खर्चा|खर्च|খরচ)\\s*(?:khate\\s*(?:me|mein)\\s*)?(?:खाते\\s*में\\s*)?(?:likho|likh do|daalo|daal do|jodo|लिखो|लिख दो|डालो|डाल दो|जोड़ो|লেখো|লিখে দাও)[?।.!]*$",
        RegexOption.IGNORE_CASE,
    )

    fun asks(text: String): Boolean = ASK.matches(text.trim())

    const val PROMPT =
        "This photo should show a bill, receipt or invoice. Reply with ONLY one JSON object: " +
            "{\"total\": <the final amount paid in rupees as a number, or null if you cannot read it clearly>, " +
            "\"payee\": <shop / company name or null>, \"category\": <one of food, fuel, travel, bills, shopping, business, health, other>}. " +
            "Never guess a number you cannot read."

    private val CATEGORIES = setOf("food", "fuel", "travel", "bills", "shopping", "business", "health", "other")

    /** The bill in Gemini's reply, or null when the total is missing / unreadable. */
    fun parse(reply: String): ScannedBill? {
        val json = Regex("\\{[\\s\\S]*\\}").find(reply)?.value ?: return null
        val o = runCatching { Json.parseToJsonElement(json) as JsonObject }.getOrNull() ?: return null
        val total = (o["total"] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.replace(Regex("[^\\d.]"), "")?.toDoubleOrNull() } ?: return null
        if (total <= 0 || total > 10_000_000) return null
        val payee = (o["payee"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }?.take(40)
        val cat = (o["category"] as? JsonPrimitive)?.contentOrNull?.lowercase()?.takeIf { it in CATEGORIES } ?: "other"
        return ScannedBill(Math.round(total * 100), payee, cat)
    }

    fun question(b: ScannedBill): String =
        "₹${String.format(java.util.Locale.US, "%,d", b.amountPaise / 100)} का खर्च${b.payee?.let { " ($it)" } ?: ""} खाते में लिख दूँ?"
}
