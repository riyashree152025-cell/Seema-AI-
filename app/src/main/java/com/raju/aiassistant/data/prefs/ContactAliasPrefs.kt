package com.raju.aiassistant.data.prefs

import android.content.Context
import com.raju.aiassistant.domain.tools.Contact
import com.raju.aiassistant.domain.tools.ContactAliasStore
import com.raju.aiassistant.domain.tools.ContactAliases

/** How the user says names → the contact they chose; app-private preferences, never sent anywhere. */
class ContactAliasPrefs(context: Context) : ContactAliasStore {
    private val prefs = context.getSharedPreferences("contact_aliases", Context.MODE_PRIVATE)

    override fun get(spoken: String): Contact? {
        val v = prefs.getString(ContactAliases.key(spoken), null) ?: return null
        val parts = v.split('\t')
        return if (parts.size == 2 && parts[1].isNotBlank()) Contact(parts[0], parts[1]) else null
    }

    override fun put(spoken: String, contact: Contact) {
        if (prefs.all.size >= MAX && !prefs.contains(ContactAliases.key(spoken))) return
        prefs.edit().putString(ContactAliases.key(spoken), "${contact.name.replace('\t', ' ')}\t${contact.number}").apply()
    }

    private companion object { const val MAX = 500 }
}
