package com.raju.aiassistant.domain.automation

class CarModeTest {
    @org.junit.Test
    fun carStereosByName() {
        listOf("My Car", "MARUTI SUZUKI", "Hyundai Creta", "Android Auto", "SYNC", "Nexon EV").forEach { org.junit.Assert.assertTrue(it, CarMode.isCar(it)) }
        listOf("boAt Rockerz 255", "JBL Tune 510BT", "Galaxy Buds2", "Scarlet", null, "").forEach { org.junit.Assert.assertFalse("$it", CarMode.isCar(it)) }
    }
}
