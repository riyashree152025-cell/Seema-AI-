package com.raju.aiassistant.domain.remote

/**
 * Raju's PC / VPS bridge: "PC mein Cursor kholo", "VPS status check karo", "PC ko command bhejo: backup chala do".
 * The phone talks to the Seema PC Companion (scripts/pc_companion) at the address and token saved in Settings,
 * over https, Tailscale or this phone only. The companion opens only its listed apps, reports status, and only
 * queues instructions in a file for Raju's own tools — nothing he says is ever run as a command there.
 */
enum class RemoteTarget { PC, VPS }

sealed interface RemoteCommand {
    val target: RemoteTarget

    /** [app]: one of [RemoteBridge.APPS]' keys (the companion refuses anything else). */
    data class OpenApp(override val target: RemoteTarget, val app: String) : RemoteCommand
    data class Status(override val target: RemoteTarget) : RemoteCommand
    data class Instruction(override val target: RemoteTarget, val text: String) : RemoteCommand
    /** Multi-step autonomous task or screen action executed by MONA on PC. */
    data class AutonomousTask(
        override val target: RemoteTarget,
        val goal: String,
        val sessionId: String = "",
        val input: String? = null,
    ) : RemoteCommand
}

/** What the companion said. */
sealed interface RemoteResult {
    /** [fields]: the reply's simple values (hostname, os, load_1m, disk_free_gb …). */
    data class Done(val fields: Map<String, String> = emptyMap()) : RemoteResult
    /** When MONA PC autonomous task needs follow-up user clarification/input. */
    data class NeedsInput(val question: String, val sessionId: String) : RemoteResult
    /** No bridge address saved, or it is plain http on the Wi-Fi. */
    data object NotConfigured : RemoteResult
    /** Wrong token. */
    data object Unauthorized : RemoteResult
    /** The companion refused (action not allowed, app not in its list). */
    data object Refused : RemoteResult
    /** Could not reach it. */
    data object Unreachable : RemoteResult
}

object RemoteBridge {
    private fun word(w: String) = Regex("(?<![\\p{L}\\p{M}])(?:$w)(?![\\p{L}\\p{M}])", RegexOption.IGNORE_CASE)

    private const val PC = "pc|p\\.c\\.|computer|laptop|पीसी|कंप्यूटर|कम्प्यूटर|लैपटॉप|পিসি|কম্পিউটার|ল্যাপটপ"
    private const val VPS = "vps|server|वीपीएस|सर्वर|ভিপিএস|সার্ভার"
    private val TARGET_PC = word(PC)
    private val TARGET_VPS = word(VPS)

    /** Spoken app name → the companion's safe app key. */
    val APPS: Map<String, List<String>> = mapOf(
        "cursor" to listOf("cursor", "कर्सर", "কার্সার"),
        "notepad" to listOf("notepad", "नोटपैड", "নোটপ্যাড"),
        "calculator" to listOf("calculator", "calc", "कैलकुलेटर", "ক্যালকুলেটর"),
        "terminal" to listOf("terminal", "cmd", "command prompt", "टर्मिनल", "টার্মিনাল"),
        "explorer" to listOf("explorer", "file explorer", "files", "एक्सप्लोरर", "ফাইল এক্সপ্লোরার", "এক্সপ্লোরার"),
        "chrome" to listOf("chrome", "browser", "google chrome", "क्रोम", "ब्राउज़र", "ব্রাউজার", "ক্রোম"),
        "vscode" to listOf("vscode", "vs code", "code"),
        "spotify" to listOf("spotify", "स्पॉटिफ़ाई", "স্পটিফাই"),
        "whatsapp" to listOf("whatsapp", "व्हाट्सएप", "হোয়াটসঅ্যাপ"),
    )

