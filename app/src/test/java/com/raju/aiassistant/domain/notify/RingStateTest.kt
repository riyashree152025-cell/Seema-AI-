package com.raju.aiassistant.domain.notify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RingStateTest {
    @Test
    fun anAnsweredSimCallIsNotRingingWhateverTheOtherSignsSay() {
        // 20 Nov: the call was up (timer 00:47) but the audio mode and the old notification still said "ringing".
        assertFalse(RingState.ringing(SimCallState.OFFHOOK, audioRinging = true, notificationRinging = true))
        assertTrue(RingState.inCall(SimCallState.OFFHOOK))
    }

    @Test
    fun aRingingSimCallIsRinging() {
        assertTrue(RingState.ringing(SimCallState.RINGING, audioRinging = false, notificationRinging = false))
        assertFalse(RingState.inCall(SimCallState.RINGING))
    }

    @Test
    fun internetCallsAndNoPermissionFallBackToTheRingtoneOrTheNotification() {
        assertTrue(RingState.ringing(SimCallState.IDLE, audioRinging = false, notificationRinging = true)) // WhatsApp call
        assertTrue(RingState.ringing(SimCallState.UNKNOWN, audioRinging = true, notificationRinging = false))
        assertFalse(RingState.ringing(SimCallState.IDLE, audioRinging = false, notificationRinging = false))
        assertFalse(RingState.inCall(SimCallState.UNKNOWN))
    }
}
