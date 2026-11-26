package com.alialanbaki.wibrprored

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class NetworkScanActivity : AppCompatActivity() {

    private val permRequestCode = 301

    private lateinit var wifiManager: WifiManager
    private lateinit var locationManager: LocationManager
    private lateinit var tvScanStatus: TextView
    private lateinit var lvScanResults: ListView

    private val networks = mutableListOf<ScannedNetwork>()
    private lateinit var adapter: ScanAdapter

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val fresh = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, true) ?: true
            updateScanList(fresh)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network_scan)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        tvScanStatus = findViewById(R.id.tvScanStatus)
        lvScanResults = findViewById(R.id.lvScanResults)
        adapter = ScanAdapter()
        lvScanResults.adapter = adapter

        findViewById<Button>(R.id.btnRefreshNetworks).setOnClickListener {
            checkPermissionsAndScan()
        }

        lvScanResults.setOnItemClickListener { _, _, position, _ ->
            if (position in networks.indices) {
                AppState.pendingNetwork = networks[position]
                startActivity(Intent(this, DictionaryActivity::class.java))
            }
        }

        checkPermissionsAndScan()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(scanReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(scanReceiver)
        } catch (_: Exception) {
        }
    }

    private fun isGpsReady(): Boolean {
        return try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            false
        }
    }

    private fun checkPermissionsAndScan() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), permRequestCode)
            return
        }
        triggerScan()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permRequestCode) {
            triggerScan()
        }
    }

    private fun triggerScan() {
        if (!wifiManager.isWifiEnabled) {
            tvScanStatus.text = "الواي فاي متوقف — فعّل الواي فاي والموقع (GPS) ثم اضغط REFRESH"
            return
        }
        val gpsNote = if (isGpsReady()) "GPS مفعّل" else "GPS مطفأ (يُنصح بتفعيله)"
        tvScanStatus.text = "جاري فحص الشبكات... ($gpsNote)"

        @Suppress("DEPRECATION")
        val ok = wifiManager.startScan()
        if (!ok) {
            updateScanList(false)
        }
    }

    private fun updateScanList(fresh: Boolean) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        @Suppress("DEPRECATION")
        val results = wifiManager.scanResults.orEmpty()
        networks.clear()

        for (r in results) {
            @Suppress("DEPRECATION")
            val ssid = if (r.SSID.isNullOrBlank()) "<Hidden SSID>" else r.SSID
            val bssid = r.BSSID ?: "00:00:00:00:00:00"
            val caps = r.capabilities ?: "[OPEN]"
            val level = WifiManager.calculateSignalLevel(r.level, 100)
            networks.add(ScannedNetwork(ssid, bssid, caps, level))
        }

        adapter.notifyDataSetChanged()
        val mode = if (fresh) "محدث" else "مخزن"
        tvScanStatus.text = if (networks.isEmpty()) {
            "لم يتم العثور على شبكات — فعّل الموقع (GPS) وافصل الشبكة المتصلة حالياً"
        } else {
            "تم رصد ${networks.size} شبكة ($mode) — اختر شبكة لفتح القواميس"
        }
    }

    private inner class ScanAdapter : BaseAdapter() {
        override fun getCount(): Int = networks.size
        override fun getItem(position: Int): Any = networks[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@NetworkScanActivity)
                .inflate(R.layout.row_scan_network, parent, false)

            val net = networks[position]
            val tvSsid = view.findViewById<TextView>(R.id.tvScanSsid)
            val pbSignal = view.findViewById<ProgressBar>(R.id.pbSignalBar)
            val tvSecurity = view.findViewById<TextView>(R.id.tvScanSecurity)

            tvSsid.text = net.ssid
            pbSignal.progress = net.signalLevel.coerceIn(0, 100)
            tvSecurity.text = "${net.capabilities}  (${net.signalLevel}%)"

            return view
        }
    }
}
