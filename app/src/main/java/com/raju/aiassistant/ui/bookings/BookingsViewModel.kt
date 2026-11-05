package com.raju.aiassistant.ui.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raju.aiassistant.di.AppContainer
import com.raju.aiassistant.domain.booking.BOOKING_FOLDER
import com.raju.aiassistant.domain.booking.Booking
import com.raju.aiassistant.domain.booking.BookingStatus
import com.raju.aiassistant.domain.booking.daySlipFileName
import com.raju.aiassistant.domain.booking.formatSlip
import com.raju.aiassistant.domain.tools.ToolOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class BookingsViewModel(private val c: AppContainer) : ViewModel() {
    val bookings: StateFlow<List<Booking>> = c.bookingRepository.bookings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val whatsAppNumber: StateFlow<String> = c.settingsRepository.settings.map { it.bookingWhatsApp }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setStatus(b: Booking, status: BookingStatus) = viewModelScope.launch {
        runCatching { c.bookingRepository.upsert(b.copy(status = status)) }
            .onSuccess { _message.value = "बुकिंग #${b.id}: ${status.label}" }
            .onFailure { _message.value = "स्टेटस नहीं बदल पाया।" }
    }

    fun delete(b: Booking) = viewModelScope.launch {
        _message.value = if (runCatching { c.bookingRepository.delete(b.id) }.getOrDefault(false)) "बुकिंग #${b.id} हटा दी।" else "हटाई नहीं जा सकी।"
    }

    fun saveNumber(number: String) = viewModelScope.launch {
        c.settingsRepository.setBookingWhatsApp(number)
        _message.value = if (number.isBlank()) "WhatsApp नंबर हटा दिया — भेजते समय चैट आप चुनेंगे।" else "पर्ची इस नंबर पर जाएगी (हर बार पूछकर): ${number.trim()}"
    }

    /** Called after the user confirmed in the dialog. */
    fun sendWhatsApp(b: Booking) {
        val outcome = c.deviceTools.openWhatsApp(whatsAppNumber.value.takeIf { it.isNotBlank() }, formatSlip(b))
        _message.value = when (outcome) {
            is ToolOutcome.Done -> outcome.message
            is ToolOutcome.Failed -> outcome.message
            is ToolOutcome.NeedsPermission -> outcome.reason
        }
    }

    fun saveFile(date: LocalDate) = viewModelScope.launch {
        _message.value = runCatching { c.bookingRepository.writeDayFile(date) }
            .fold({ "फ़ाइल सेव हो गई: $BOOKING_FOLDER/${daySlipFileName(date)}" }, { "फ़ाइल सेव नहीं हो पाई।" })
    }
}
