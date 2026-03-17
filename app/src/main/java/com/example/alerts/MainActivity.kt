package com.example.alerts

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)

        // Start the background service
        val intent = Intent(this, AlertService::class.java)
        startForegroundService(intent)

        // Update status from service
        AlertService.statusCallback = { msg ->
            runOnUiThread { tvStatus.text = msg }
        }

        findViewById<Button>(R.id.btnAlert).setOnClickListener {
            tvStatus.text = "📤 Sending..."
            AlertService.sendAlert()
        }
    }

    override fun onResume() {
        super.onResume()
        AlertService.statusCallback = { msg ->
            runOnUiThread { tvStatus.text = msg }
        }
    }
}