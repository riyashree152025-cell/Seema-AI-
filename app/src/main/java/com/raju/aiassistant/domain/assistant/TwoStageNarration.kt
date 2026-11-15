package com.raju.aiassistant.domain.assistant

import com.raju.aiassistant.domain.model.Personality

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
    fun plan(action: AssistantAction, personality: Personality): Line? {
        val tapori = personality == Personality.TAPORI
        val pro = personality == Personality.PRO
        return when (action) {
            is AssistantAction.PnrStatus -> when {
                pro -> Line("PNR देख रही हूँ।", "PNR দেখছি।")
                tapori -> Line("बोले तो, PNR का हाल निकाल रही हूँ भिड़ू… ट्रेन, कोच, सीट सब, दो सेकंड रुक।", "PNR-এর খবর বের করছি দোস্ত… ট্রেন, কোচ, সিট সব, দু সেকেন্ড দাঁড়াও।")
                else -> Line("राजू, PNR देखना शुरू कर दिया है… ट्रेन, कोच और अभी की सीट का हाल निकाल रही हूँ, बस दो सेकंड में बताती हूँ।", "রাজু, PNR দেখা শুরু করেছি… ট্রেন, কোচ আর এখনকার সিটের খবর বের করছি, দু সেকেন্ডে বলছি।")
            }
            is AssistantAction.TrainLive -> when {
                pro -> Line("ट्रेन की लोकेशन देख रही हूँ।", "ট্রেনের অবস্থান দেখছি।")
                tapori -> Line("ट्रेन कहाँ पहुँची, अभी पता करती हूँ भिड़ू…", "ট্রেন কোথায় পৌঁছল, এখনই দেখছি দোস্ত…")
                else -> Line("राजू, ट्रेन अभी कहाँ पहुँची है, देख रही हूँ… कितनी देर चल रही है वो भी बताती हूँ।", "রাজু, ট্রেন এখন কোথায় দেখছি… কত দেরিতে চলছে সেটাও বলছি।")
            }
            is AssistantAction.TrainsBetween -> when {
                pro -> Line("ट्रेनें देख रही हूँ।", "ট্রেন দেখছি।")
                tapori -> Line("कौन कौन सी ट्रेन है, लिस्ट निकाल रही हूँ भिड़ू…", "কোন কোন ট্রেন আছে, লিস্ট বের করছি দোস্ত…")
                else -> Line("राजू, ${action.from} से ${action.to} की ट्रेनें निकाल रही हूँ… समय के साथ बताती हूँ।", "রাজু, ${action.from} থেকে ${action.to} ট্রেন বের করছি… সময় সহ বলছি।")
            }
            is AssistantAction.TrainFare -> when {
                pro -> Line("किराया देख रही हूँ।", "ভাড়া দেখছি।")
                else -> Line("राजू, ${action.travelClass} का किराया देख रही हूँ… बस एक पल।", "রাজু, ${action.travelClass} ভাড়া দেখছি… এক মুহূর্ত।")
            }
            is AssistantAction.LookCamera -> when {
                pro -> Line("कैमरे से देख रही हूँ।", "ক্যামেরায় দেখছি।")
                tapori -> Line("रुक भिड़ू, कैमरे से ध्यान से देखती हूँ…", "দাঁড়াও দোস্ত, ক্যামেরায় ভালো করে দেখি…")
                else -> Line("राजू, कैमरे से ध्यान से देख रही हूँ… एक पल, फिर बताती हूँ क्या दिखा।", "রাজু, ক্যামেরায় ভালো করে দেখছি… এক মুহূর্ত, তারপর বলছি কী দেখলাম।")
            }
            is AssistantAction.WhatsAppPeek -> when {
                pro -> Line("चैट देख रही हूँ।", "চ্যাট দেখছি।")
                else -> Line("राजू, WhatsApp में ${action.contact} की चैट खोलकर देख रही हूँ… क्या क्या आया है, बस एक पल में बताती हूँ।", "রাজু, হোয়াটসঅ্যাপে ${action.contact}-এর চ্যাট খুলে দেখছি… কী কী এসেছে, এক মুহূর্তে বলছি।")
            }
            is AssistantAction.ReadTicket -> when {
                pro -> Line("टिकट पढ़ रही हूँ।", "টিকিট পড়ছি।")
                else -> Line("राजू, अभी वाला टिकट खोल रही हूँ… PNR, ट्रेन, तारीख और यात्रियों के नाम पढ़कर बताती हूँ।", "রাজু, এখনকার টিকিট খুলছি… PNR, ট্রেন, তারিখ আর যাত্রীদের নাম পড়ে বলছি।")
            }
            else -> null
        }
    }

    /** Stage 3: a caring question after a result that went well, or null. Pro asks nothing extra. */
    fun checkIn(action: AssistantAction, personality: Personality): Line? {
        if (personality == Personality.PRO) return null
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
