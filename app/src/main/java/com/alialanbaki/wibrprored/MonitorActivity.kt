package com.alialanbaki.wibrprored

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.SupplicantState
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MonitorActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wifiManager: WifiManager

    private lateinit var tvRunState: TextView
    private lateinit var pbSpinner: ProgressBar
    private lateinit var tvSsid: TextView
    private lateinit var tvBssid: TextView
    private lateinit var tvEncryption: TextView
    private lateinit var tvPasswordTry: TextView
    private lateinit var tvPassProgress: TextView
    private lateinit var tvSpeedInfo: TextView
    private lateinit var tvLinkStatus: TextView
    private lateinit var tvEstimatedInfo: TextView
    private lateinit var tvSignalInfo: TextView
    private lateinit var tvMonSpeedLabel: TextView
    private lateinit var btnPause: Button

    private var targetIndex = -1
    private var currentPasswordBeingTested: String = "-"
    private var currentIndex = 0
    private var isPaused = false
    private var isConnectedSuccess = false
    private var isExhausted = false

    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isConnectedSuccess || isExhausted) return
            evaluateConnectionState(intent)
        }
    }

    private val attemptRunnable = object : Runnable {
        override fun run() {
            if (isPaused || isConnectedSuccess || isExhausted) return

            val queued = QueueStore.items.getOrNull(targetIndex) ?: return
            val total = queued.passwords.size

            if (currentIndex >= total) {
                isExhausted = true
                pbSpinner.isIndeterminate = false
                pbSpinner.progress = 100
                tvRunState.text = "Test FINISHED"
                tvLinkStatus.text = "No match found"
                tvEstimatedInfo.text = "0 hr, 0 min"
                btnPause.isEnabled = false
                return
            }

            val candidate = queued.passwords[currentIndex]
            currentPasswordBeingTested = candidate
            val attemptNumber = currentIndex + 1

            tvPasswordTry.text = "Password: $candidate"
            tvPassProgress.text = "$attemptNumber of $total"
            tvLinkStatus.text = "Trying connection..."

            QueueStore.items[targetIndex] = queued.copy(progressCurrent = attemptNumber)

            attemptWifiJoin(queued.ssid, queued.bssid, queued.capabilities, candidate)
            updateSpeedAndEstimate(remaining = total - attemptNumber, delaySec = queued.delaySeconds)

            currentIndex++
            val waitMs = queued.delaySeconds.coerceAtLeast(1) * 1000L
            handler.postDelayed(this, waitMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monitor)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        tvRunState = findViewById(R.id.tvRunState)
        pbSpinner = findViewById(R.id.pbSpinner)
        tvSsid = findViewById(R.id.tvSsid)
        tvBssid = findViewById(R.id.tvBssid)
        tvEncryption = findViewById(R.id.tvEncryption)
        tvPasswordTry = findViewById(R.id.tvPasswordTry)
        tvPassProgress = findViewById(R.id.tvPassProgress)
        tvSpeedInfo = findViewById(R.id.tvSpeedInfo)
        tvLinkStatus = findViewById(R.id.tvLinkStatus)
        tvEstimatedInfo = findViewById(R.id.tvEstimatedInfo)
        tvSignalInfo = findViewById(R.id.tvSignalInfo)
        tvMonSpeedLabel = findViewById(R.id.tvMonSpeedLabel)
        btnPause = findViewById(R.id.btnPause)

        targetIndex = AppState.activeQueuedIndex
        val queued = QueueStore.items.getOrNull(targetIndex)
        if (queued == null) {
            finish()
            return
        }

        tvSsid.text = "SSID: ${queued.ssid}"
        tvBssid.text = "BSSID: ${queued.bssid}"
        tvEncryption.text = "Encryption: ${queued.capabilities}"
        tvSignalInfo.text = "${queued.signalLevel}"
        tvPassProgress.text = "0 of ${queued.passwords.size}"
        updateSpeedLabel(queued.delaySeconds)
        updateSpeedAndEstimate(remaining = queued.passwords.size, delaySec = queued.delaySeconds)

        findViewById<Button>(R.id.btnMonSpeedDec).setOnClickListener {
            val cur = QueueStore.items.getOrNull(targetIndex) ?: return@setOnClickListener
            if (cur.delaySeconds > 1) {
                val updatedSec = cur.delaySeconds - 1
                AppState.delaySeconds = updatedSec
                QueueStore.items[targetIndex] = cur.copy(delaySeconds = updatedSec)
                updateSpeedLabel(updatedSec)
                updateSpeedAndEstimate(remaining = (cur.passwords.size - currentIndex).coerceAtLeast(0), delaySec = updatedSec)
                rescheduleRunnable(updatedSec)
            }
        }

        findViewById<Button>(R.id.btnMonSpeedInc).setOnClickListener {
            val cur = QueueStore.items.getOrNull(targetIndex) ?: return@setOnClickListener
            if (cur.delaySeconds < 60) {
                val updatedSec = cur.delaySeconds + 1
                AppState.delaySeconds = updatedSec
                QueueStore.items[targetIndex] = cur.copy(delaySeconds = updatedSec)
                updateSpeedLabel(updatedSec)
                updateSpeedAndEstimate(remaining = (cur.passwords.size - currentIndex).coerceAtLeast(0), delaySec = updatedSec)
                rescheduleRunnable(updatedSec)
            }
        }

        btnPause.setOnClickListener {
            if (isConnectedSuccess || isExhausted) return@setOnClickListener
            if (!isPaused) {
                isPaused = true
                handler.removeCallbacks(attemptRunnable)
                tvRunState.text = "Test PAUSED"
                btnPause.text = "Resume"
            } else {
                isPaused = false
                tvRunState.text = "Test RUNNING..."
                btnPause.text = "Pause"
                handler.post(attemptRunnable)
            }
        }

        handler.post(attemptRunnable)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wifiStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(wifiStateReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(wifiStateReceiver)
        } catch (_: Exception) {
        }
    }

    private fun evaluateConnectionState(intent: Intent?) {
        val queued = QueueStore.items.getOrNull(targetIndex) ?: return

        @Suppress("DEPRECATION")
        val info = wifiManager.connectionInfo
        @Suppress("DEPRECATION")
        val activeSsid = info?.ssid?.replace("\"", "") ?: ""
        val supplicantOk = info?.supplicantState == SupplicantState.COMPLETED

        @Suppress("DEPRECATION")
        val netInfo: NetworkInfo? = intent?.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO)
        val connectedState = netInfo?.isConnected == true

        if (activeSsid == queued.ssid && (supplicantOk || connectedState)) {
            onPasswordMatched(queued.ssid, currentPasswordBeingTested)
        }
    }

    private fun onPasswordMatched(ssid: String, matchedPassword: String) {
        if (isConnectedSuccess) return
        isConnectedSuccess = true
        isPaused = true
        handler.removeCallbacks(attemptRunnable)

        pbSpinner.isIndeterminate = false
        pbSpinner.progress = 100
        tvRunState.text = "CONNECTED!"
        tvPasswordTry.text = "Password: $matchedPassword"
        tvLinkStatus.text = "Connected to $ssid"
        tvEstimatedInfo.text = "0 hr, 0 min"
        btnPause.text = "Connected"
        btnPause.isEnabled = false
    }

    private fun attemptWifiJoin(ssid: String, bssid: String, capabilities: String, password: String) {
        if (!wifiManager.isWifiEnabled) return
        try {
            @Suppress("DEPRECATION")
            val config = WifiConfiguration().apply {
                SSID = "\"$ssid\""
                BSSID = bssid
                if (capabilities.uppercase().contains("WEP")) {
                    wepKeys[0] = "\"$password\""
                    wepTxKeyIndex = 0
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                    allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
                } else if (capabilities.uppercase().contains("WPA") ||
                    capabilities.uppercase().contains("PSK")
                ) {
                    preSharedKey = "\"$password\""
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                } else {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                }
            }

            @Suppress("DEPRECATION")
            val netId = wifiManager.addNetwork(config)
            if (netId != -1) {
                @Suppress("DEPRECATION")
                wifiManager.disconnect()
                @Suppress("DEPRECATION")
                wifiManager.enableNetwork(netId, true)
                @Suppress("DEPRECATION")
                wifiManager.reconnect()
            }
        } catch (_: Exception) {
        }
    }

    private fun rescheduleRunnable(delaySec: Int) {
        if (!isPaused && !isConnectedSuccess && !isExhausted) {
            handler.removeCallbacks(attemptRunnable)
            handler.postDelayed(attemptRunnable, delaySec * 1000L)
        }
    }

    private fun updateSpeedLabel(delaySec: Int) {
        tvMonSpeedLabel.text = "Interval: ${delaySec}s"
    }

    private fun updateSpeedAndEstimate(remaining: Int, delaySec: Int) {
        val safeSec = delaySec.coerceAtLeast(1)
        val perMin = 60 / safeSec
        tvSpeedInfo.text = "$perMin passwords/minute"

        val totalSec = remaining.coerceAtLeast(0).toLong() * safeSec.toLong()
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        tvEstimatedInfo.text = "$hours hr, $minutes min"
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(attemptRunnable)
    }
}
