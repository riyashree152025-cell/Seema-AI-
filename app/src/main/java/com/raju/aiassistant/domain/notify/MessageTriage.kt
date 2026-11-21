package com.raju.aiassistant.domain.notify

/**
 * Which incoming messages matter first, and which are scams. Money and railway messages, and the people Raju
 * deals with, are said before the rest; a message that looks like fraud ("KYC update karo", "lottery jeeti",
 * "account block ho jayega, link par click karo") is never read as normal — Seema warns him instead.
 * Everything is decided on the phone from the notification's own words.
 */
object MessageTriage {
    private fun words(vararg w: String) = Regex("(?<![\\p{L}\\p{M}])(?:${w.joinToString("|")})(?![\\p{L}\\p{M}])", RegexOption.IGNORE_CASE)

    private val BANK_OR_RAIL = words(
        "credited", "debited", "a/c", "upi", "neft", "imps", "rs", "inr", "₹", "pnr", "irctc", "train", "chart", "berth", "coach", "refund",
        "जमा", "डेबिट", "क्रेडिट", "पीएनआर", "ट्रेन", "रिफंड", "টাকা", "জমা", "ট্রেন",
    )
    private val BANK_APPS = setOf("com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging", "com.irctc.railconnect", "cris.org.in.prs.ima")

    private val SCAM = listOf(
        words("kyc", "केवाईसी", "কেওয়াইসি").pattern + ".{0,60}" + words("update", "expire", "expired", "block", "blocked", "suspend", "अपडेट", "बंद", "ब्लॉक").pattern,
        words("lottery", "lucky draw", "jackpot", "you have won", "you won", "prize", "लॉटरी", "इनाम", "जीत गए", "লটারি").pattern,
        words("account", "a/c", "खाता", "অ্যাকাউন্ট").pattern + ".{0,60}" + words("block", "blocked", "suspend", "suspended", "freeze", "बंद", "ब्लॉक").pattern + ".{0,80}(?:https?://|bit\\.ly|click|क्लिक|लिंक)",
        words("electricity", "bijli", "बिजली", "বিদ্যুৎ").pattern + ".{0,60}" + words("disconnect", "disconnected", "cut", "कट", "कटेगी", "काट").pattern + ".{0,80}(?:call|कॉल|\\d{10})",
        words("share", "send", "बताएं", "बताइए", "भेजें").pattern + ".{0,40}" + words("otp", "ओटीपी", "pin", "cvv").pattern,
        words("work from home", "part time job", "daily earn", "per day earn", "घर बैठे कमाएं", "telegram task").pattern,
        words("customs", "parcel", "courier").pattern + ".{0,60}" + words("pay", "fee", "charge", "शुल्क").pattern + ".{0,60}(?:https?://|bit\\.ly)",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    /** A message that looks like a scam (the reason is not needed: the warning is the same). */
    fun looksLikeFraud(text: String?): Boolean = !text.isNullOrBlank() && SCAM.any { it.containsMatchIn(text) }

    /** Bank, UPI or railway news, or a person he has a khata with: said before other messages. */
    fun isPriority(item: IncomingItem, knownPeople: Set<String> = emptySet()): Boolean {
        val who = (item.sender ?: item.group).orEmpty().trim().lowercase()
        return item.packageName in BANK_APPS || BANK_OR_RAIL.containsMatchIn(item.preview.orEmpty()) ||
            (who.isNotEmpty() && knownPeople.any { it.lowercase() == who })
    }

    /** Said instead of reading a scam message aloud. */
    fun fraudWarning(item: IncomingItem): String =
        "सावधान! ${whoText(item)} का मैसेज धोखे जैसा लग रहा है — इसमें कोई लिंक मत खोलना, OTP या PIN किसी को मत बताना।"
}
