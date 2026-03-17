package com.example.alerts

import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.os.Build

class MainActivity : AppCompatActivity() {

    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvStatus: TextView
    private lateinit var cbSleepWindow: CheckBox
    private lateinit var cbWindow: CheckBox
    private lateinit var btnAlert: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        tvStatus = findViewById(R.id.tvStatus)
        cbSleepWindow = findViewById(R.id.cbSleepWindow)
        cbWindow = findViewById(R.id.cbWindow)
        btnAlert = findViewById(R.id.btnAlert)

        updateConnectionStatusUi(AlertService.lastConnStatus)
        updateStatusUi(AlertService.lastStatus)

        AlertService.connectionCallback = { message ->
            runOnUiThread { updateConnectionStatusUi(message) }
        }

        AlertService.statusCallback = { message ->
            runOnUiThread { updateStatusUi(message) }
        }

        val intent = Intent(this, AlertService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        cbWindow.isChecked = true
        cbSleepWindow.isChecked = false

        cbSleepWindow.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                cbWindow.isChecked = false
            } else if (!cbWindow.isChecked) {
                cbSleepWindow.isChecked = true
            }
            updateServiceState()
        }

        cbWindow.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                cbSleepWindow.isChecked = false
            } else if (!cbSleepWindow.isChecked) {
                cbWindow.isChecked = true
            }
            updateServiceState()
        }
        
        btnAlert.setOnClickListener {
            AlertService.sendAlert()
        }
    }

    private fun updateServiceState() {
        val statusStr = if (cbSleepWindow.isChecked) "Asleep with open window" else "Window closed"
        showStatusNotification(statusStr)
        AlertService.sendStatusMessage(statusStr)
    }

    private fun updateConnectionStatusUi(message: String) {
        tvConnectionStatus.text = "Connection: " + message
    }

    private fun updateStatusUi(message: String) {
        tvStatus.text = "Status: " + message
        if (message.contains("alert", ignoreCase = true) || message.contains("error", ignoreCase = true) || message.contains("fail", ignoreCase = true)) {
            tvStatus.setTextColor(android.graphics.Color.RED)
        } else {
            tvStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        }
    }

    private fun showStatusNotification(statusStr: String) {
        val channelId = "status_events"
        val notificationManager = getSystemService(NotificationManager::class.java)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Status Events",
                NotificationManager.IMPORTANCE_LOW 
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        notificationManager.notify(3, notification
            .setContentTitle("App Status")
            .setContentText(statusStr)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build())
    }

    override fun onDestroy() {
        super.onDestroy()
        AlertService.connectionCallback = null
        AlertService.statusCallback = null
    }
}