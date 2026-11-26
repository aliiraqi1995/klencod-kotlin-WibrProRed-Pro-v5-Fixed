package com.alialanbaki.wibrprored

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

class DictionaryActivity : AppCompatActivity() {

    private lateinit var tvDictTargetName: TextView
    private lateinit var lvDictionaries: ListView
    private lateinit var tvDictSpeedLabel: TextView

    private val dicts = mutableListOf<DictionaryEntry>()
    private val selectedNames = mutableSetOf<String>()
    private lateinit var adapter: DictAdapter

    private val pickExternalDictLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                importExternalDictionary(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dictionary)

        tvDictTargetName = findViewById(R.id.tvDictTargetName)
        lvDictionaries = findViewById(R.id.lvDictionaries)
        tvDictSpeedLabel = findViewById(R.id.tvDictSpeedLabel)

        val target = AppState.pendingNetwork
        tvDictTargetName.text = target?.ssid ?: "شبكة"

        adapter = DictAdapter()
        lvDictionaries.adapter = adapter
        reloadDictionaries(selectFirstIfEmpty = true)

        findViewById<Button>(R.id.btnAddExternalDict).setOnClickListener {
            pickExternalDictLauncher.launch(arrayOf("text/*"))
        }

        findViewById<Button>(R.id.btnSpeedDec).setOnClickListener {
            if (AppState.delaySeconds > 1) {
                AppState.delaySeconds -= 1
                updateSpeedLabel()
            }
        }

        findViewById<Button>(R.id.btnSpeedInc).setOnClickListener {
            if (AppState.delaySeconds < 60) {
                AppState.delaySeconds += 1
                updateSpeedLabel()
            }
        }

        findViewById<Button>(R.id.btnStartTest).setOnClickListener {
            val net = AppState.pendingNetwork
            if (net == null) {
                Toast.makeText(this, "لم يتم تحديد شبكة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val chosenDicts = dicts.filter { selectedNames.contains(it.name) }
            if (chosenDicts.isEmpty()) {
                Toast.makeText(this, "حدد قاموساً واحداً على الأقل", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val mergedPasswords = LinkedHashSet<String>()
            for (d in chosenDicts) {
                mergedPasswords.addAll(d.passwords)
            }

            val queued = QueuedTarget(
                ssid = net.ssid,
                bssid = net.bssid,
                capabilities = net.capabilities,
                signalLevel = net.signalLevel,
                dictionaryNames = chosenDicts.map { it.name },
                passwords = mergedPasswords.toList(),
                delaySeconds = AppState.delaySeconds,
                progressCurrent = 0,
                progressTotal = mergedPasswords.size
            )

            val idx = QueueStore.addOrUpdate(queued)
            AppState.activeQueuedIndex = idx
            startActivity(Intent(this, MonitorActivity::class.java))
        }

        updateSpeedLabel()
    }

    private fun updateSpeedLabel() {
        tvDictSpeedLabel.text = "Speed: ${AppState.delaySeconds}s per attempt"
    }

    private fun reloadDictionaries(selectFirstIfEmpty: Boolean) {
        dicts.clear()
        dicts.addAll(DictionaryStore.loadAll(this))
        selectedNames.retainAll(dicts.map { it.name }.toSet())
        if (selectFirstIfEmpty && selectedNames.isEmpty() && dicts.isNotEmpty()) {
            selectedNames.add(dicts.first().name)
        }
        adapter.notifyDataSetChanged()
    }

    private fun importExternalDictionary(uri: Uri) {
        try {
            val words = mutableListOf<String>()
            contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        val clean = line.trim()
                        if (clean.isNotEmpty()) {
                            words.add(clean)
                        }
                        line = reader.readLine()
                    }
                }
            }

            if (words.isEmpty()) {
                Toast.makeText(this, "الملف فارغ أو لا يحتوي على كلمات", Toast.LENGTH_SHORT).show()
                return
            }

            val rawName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
                ?: "custom_dict.txt"
            val dictName = if (rawName.endsWith(".txt", ignoreCase = true)) rawName else "$rawName.txt"

            DictionaryStore.addOrReplaceCustomDictionary(this, dictName, words)
            selectedNames.add(dictName)
            reloadDictionaries(selectFirstIfEmpty = false)
            Toast.makeText(this, "تم حفظ القاموس ($dictName) تلقائياً (${words.size} كلمة)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر قراءة الملف: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class DictAdapter : BaseAdapter() {
        override fun getCount(): Int = dicts.size
        override fun getItem(position: Int): Any = dicts[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@DictionaryActivity)
                .inflate(R.layout.row_dictionary_item, parent, false)

            val entry = dicts[position]
            val cb = view.findViewById<CheckBox>(R.id.cbDictSelected)
            val tvName = view.findViewById<TextView>(R.id.tvDictName)
            val tvCount = view.findViewById<TextView>(R.id.tvDictCount)
            val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteDict)

            tvName.text = entry.name
            tvCount.text = "${entry.passwords.size} passwords${if (entry.isCustom) " • محفوظ" else ""}"

            cb.setOnCheckedChangeListener(null)
            cb.isChecked = selectedNames.contains(entry.name)
            cb.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedNames.add(entry.name) else selectedNames.remove(entry.name)
            }

            view.setOnClickListener {
                val next = !cb.isChecked
                cb.isChecked = next
            }

            btnDelete.setOnClickListener {
                DictionaryStore.deleteDictionary(this@DictionaryActivity, entry)
                selectedNames.remove(entry.name)
                reloadDictionaries(selectFirstIfEmpty = true)
                Toast.makeText(this@DictionaryActivity, "تم حذف ${entry.name}", Toast.LENGTH_SHORT).show()
            }

            return view
        }
    }
}
