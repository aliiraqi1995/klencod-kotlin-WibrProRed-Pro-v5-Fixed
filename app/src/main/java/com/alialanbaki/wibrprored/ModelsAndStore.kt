package com.alialanbaki.wibrprored

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ScannedNetwork(
    val ssid: String,
    val bssid: String,
    val capabilities: String,
    val signalLevel: Int
)

data class QueuedTarget(
    val ssid: String,
    val bssid: String,
    val capabilities: String,
    val signalLevel: Int,
    val dictionaryNames: List<String>,
    val passwords: List<String>,
    val delaySeconds: Int,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0
)

data class DictionaryEntry(
    val name: String,
    val passwords: List<String>,
    val isCustom: Boolean = false
)

object AppState {
    var pendingNetwork: ScannedNetwork? = null
    var activeQueuedIndex: Int = -1
    var delaySeconds: Int = 7
}

object DictionaryStore {
    private const val PREFS_NAME = "wibr_pro_red_dicts"
    private const val KEY_CUSTOM_DICTS = "custom_dicts_json"
    private const val KEY_DELETED_BUILTINS = "deleted_builtins_json"

    private val builtInDictionaries = listOf(
        DictionaryEntry(
            name = "Small Dictionary",
            passwords = listOf(
                "12345678", "123456789", "password", "password123",
                "admin123", "qwerty123", "11223344", "00000000"
            ),
            isCustom = false
        ),
        DictionaryEntry(
            name = "Middle Dictionary",
            passwords = listOf(
                "12345678", "123456789", "1234567890", "87654321",
                "password", "password123", "admin123", "qwerty123",
                "11223344", "00000000", "11111111", "abc12345",
                "wifi1234", "internet123", "pass1234", "12341234",
                "iraq1234", "baghdad123", "20242024", "20252025"
            ),
            isCustom = false
        ),
        DictionaryEntry(
            name = "Big Dictionary",
            passwords = listOf(
                "12345678", "123456789", "1234567890", "87654321",
                "password", "password123", "admin123", "qwerty123",
                "11223344", "00000000", "11111111", "abc12345",
                "wifi1234", "internet123", "pass1234", "12341234",
                "iraq1234", "baghdad123", "diyala123", "basra123",
                "20202020", "20242024", "20252025", "20262026",
                "10203040", "1122334455", "55667788", "66778899",
                "ali123456", "ahmed1234", "omar12345", "mustafa123"
            ),
            isCustom = false
        )
    )

    fun loadAll(context: Context): MutableList<DictionaryEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deletedSet = mutableSetOf<String>()
        val deletedRaw = prefs.getString(KEY_DELETED_BUILTINS, "[]") ?: "[]"
        try {
            val arr = JSONArray(deletedRaw)
            for (i in 0 until arr.length()) {
                deletedSet.add(arr.optString(i))
            }
        } catch (_: Exception) {
        }

        val list = mutableListOf<DictionaryEntry>()
        for (item in builtInDictionaries) {
            if (!deletedSet.contains(item.name)) {
                list.add(item)
            }
        }

        val customRaw = prefs.getString(KEY_CUSTOM_DICTS, "[]") ?: "[]"
        try {
            val arr = JSONArray(customRaw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name")
                val pwArr = obj.optJSONArray("passwords") ?: JSONArray()
                val pwList = mutableListOf<String>()
                for (j in 0 until pwArr.length()) {
                    val p = pwArr.optString(j)
                    if (p.isNotBlank()) pwList.add(p)
                }
                if (name.isNotBlank() && pwList.isNotEmpty()) {
                    list.add(DictionaryEntry(name = name, passwords = pwList, isCustom = true))
                }
            }
        } catch (_: Exception) {
        }

        return list
    }

    fun addOrReplaceCustomDictionary(context: Context, name: String, passwords: List<String>) {
        val currentCustoms = loadCustomOnly(context).filterNot { it.name == name }.toMutableList()
        currentCustoms.add(DictionaryEntry(name = name, passwords = passwords, isCustom = true))
        saveCustomOnly(context, currentCustoms)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deletedSet = loadDeletedBuiltins(prefs).apply { remove(name) }
        saveDeletedBuiltins(prefs, deletedSet)
    }

    fun deleteDictionary(context: Context, entry: DictionaryEntry) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (entry.isCustom) {
            val updated = loadCustomOnly(context).filterNot { it.name == entry.name }
            saveCustomOnly(context, updated)
        } else {
            val deletedSet = loadDeletedBuiltins(prefs).apply { add(entry.name) }
            saveDeletedBuiltins(prefs, deletedSet)
        }
    }

    private fun loadCustomOnly(context: Context): List<DictionaryEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CUSTOM_DICTS, "[]") ?: "[]"
        val out = mutableListOf<DictionaryEntry>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name")
                val pwArr = obj.optJSONArray("passwords") ?: JSONArray()
                val pws = mutableListOf<String>()
                for (j in 0 until pwArr.length()) {
                    val p = pwArr.optString(j)
                    if (p.isNotBlank()) pws.add(p)
                }
                if (name.isNotBlank() && pws.isNotEmpty()) {
                    out.add(DictionaryEntry(name, pws, isCustom = true))
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun saveCustomOnly(context: Context, list: List<DictionaryEntry>) {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("name", item.name)
            val pwArr = JSONArray()
            for (p in item.passwords) {
                pwArr.put(p)
            }
            obj.put("passwords", pwArr)
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_DICTS, arr.toString())
            .apply()
    }

    private fun loadDeletedBuiltins(prefs: android.content.SharedPreferences): MutableSet<String> {
        val set = mutableSetOf<String>()
        val raw = prefs.getString(KEY_DELETED_BUILTINS, "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                set.add(arr.optString(i))
            }
        } catch (_: Exception) {
        }
        return set
    }

    private fun saveDeletedBuiltins(prefs: android.content.SharedPreferences, set: Set<String>) {
        val arr = JSONArray()
        for (name in set) arr.put(name)
        prefs.edit().putString(KEY_DELETED_BUILTINS, arr.toString()).apply()
    }
}

object QueueStore {
    val items = mutableListOf<QueuedTarget>()

    fun addOrUpdate(target: QueuedTarget): Int {
        val existingIdx = items.indexOfFirst { it.ssid == target.ssid && it.bssid == target.bssid }
        return if (existingIdx >= 0) {
            items[existingIdx] = target
            existingIdx
        } else {
            items.add(0, target)
            0
        }
    }

    fun removeAt(index: Int) {
        if (index in items.indices) {
            items.removeAt(index)
        }
    }
}
