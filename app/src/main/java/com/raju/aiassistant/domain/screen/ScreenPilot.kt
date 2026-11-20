package com.raju.aiassistant.domain.screen

/**
 * "Neeche wala button daba do", "pehla button", "doosra wala", "সবচেয়ে নিচের বোতাম": a button named by where it is,
 * not by its text. The accessibility tree lists elements top to bottom (reading order), so "upar wala / pehla" is
 * the first tappable button with a label and "neeche wala / aakhri" the last. The element found is then tapped by its
 * own text through the usual confirm-and-verify path; nothing is ever tapped without the user's yes.
 */
object ScreenPilot {
    sealed interface Position {
        data object First : Position
        data object Last : Position
        data class Nth(val index: Int) : Position
    }

    private fun word(w: String) = Regex("(?<![\\p{L}\\p{M}])(?:$w)(?![\\p{L}\\p{M}])", RegexOption.IGNORE_CASE)

    private val LAST = word("neeche|niche|nichey|bottom|last|aakhri|akhri|antim|नीचे|आखिरी|आख़िरी|अंतिम|নিচের|নিচে|শেষের|শেষ")
    private val FIRST = word("upar|upper|oopar|top|first|pehla|pahla|pehle|ऊपर|ऊपर|पहला|पहले|ওপরের|উপরের|প্রথম")
    private val SECOND = word("doosra|dusra|second|दूसरा|দ্বিতীয়")
    private val THIRD = word("teesra|tisra|third|तीसरा|তৃতীয়")
    /** Only "the … one / button", never a real label like "Next" with an extra word. */
    private val SHAPE = Regex("^(?:[\\p{L}\\p{M}]+\\s*){1,4}$")
    private val FILLER = word("wala|waala|vala|wali|waali|button|batan|बटन|वाला|वाली|ওয়ালা|বোতাম|টা|का|ka")

    /** The position named in [target], or null when [target] is a button's own text. */
    fun positionOf(target: String): Position? {
        val t = target.trim()
        if (t.isEmpty() || !SHAPE.matches(t)) return null
        // A real label ("Top up", "First name") still has its own text after the position words are taken away.
        val rest = FILLER.replace(LAST.replace(FIRST.replace(SECOND.replace(THIRD.replace(t, " "), " "), " "), " "), " ").trim()
        if (rest.isNotEmpty()) return null
        return when {
            THIRD.containsMatchIn(t) -> Position.Nth(2)
            SECOND.containsMatchIn(t) -> Position.Nth(1)
            LAST.containsMatchIn(t) -> Position.Last
            FIRST.containsMatchIn(t) -> Position.First
            else -> null
        }
    }

    /** The tappable, labelled buttons of the screen, in reading order (password boxes and blank icons never). */
    fun buttons(snapshot: ScreenSnapshot): List<ScreenElement> =
        snapshot.elements.filter { it.clickable && !it.password && it.text.isNotBlank() && it.kind != ElementKind.INPUT }

    /**
     * The button [target] points at by position, or null when it names none, the screen has no such button, or the
     * button's text appears twice (then tapping by text could hit the other one — the user is asked instead).
     */
    fun resolve(target: String, snapshot: ScreenSnapshot): ScreenElement? {
        val position = positionOf(target) ?: return null
        val list = buttons(snapshot)
        val element = when (position) {
            Position.First -> list.firstOrNull()
            Position.Last -> list.lastOrNull()
            is Position.Nth -> list.getOrNull(position.index)
        } ?: return null
        return element.takeIf { e -> list.count { ScreenMatch.normalize(it.text) == ScreenMatch.normalize(e.text) } == 1 }
    }

    /** "Isme kal milte hain likh do", "এখানে লেখো ঠিক আছে": what to type into the box on screen, or null. */
    private val TYPE_HERE = Regex(
        "^(?:isme|ismein|ismen|yahan|yaha|is box mein|इसमें|इसमे|यहाँ|यहां|এতে|এখানে)\\s+(.{1,200}?)\\s+(?:likh do|likho|likh de|type karo|type kar do|लिख दो|लिखो|लिख दे|टाइप करो|লিখে দাও|লেখো|লিখে দে)$",
        RegexOption.IGNORE_CASE,
    )

    fun typeHere(text: String): String? =
        TYPE_HERE.matchEntire(text.trim().trimEnd('.', '!', '।', ' ').replace(Regex("\\s+"), " "))
            ?.groupValues?.get(1)?.trim()?.trim('"', '\'', '“', '”')?.takeIf { it.isNotBlank() }
}