    private val OPEN = Regex(
        "^(?:seema[, ]+)?(?:mere\\s+|मेरे\\s+|আমার\\s+)?(?:$PC|$VPS)\\s*(?:mein|me|mai|par|pe|में|पर|পে|তে|এ)?\\s+(.{1,40}?)\\s+" +
            "(?:kholo|khol do|open karo|open kar do|chalu karo|start karo|खोलो|खोल दो|ओपन करो|चालू करो|খোলো|খুলে দাও|চালু করো)$",
        RegexOption.IGNORE_CASE,
    )
    private val OPEN_SUFFIX = Regex("^(.{1,40}?)\\s+(?:app|ऐप|অ্যাপ)$", RegexOption.IGNORE_CASE)
    private val STATUS = Regex(
        "^(?:seema[, ]+)?(?:mere\\s+|मेरे\\s+|আমার\\s+)?(?:$PC|$VPS)\\s*(?:ka|ki|का|की|এর|ের)?\\s*" +
            "(?:status|स्टेटस|haal|हाल|স্ট্যাটাস|অবস্থা)\\s*(?:check karo|check kar do|batao|bata do|dekho|चेक करो|बताओ|देखो|চেক করো|বলো|দেখো)?" +
            "|^(?:seema[, ]+)?(?:$PC|$VPS)\\s+(?:chalu hai|chal raha hai|online hai|चालू है|चल रहा है|ऑनलाइन है|চালু আছে|চলছে)\\s*(?:kya|क्या|কি)?$",
        RegexOption.IGNORE_CASE,
    )
    private val DIRECT_ACTION = Regex(
        "^(?:seema[, ]+)?(?:$PC|$VPS)\\s*(?:par|pe|mein|me|पर|पे|में|তে|এ)?\\s+(.+?)\\s*" +
            "(?:chalao|chala do|karo|kar do|run karo|restart karo|dekho|status|चलाओ|चला दो|करो|कर दो|রান করো|চালু করো)$",
        RegexOption.IGNORE_CASE,
    )
    private val COMMAND = Regex(
        "^(?:seema[, ]+)?(?:$PC|$VPS)\\s*(?:ko|को|কে|তে|এ)?\\s+(?:command|कमांड|কমান্ড|instruction|निर्देश)\\s+(?:bhejo|bhej do|भेजो|भेज दो|পাঠাও|পাঠিয়ে দাও)\\s*[:,\\-–]?\\s*(.{1,500})$",
        RegexOption.IGNORE_CASE,
    )
    private val TASK = Regex(
        "^(?:seema[, ]+)?(?:mere\\s+|मेरे\\s+|আমার\\s+)?(?:$PC|$VPS)\\s*(?:par|pe|ko|mein|me|पर|पे|को|में|তে|এ)?\\s*(?:task|টাস্ক|কাজ|काम)\\s*(?:karo|kar do|bhejo|करो|कर दो|পাঠাও)?\\s*[:,\\-–]?\\s*(.{1,500})$",
        RegexOption.IGNORE_CASE,
    )
    private val SCREEN = Regex(
        "^(?:seema[, ]+)?(?:mere\\s+|मेरे\\s+|আমার\\s+)?(?:$PC|$VPS)\\s*(?:ki|ka|par|pe|mein|me|पर|पे|में|এর)?\\s*(?:screen|स्क्रीन|স্ক্রিন)\\s*(?:par\\s+kya\\s+(?:hai|chal\\s+raha\\s+hai)|check\\s+karo|check\\s+kar\\s+do|dekho|batao|dekhiye|चेक करो|देखो|बताओ|দেখো|কী আছে)(?:\\s+(?:hai|kya|है|क्या))?$",
        RegexOption.IGNORE_CASE,
    )
    private val AUTONOMOUS_GOAL = Regex(
        "^(?:seema[, ]+)?(?:mere\\s+|मेरे\\s+|আমার\\s+)?(?:$PC|$VPS)\\s*(?:par|pe|mein|me|se|पर|पे|में|से|তে|এ)?\\s+(.+?(?:dhoondh|find|search|khoj|ticket|whatsapp|telegram|dhundo|ढूंढ|खोज|टिकट|হোয়াটসঅ্যাপ|টেলিগ্রাম).+?)$",
        RegexOption.IGNORE_CASE,
    )

    /** The machine named first ("PC ko command bhejo: server restart karo" is for the PC). */
    private fun targetOf(text: String): RemoteTarget {
        val vps = TARGET_VPS.find(text)?.range?.first ?: Int.MAX_VALUE
        val pc = TARGET_PC.find(text)?.range?.first ?: Int.MAX_VALUE
        return if (vps < pc) RemoteTarget.VPS else RemoteTarget.PC
    }

    fun appKey(spoken: String): String? {
        val s = spoken.trim().lowercase().let { OPEN_SUFFIX.matchEntire(it)?.groupValues?.get(1) ?: it }.trim()
        return APPS.entries.firstOrNull { (_, names) -> s in names }?.key
    }

    /** The bridge command in [said], or null when it is not one. Unknown app names still parse (Seema says no). */
    fun parse(said: String): RemoteCommand? {
        val t = said.trim().trimEnd('.', '!', '?', '।', ' ').replace(Regex("\\s+"), " ")
        if (t.isEmpty() || t.length > 560) return null
        COMMAND.matchEntire(t)?.let { m ->
            val text = m.groupValues[1].trim().trim('"', '\'', '“', '”')
            return if (text.isBlank()) null else RemoteCommand.Instruction(targetOf(t), text)
        }
        TASK.matchEntire(t)?.let { m ->
            val goal = m.groupValues[1].trim().trim('"', '\'', '“', '”')
            return if (goal.isBlank()) null else RemoteCommand.AutonomousTask(targetOf(t), goal = goal)
        }
        if (SCREEN.matches(t)) return RemoteCommand.AutonomousTask(targetOf(t), goal = t)
        if (STATUS.matches(t)) return RemoteCommand.Status(targetOf(t))
        OPEN.matchEntire(t)?.let { m ->
            val spoken = m.groupValues[1].trim()
            return RemoteCommand.OpenApp(targetOf(t), appKey(spoken) ?: spoken.lowercase())
        }
        AUTONOMOUS_GOAL.matchEntire(t)?.let { m ->
            val goal = m.groupValues[1].trim()
            if (!OPEN.matches(t) && !STATUS.matches(t)) {
                return RemoteCommand.AutonomousTask(targetOf(t), goal = goal)
            }
        }
        DIRECT_ACTION.matchEntire(t)?.let { m ->
            val action = m.groupValues[1].trim()
            if (!OPEN.matches(t) && !STATUS.matches(t) && action.length in 2..120) {
                return RemoteCommand.Instruction(targetOf(t), action)
            }
        }
        return null
    }

