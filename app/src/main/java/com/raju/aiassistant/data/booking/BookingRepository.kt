package com.raju.aiassistant.data.booking

import com.raju.aiassistant.domain.booking.Booking
import com.raju.aiassistant.domain.booking.BookingStatus
import com.raju.aiassistant.domain.booking.BookingStore
import com.raju.aiassistant.domain.booking.DaySlipWriter
import com.raju.aiassistant.domain.booking.Passenger
import com.raju.aiassistant.domain.booking.daySlipFileName
import com.raju.aiassistant.domain.booking.daySlipFileText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * Bookings in one small JSON file in the app's private storage (a few hundred entries at most,
 * so no database table or migration is needed). After every change the text file with that
 * journey date's slips is written again through [slips].
 */
class BookingRepository(
    private val file: File,
    private val slips: DaySlipWriter? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) : BookingStore {
    private val mutex = Mutex()
    private val state = MutableStateFlow<List<Booking>?>(null)

    /** All bookings, updated after every change; the file is read on first collection. */
    val bookings: Flow<List<Booking>> = flow {
        all()
        emitAll(state.filterNotNull())
    }

    override suspend fun all(): List<Booking> = mutex.withLock { loaded() }

    override suspend fun upsert(booking: Booking): Booking = mutex.withLock {
        val list = loaded()
        val now = clock()
        val old = list.firstOrNull { it.id == booking.id && booking.id != 0L }
        val stored = if (old == null) {
            booking.copy(id = (list.maxOfOrNull { it.id } ?: 0L) + 1, createdAt = now, updatedAt = now)
        } else {
            booking.copy(createdAt = old.createdAt, updatedAt = now)
        }
        val next = if (old == null) list + stored else list.map { if (it.id == stored.id) stored else it }
        persist(next)
        rewriteDays(next, setOfNotNull(stored.journeyDate, old?.journeyDate))
        stored
    }

    override suspend fun delete(id: Long): Boolean = mutex.withLock {
        val list = loaded()
        val gone = list.firstOrNull { it.id == id } ?: return@withLock false
        val next = list.filterNot { it.id == id }
        persist(next)
        rewriteDays(next, setOf(gone.journeyDate))
        true
    }

    /** Writes today's file again (used by the Bookings screen's "Save file" button). */
    suspend fun writeDayFile(date: LocalDate) = mutex.withLock { rewriteDays(loaded(), setOf(date)) }

    private suspend fun loaded(): List<Booking> {
        state.value?.let { return it }
        val list = withContext(Dispatchers.IO) {
            runCatching { if (file.isFile) decode(file.readText()) else emptyList() }
                .onFailure { log("bookings: could not read (${it.javaClass.simpleName})") }
                .getOrDefault(emptyList())
        }
        state.value = list
        return list
    }

    private suspend fun persist(list: List<Booking>) {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(encode(list))
            if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
        }
        state.value = list
    }

    private suspend fun rewriteDays(list: List<Booking>, dates: Set<LocalDate>) {
        val writer = slips ?: return
        val stamp = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.ENGLISH).format(Date(clock()))
        for (d in dates) {
            runCatching { writer.write(d, daySlipFileName(d), daySlipFileText(d, list, stamp)) }
                .onFailure { log("bookings: slip file failed (${it.javaClass.simpleName})") }
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun encode(list: List<Booking>): String = buildJsonArray {
            list.forEach { b ->
                add(
                    buildJsonObject {
                        put("id", b.id)
                        put("date", b.journeyDate.toString())
                        put("from", b.from)
                        put("to", b.to)
                        b.trainNo?.let { put("train", it) }
                        b.travelClass?.let { put("class", it) }
                        b.sender?.let { put("sender", it) }
                        put("status", b.status.name)
                        put("createdAt", b.createdAt)
                        put("updatedAt", b.updatedAt)
                        put(
                            "passengers",
                            buildJsonArray {
                                b.passengers.forEach { p ->
                                    add(
                                        buildJsonObject {
                                            put("name", p.name)
                                            p.gender?.let { put("gender", it.toString()) }
                                            p.age?.let { put("age", it) }
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            }
        }.toString()

        /** Broken entries are skipped, never the whole file. */
        fun decode(text: String): List<Booking> {
            val arr = json.parseToJsonElement(text) as? JsonArray ?: return emptyList()
            return arr.mapNotNull { el ->
                runCatching {
                    val o = el as JsonObject
                    fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                    fun l(k: String) = (o[k] as? JsonPrimitive)?.longOrNull ?: 0L
                    Booking(
                        id = l("id"),
                        journeyDate = LocalDate.parse(s("date")!!),
                        from = s("from")!!,
                        to = s("to")!!,
                        trainNo = s("train"),
                        travelClass = s("class"),
                        sender = s("sender"),
                        status = BookingStatus.entries.firstOrNull { it.name == s("status") } ?: BookingStatus.PENDING,
                        createdAt = l("createdAt"),
                        updatedAt = l("updatedAt"),
                        passengers = (o["passengers"] as JsonArray).map { pe ->
                            val p = pe as JsonObject
                            Passenger(
                                name = p["name"]!!.jsonPrimitive.content,
                                gender = (p["gender"] as? JsonPrimitive)?.contentOrNull?.firstOrNull(),
                                age = (p["age"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
                            )
                        },
                    )
                }.getOrNull()
            }.filter { it.id > 0 && it.passengers.isNotEmpty() }
        }
    }
}
