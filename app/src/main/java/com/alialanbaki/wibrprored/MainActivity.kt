package com.alialanbaki.wibrprored

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var lvQueuedNetworks: ListView
    private lateinit var adapter: QueuedAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lvQueuedNetworks = findViewById(R.id.lvQueuedNetworks)
        adapter = QueuedAdapter()
        lvQueuedNetworks.adapter = adapter

        findViewById<Button>(R.id.btnHelp).setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }

        findViewById<Button>(R.id.btnExit).setOnClickListener {
            finishAffinity()
        }

        findViewById<Button>(R.id.btnAddNetwork).setOnClickListener {
            startActivity(Intent(this, NetworkScanActivity::class.java))
        }

        lvQueuedNetworks.setOnItemClickListener { _, _, position, _ ->
            if (position in QueueStore.items.indices) {
                AppState.activeQueuedIndex = position
                startActivity(Intent(this, MonitorActivity::class.java))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
    }

    private inner class QueuedAdapter : BaseAdapter() {
        override fun getCount(): Int = QueueStore.items.size
        override fun getItem(position: Int): Any = QueueStore.items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@MainActivity)
                .inflate(R.layout.row_queued_network, parent, false)

            val item = QueueStore.items[position]
            val tvSsid = view.findViewById<TextView>(R.id.tvRowSsid)
            val tvMeta = view.findViewById<TextView>(R.id.tvRowMeta)
            val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteQueued)

            tvSsid.text = item.ssid
            val dictsLabel = if (item.dictionaryNames.isEmpty()) "قائمة مخصصة" else item.dictionaryNames.joinToString(", ")
            tvMeta.text = "Password: ${item.progressCurrent} of ${item.passwords.size}  |  $dictsLabel  |  ${item.delaySeconds}s"

            btnDelete.setOnClickListener {
                QueueStore.removeAt(position)
                notifyDataSetChanged()
            }

            return view
        }
    }
}