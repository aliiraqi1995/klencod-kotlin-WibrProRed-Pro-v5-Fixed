package com.alialanbaki.wibrprored

data class WifiTarget(
    val ssid: String,
    val bssid: String,
    val capabilities: String,
    val signalPercent: Int
) {
    fun displayLabel(): String {
        val sec = if (capabilities.isBlank()) "OPEN" else capabilities
        return "$ssid  |  إشارة: $signalPercent%  |  $sec"
    }
}

object AppSession {
    var selectedTarget: WifiTarget? = null
    var delaySeconds: Int = 7
    var activeWordlistName: String = "القاموس الافتراضي الشائع"
    var activePasswords: List<String> = WordlistPresets.commonPasswords()
}

object WordlistPresets {
    fun presetNames(): List<String> = listOf(
        "القاموس الافتراضي الشائع",
        "أرقام ورموز شائعة (8+ خانات)",
        "كلمات مرور محلية وشائعة"
    )

    fun passwordsForPresetIndex(index: Int): List<String> = when (index) {
        1 -> numericCommon()
        2 -> localCommon()
        else -> commonPasswords()
    }

    fun commonPasswords(): List<String> = listOf(
        "12345678", "123456789", "1234567890", "87654321",
        "password", "password123", "admin123", "qwerty123",
        "11223344", "00000000", "11111111", "abc12345",
        "wifi1234", "internet123", "pass1234", "12341234"
    )

    fun numericCommon(): List<String> = listOf(
        "10203040", "1122334455", "123123123", "20202020",
        "20242024", "20252025", "20262026", "987654321",
        "55667788", "66778899", "123456789a", "00001111"
    )

    fun localCommon(): List<String> = listOf(
        "iraq1234", "baghdad123", "diyala123", "basra123",
        "erbil123", "mosul123", "ali123456", "ahmed1234",
        "omar12345", "mustafa123", "zain12345", "hussain123"
    )
}