    /** The companion's action name and params for [command]. */
    fun request(command: RemoteCommand): Pair<String, Map<String, String>> = when (command) {
        is RemoteCommand.OpenApp -> "open_app" to mapOf("name" to command.app)
        is RemoteCommand.Status -> "system_info" to mapOf("target" to command.target.name.lowercase())
        is RemoteCommand.Instruction -> "instruction" to mapOf("text" to command.text, "target" to command.target.name.lowercase())
        is RemoteCommand.AutonomousTask -> "autonomous_task" to buildMap {
            put("goal", command.goal)
            put("session_id", command.sessionId)
            command.input?.let { put("input", it) }
            put("target", command.target.name.lowercase())
        }
    }

    private fun name(t: RemoteTarget, bengali: Boolean) = when (t) {
        RemoteTarget.PC -> if (bengali) "পিসি" else "PC"
        RemoteTarget.VPS -> if (bengali) "সার্ভার" else "VPS"
    }

    /** Asked before an instruction is sent. */
    fun confirmQuestion(c: RemoteCommand.Instruction, bengali: Boolean): String =
        if (bengali) "${name(c.target, true)}-এ এই কমান্ড পাঠাব: \"${c.text}\"?" else "${name(c.target, false)} को यह कमांड भेजूँ: \"${c.text}\"?"

    /** An app the companion would refuse: said without sending anything. */
    fun unknownApp(c: RemoteCommand.OpenApp, bengali: Boolean): String? =
        if (c.app in APPS) null
        else if (bengali) "পিসিতে শুধু Cursor, VS Code, Chrome, Notepad, Calculator, Terminal, Spotify আর Explorer খুলতে পারি।"
        else "PC पर अभी सिर्फ़ Cursor, VS Code, Chrome, Notepad, Calculator, Terminal, Spotify और Explorer खोल सकती हूँ।"

    /** What Seema says after the companion answered. */
    fun spoken(c: RemoteCommand, r: RemoteResult, bengali: Boolean): String {
        val n = name(c.target, bengali)
        return when (r) {
            is RemoteResult.NeedsInput -> r.question
            RemoteResult.NotConfigured -> if (bengali) "$n ব্রিজ এখনো সেট করা নেই — Settings-এ PC / VPS Bridge URL আর Token দাও।" else "$n ब्रिज अभी सेट नहीं है — Settings में PC / VPS Bridge URL और Token डालिए।"
            RemoteResult.Unauthorized -> if (bengali) "$n টোকেন মিলছে না, Settings-এ টোকেনটা দেখে নাও।" else "$n का टोकन मेल नहीं खा रहा, Settings में टोकन देख लीजिए।"
            RemoteResult.Refused -> if (bengali) "$n এটা করতে দিল না — কম্প্যানিয়নের অনুমতির তালিকায় নেই।" else "$n ने यह करने नहीं दिया — कम्पैनियन की इजाज़त वाली सूची में नहीं है।"
            RemoteResult.Unreachable -> if (bengali) "$n-এর সাথে যোগাযোগ হচ্ছে না, ওটা চালু আছে তো?" else "$n से संपर्क नहीं हो पा रहा, वो चालू है ना?"
            is RemoteResult.Done -> when (c) {
                is RemoteCommand.AutonomousTask -> {
                    r.fields["message"] ?: if (bengali) "$n-এ কাজ সম্পন্ন হয়েছে।" else "$n पर टास्क पूरा हो गया।"
                }
                is RemoteCommand.OpenApp -> if (bengali) "$n-এ ${c.app.replaceFirstChar { it.uppercase() }} খুলে দিয়েছি।" else "$n पर ${c.app.replaceFirstChar { it.uppercase() }} खोल दिया।"
                is RemoteCommand.Instruction -> if (bengali) "কমান্ডটা $n-এ পাঠিয়ে দিয়েছি, ওখানকার টুল এটা তুলে নেবে।" else "कमांड $n पर भेज दी, वहाँ के टूल इसे उठा लेंगे।"
                is RemoteCommand.Status -> {
                    val host = r.fields["hostname"]
                    val load = r.fields["load_1m"]
                    val disk = r.fields["disk_free_gb"]?.toDoubleOrNull()?.let { Math.round(it).toString() }
                    buildString {
                        append(if (bengali) "$n চালু আছে" else "$n चालू है")
                        host?.let { append(" ($it)") }
                        if (bengali) {
                            load?.let { append(", লোড $it") }
                            disk?.let { append(", ডিস্কে $it জিবি খালি") }
                        } else {
                            load?.let { append(", लोड $it") }
                            disk?.let { append(", डिस्क में $it GB खाली") }
                        }
                        append("।")
                    }
                }
            }
        }
    }
}
