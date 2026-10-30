package com.raju.aiassistant.data.repository

import com.raju.aiassistant.data.db.ChatDao
import com.raju.aiassistant.data.db.ConversationEntity
import com.raju.aiassistant.data.db.MessageEntity
import com.raju.aiassistant.domain.assistant.ChatHistory
import com.raju.aiassistant.domain.model.ChatMessage
import com.raju.aiassistant.domain.model.ChatRole
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ConversationRepository(
    private val dao: ChatDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : ChatHistory {
    private val lock = Mutex()
    private var conversationId: Long? = null

    private suspend fun currentConversationId(): Long = lock.withLock {
        conversationId ?: (dao.latestConversation()?.id ?: dao.insertConversation(
            ConversationEntity(title = "Chat", createdAt = clock(), updatedAt = clock())
        )).also { conversationId = it }
    }

    /** Messages of the current conversation (the latest one), oldest first. */
    override suspend fun recent(limit: Int): List<ChatMessage> {
        val id = lock.withLock { conversationId ?: dao.latestConversation()?.id } ?: return emptyList()
        return dao.recentMessagesIn(id, limit).map { it.toDomain() }
    }

    /** "New conversation": later messages go to a fresh conversation; older chats stay saved and searchable. */
    suspend fun startNew() {
        lock.withLock {
            conversationId = dao.insertConversation(ConversationEntity(title = "Chat", createdAt = clock(), updatedAt = clock()))
        }
    }

    /** Search all saved chats; `%` and `_` in the query are matched literally. */
    suspend fun search(query: String, limit: Int = 50): List<ChatMessage> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return dao.searchMessages(escaped, limit).map { it.toDomain() }
    }

    override suspend fun add(role: ChatRole, text: String): Long {
        val id = currentConversationId()
        val now = clock()
        val rowId = dao.insertMessage(MessageEntity(conversationId = id, role = role.name, text = text, createdAt = now))
        dao.touchConversation(id, now)
        return rowId
    }

    override suspend fun clear() {
        lock.withLock {
            dao.deleteAllConversations()
            conversationId = null
        }
    }

    private fun MessageEntity.toDomain() = ChatMessage(
        id = id,
        conversationId = conversationId,
        role = ChatRole.entries.firstOrNull { it.name == role } ?: ChatRole.SYSTEM,
        text = text,
        createdAt = createdAt,
    )
}
