package com.raju.aiassistant.domain.tools

/**
 * How the user says a name, learned from their own choice: "साजिद शेख एन जेडम" → the contact they confirmed
 * ("Sajid Shaikh NZM", its number). Next time that spoken name finds that contact first (it is still confirmed
 * before a call or a message). Kept on the phone only; matched by sound, so the recogniser's spelling of the
 * same words does not matter.
 */
interface ContactAliasStore {
    fun get(spoken: String): Contact?
    fun put(spoken: String, contact: Contact)
}

class InMemoryContactAliases : ContactAliasStore {
    private val map = HashMap<String, Contact>()
    override fun get(spoken: String): Contact? = synchronized(map) { map[ContactAliases.key(spoken)] }
    override fun put(spoken: String, contact: Contact) { synchronized(map) { map[ContactAliases.key(spoken)] = contact } }
}

object ContactAliases {
    /** The spoken name as a sound, honorifics left out ("Jalal bhai" = "जलाल भाई" = "jalal"). */
    fun key(spoken: String): String {
        val core = ContactMatch.stripHonorifics(spoken).ifBlank { spoken }
        return Romanize.phonetic(core).ifBlank { core.trim().lowercase() }
    }

    /** Worth remembering: the spoken name is not simply the saved name (that is found anyway). */
    fun worthRemembering(spoken: String, contact: Contact): Boolean =
        spoken.isNotBlank() && key(spoken).isNotBlank() && key(spoken) != key(contact.name)
}
