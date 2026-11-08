package com.raju.aiassistant.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("conversationId")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    /** Stored as [com.raju.aiassistant.domain.model.ChatRole.name]. */
    val role: String,
    val text: String,
    val createdAt: Long,
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long,
)

@Entity(tableName = "action_log")
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val summary: String,
    /** [com.raju.aiassistant.domain.assistant.ActionStatus.name] */
    val status: String,
    val createdAt: Long,
)

/** Khata: one expense; amount in paise. */
@Entity(tableName = "expenses", indices = [Index("createdAt")])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val note: String,
    val category: String,
    val createdAt: Long,
)

/** PNR diary: one saved train journey; [journeyDate] is ISO yyyy-MM-dd. */
@Entity(tableName = "journeys", indices = [Index(value = ["pnr"], unique = true)])
data class JourneyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pnr: String,
    val train: String?,
    val journeyDate: String,
    val route: String?,
    val passenger: String?,
    val phone: String?,
    val note: String?,
    val createdAt: Long,
)

/** Knowledge Base: one imported document; [text] is the extracted text (kept so the index can be rebuilt). */
@Entity(tableName = "kb_documents")
data class KbDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mimeType: String,
    val text: String,
    val chunkCount: Int,
    val addedAt: Long,
)

/** Knowledge Base: one searchable passage of a document. */
@Entity(tableName = "kb_chunks", indices = [Index("documentId")])
data class KbChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val chunkIndex: Int,
    val text: String,
)

/** Client memory: someone the user does udhaar or payments with. [normalizedName] finds them again. */
@Entity(tableName = "clients", indices = [Index("normalizedName")])
data class ClientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val phone: String?,
    val createdAt: Long,
)

/** One udhaar-book entry: [type] is GAVE (I gave) or GOT (I got back); amount in paise. */
@Entity(tableName = "ledger_entries", indices = [Index("clientId")])
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val amountPaise: Long,
    val type: String,
    val note: String,
    val createdAt: Long,
)
