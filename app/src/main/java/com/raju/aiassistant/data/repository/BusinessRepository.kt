package com.raju.aiassistant.data.repository

import com.raju.aiassistant.data.db.BusinessDao
import com.raju.aiassistant.data.db.ClientEntity
import com.raju.aiassistant.data.db.ExpenseEntity
import com.raju.aiassistant.data.db.JourneyEntity
import com.raju.aiassistant.data.db.LedgerEntryEntity
import com.raju.aiassistant.domain.business.BusinessStore
import com.raju.aiassistant.domain.business.Expense
import com.raju.aiassistant.domain.business.Journey
import com.raju.aiassistant.domain.ledger.LedgerKind
import com.raju.aiassistant.domain.ledger.LedgerLine
import com.raju.aiassistant.domain.ledger.balanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class BusinessRepository(private val dao: BusinessDao, private val clock: () -> Long = System::currentTimeMillis) : BusinessStore {
    val recentExpenses: Flow<List<Expense>> = dao.observeRecentExpenses(50).map { rows -> rows.map { it.toDomain() } }

    fun upcomingJourneys(today: LocalDate): Flow<List<Journey>> =
        dao.observeJourneysFrom(today.toString()).map { rows -> rows.mapNotNull { it.toDomain() } }

    override suspend fun addExpense(amountPaise: Long, note: String, category: String): Long =
        dao.insertExpense(ExpenseEntity(amountPaise = amountPaise, note = note.trim().take(80), category = category, createdAt = clock()))

    override suspend fun deleteLastExpense(): Expense? {
        val last = dao.lastExpense() ?: return null
        return if (dao.deleteExpense(last.id) > 0) last.toDomain() else null
    }

    suspend fun deleteExpense(id: Long): Boolean = dao.deleteExpense(id) > 0

    override suspend fun expensesBetween(fromMillis: Long, toMillis: Long): List<Expense> =
        dao.expensesBetween(fromMillis, toMillis).map { it.toDomain() }

    override suspend fun saveJourney(journey: Journey): Long {
        val date = journey.date.toString()
        val existing = dao.journeyByPnr(journey.pnr)
        if (existing != null) {
            // Keep details the user gave before when this update doesn't repeat them.
            dao.updateJourney(
                journey.pnr, journey.train ?: existing.train, date, journey.route ?: existing.route,
                journey.passenger ?: existing.passenger, journey.phone ?: existing.phone, journey.note ?: existing.note,
            )
            return existing.id
        }
        return dao.insertJourney(
            JourneyEntity(
                pnr = journey.pnr, train = journey.train, journeyDate = date, route = journey.route,
                passenger = journey.passenger, phone = journey.phone, note = journey.note, createdAt = clock(),
            ),
        )
    }

    override suspend fun journeysFrom(date: LocalDate): List<Journey> = dao.journeysFrom(date.toString()).mapNotNull { it.toDomain() }

    override suspend fun removeJourney(pnr: String): Boolean = dao.deleteJourney(pnr) > 0

    /** Journeys more than a week in the past are no longer useful. */
    suspend fun pruneOldJourneys(today: LocalDate) = dao.deleteJourneysBefore(today.minusDays(7).toString())

    /** Clients are found by their name ignoring case, spaces and punctuation; a new name makes a new client. */
    override suspend fun recordLedger(name: String, amountPaise: Long, kind: LedgerKind): Long? {
        val key = clientKey(name).ifEmpty { return null }
        val id = dao.clientByName(key)?.id
            ?: dao.insertClient(ClientEntity(name = name.trim().take(60), normalizedName = key, phone = null, createdAt = clock()))
        dao.insertLedgerEntry(LedgerEntryEntity(clientId = id, amountPaise = amountPaise, type = kind.name, note = "", createdAt = clock()))
        return balanceOfClient(id)
    }

    override suspend fun ledgerBalance(name: String): Long? {
        val key = clientKey(name).ifEmpty { return null }
        val id = dao.clientByName(key)?.id ?: return null
        return balanceOfClient(id)
    }

    private suspend fun balanceOfClient(clientId: Long): Long =
        balanceOf(dao.ledgerFor(clientId).map { LedgerLine(LedgerKind.valueOf(it.type), it.amountPaise) })

    override suspend fun lastLedgerEntry(): com.raju.aiassistant.domain.ledger.LedgerEntryInfo? {
        val e = dao.lastLedgerEntry() ?: return null
        val name = dao.clientById(e.clientId)?.name ?: return null
        return com.raju.aiassistant.domain.ledger.LedgerEntryInfo(e.id, name, e.amountPaise, LedgerKind.valueOf(e.type), e.createdAt)
    }

    override suspend fun deleteLedgerEntry(id: Long): Boolean = dao.deleteLedgerEntry(id) > 0

    /** Every client with what they owe now, and the newest entry (for the screen and its undo button). */
    val ledgerView: Flow<Pair<List<com.raju.aiassistant.domain.ledger.ClientBalance>, com.raju.aiassistant.domain.ledger.LedgerEntryInfo?>> =
        kotlinx.coroutines.flow.combine(dao.observeClients(), dao.observeLedgerEntries()) { clients, entries ->
            val byClient = entries.groupBy { it.clientId }
            val balances = clients.mapNotNull { c ->
                val list = byClient[c.id].orEmpty()
                if (list.isEmpty()) null
                else com.raju.aiassistant.domain.ledger.ClientBalance(c.name, balanceOf(list.map { LedgerLine(LedgerKind.valueOf(it.type), it.amountPaise) }), list.size)
            }
            val newest = entries.firstOrNull()?.let { e ->
                clients.firstOrNull { it.id == e.clientId }?.let { c ->
                    com.raju.aiassistant.domain.ledger.LedgerEntryInfo(e.id, c.name, e.amountPaise, LedgerKind.valueOf(e.type), e.createdAt)
                }
            }
            balances to newest
        }

    /**
     * The same person, however the name was said: case, spaces and punctuation ignored, and "Bhai", "ji",
     * "Sahab" … left out, so "Jalal Bhai" and "Jalal" share one account.
     */
    private fun clientKey(name: String): String {
        val core = com.raju.aiassistant.domain.tools.ContactMatch.stripHonorifics(name).ifBlank { name }
        return core.lowercase().filter { it.isLetterOrDigit() }
    }
}

private fun ExpenseEntity.toDomain() = Expense(id, amountPaise, note, category, createdAt)

private fun JourneyEntity.toDomain(): Journey? = runCatching {
    Journey(id, pnr, train, LocalDate.parse(journeyDate), route, passenger, phone, note)
}.getOrNull()
