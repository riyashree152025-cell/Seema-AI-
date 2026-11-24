package com.raju.aiassistant.domain.assistant

/**
 * How Seema talks through a task that takes a moment, like a person on a call:
 *  1. before the task: what she is starting and what she will find out ("PNR देख रही हूँ… ट्रेन, कोच
 *     और सीट का हाल निकाल रही हूँ, बस दो सेकंड में बताती हूँ");
 *  2. the result itself (each task already says it in full);
 *  3. after a result that went well: one caring question ("किसी को WhatsApp पर भेज दूँ?").
 * Only for tasks that read something and take more than a second; tasks that change something ask
 * for confirmation first and say their own result. The name in a line ("राजू, …") is turned into a
 * pet name now and then, or left out, by the engine's phrase().
 */
object TwoStageNarration {
    data class Line(val hindi: String, val bengali: String)

    /** Stage 1: said before the task runs, or null for tasks that need no plan. */
    fun plan(action: AssistantAction): Line? {
        return when (action) {
            is AssistantAction.PnrStatus -> Line("राजू, PNR देखना शुरू कर दिया है… ट्रेन, कोच और अभी की सीट का हाल निकाल रही हूँ, बस दो सेकंड में बताती हूँ।", "রাজু, PNR দেখা শুরু করেছি… ট্রেন, কোচ আর এখনকার সিটের খবর বের করছি, দু সেকেন্ডে বলছি।")
            is AssistantAction.TrainLive -> Line("राजू, ट्रेन अभी कहाँ पहुँची है, देख रही हूँ… कितनी देर चल रही है वो भी बताती हूँ।", "রাজু, ট্রেন এখন কোথায় দেখছি… কত দেরিতে চলছে সেটাও বলছি।")
            is AssistantAction.TrainsBetween -> Line("राजू, ${action.from} से ${action.to} की ट्रेनें निकाल रही हूँ… समय के साथ बताती हूँ।", "রাজু, ${action.from} থেকে ${action.to} ট্রেন বের করছি… সময় সহ বলছি।")
            is AssistantAction.TrainFare -> Line("राजू, ${action.travelClass} का किराया देख रही हूँ… बस एक पल।", "রাজু, ${action.travelClass} ভাড়া দেখছি… এক মুহূর্ত।")
            is AssistantAction.LookCamera -> Line("राजू, कैमरे से ध्यान से देख रही हूँ… एक पल, फिर बताती हूँ क्या दिखा।", "রাজু, ক্যামেরায় ভালো করে দেখছি… এক মুহূর্ত, তারপর বলছি কী দেখলাম।")
            is AssistantAction.WhatsAppPeek -> Line("राजू, WhatsApp में ${action.contact} की चैट खोलकर देख रही हूँ… क्या क्या आया है, बस एक पल में बताती हूँ।", "রাজু, হোয়াটসঅ্যাপে ${action.contact}-এর চ্যাট খুলে দেখছি… কী কী এসেছে, এক মুহূর্তে বলছি।")
            is AssistantAction.ReadTicket -> Line("राजू, टिकट चेक कर रही हूँ…", "রাজু, টিকিট দেখছি…")
            else -> null
        }
    }

    /** Stage 3: a caring question after a result that went well, or null. */
    fun checkIn(action: AssistantAction): Line? {
        return when (action) {
            is AssistantAction.PnrStatus, is AssistantAction.ReadTicket ->
                Line("किसी को WhatsApp पर भेज दूँ?", "কাউকে হোয়াটসঅ্যাপে পাঠাব?")
            is AssistantAction.LookCamera ->
                Line("एक बार देखकर बताओ, सही समझा ना?", "একবার দেখে বলো, ঠিক বুঝেছি তো?")
            is AssistantAction.TrainsBetween ->
                Line("किसी ट्रेन की सीट या किराया भी देखूँ?", "কোনো ট্রেনের সিট বা ভাড়াও দেখব?")
            else -> null
        }
    }
}
