package com.example.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.IBinder
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import kotlin.math.sin

class AlertService : Service() {

    companion object {
        private var client: MqttClient? = null
        private val TOPIC = "alerts_app_daniel123"
        var statusCallback: ((String) -> Unit)? = null

        fun sendAlert() {
            Thread {
                try {
                    client?.publish(TOPIC, MqttMessage("alert".toByteArray()))
                    statusCallback?.invoke("✅ Alert sent!")
                } catch (e: Exception) {
                    statusCallback?.invoke("❌ Send failed: ${e.message}")
                }
            }.start()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())
        connect()
        return START_STICKY // restart if killed
    }

    private fun connect() {
        Thread {
            try {
                client = MqttClient("wss://broker.hivemq.com:8884/mqtt", MqttClient.generateClientId(), MemoryPersistence())

                val options = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    isCleanSession = true
                }
                client!!.connect(options)
                client!!.subscribe(TOPIC) { _, _ ->
                    playFartSound()
                    statusCallback?.invoke("💨 ALERT!")
                }
                statusCallback?.invoke("✅ Connected. Ready.")
            } catch (e: Exception) {
                statusCallback?.invoke("❌ Connection failed: ${e.message}")
            }
        }.start()
    }

    private fun playFartSound() {
        Thread {
            val sampleRate = 44100
            val duration = 0.8 // seconds
            val numSamples = (sampleRate * duration).toInt()
            val samples = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val progress = t / duration
                // Decreasing frequency fart: starts low, gets lower, with noise
                val freq = 120.0 - progress * 80.0
                val noise = (Math.random() * 2 - 1) * 0.4
                val tone = sin(2 * Math.PI * freq * t) * (1.0 - progress)
                val envelope = if (progress < 0.1) progress / 0.1 else 1.0 - (progress - 0.1) / 0.9
                samples[i] = ((tone + noise) * envelope * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(samples, 0, samples.size)
            audioTrack.play()
            Thread.sleep((duration * 1000).toLong())
            audioTrack.release()
        }.start()
    }

    private fun buildNotification(): Notification {
        val channelId = "alert_service"
        val channel = NotificationChannel(channelId, "Alert Service", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        return Notification.Builder(this, channelId)
            .setContentTitle("Alerts App")
            .setContentText("Listening for alerts...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}