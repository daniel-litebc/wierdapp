package com.example.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.pm.ServiceInfo
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.ArrayDeque
import kotlin.math.sin

class AlertService : Service() {

    private val randomSoundNames = listOf(
        "sound_1",
        "sound_2",
        "sound_3",
        "sound_4",
        "sound_5"
    )

    companion object {
        private var client: MqttClient? = null
        private val TOPIC = "alerts_app_daniel123"
        var lastStatus: String = "Connecting..."
        var statusCallback: ((String) -> Unit)? = null
            set(value) {
                field = value
                value?.invoke(lastStatus)
            }

        var lastConnStatus: String = "Connecting..."
        var connectionCallback: ((String) -> Unit)? = null
            set(value) {
                field = value
                value?.invoke(lastConnStatus)
            }
        
        fun updateStatus(msg: String) {
            lastStatus = msg
            statusCallback?.invoke(msg)
        }
        
        fun updateConnection(msg: String) {
            lastConnStatus = msg
            connectionCallback?.invoke(msg)
        }

        fun sendStatusMessage(msg: String) {
            Thread {
                try {
                    client?.publish(TOPIC, MqttMessage(msg.toByteArray()))
                    updateStatus("✅ Sent: $msg")
                } catch (e: Exception) {
                    updateStatus("❌ Send failed: ${e.message}")
                }
            }.start()
        }

        fun sendAlert() {
            sendStatusMessage("alert")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    1,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(1, buildNotification())
            }
        } catch (e: ForegroundServiceStartNotAllowedException) {
            updateConnection("❌ Foreground start not allowed right now")
            stopSelfResult(startId)
            return START_NOT_STICKY
        } catch (e: SecurityException) {
            updateConnection("❌ Missing foreground service permission")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        connect()
        return START_NOT_STICKY
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
                client!!.subscribe(TOPIC) { _, msg ->
                    val payloadStr = String(msg.payload)
                    if (payloadStr == "alert") {
                        playRandomAlertSound()
                        updateStatus("🔊 ALERT!")
                    } else {
                        updateStatus("📝 $payloadStr")
                    }
                }
                updateConnection("✅ Connected. Ready.")
            } catch (e: Exception) {
                updateConnection("❌ Connection failed: ${e.message}")
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

    private fun playRandomAlertSound() {
        val chosenName = randomSoundNames.random()
        val resourceId = resources.getIdentifier(chosenName, "raw", packageName)

        if (resourceId == 0) {
            playFartSound()
            return
        }

        val mediaPlayer = MediaPlayer.create(this, resourceId)
        if (mediaPlayer == null) {
            playFartSound()
            return
        }

        val duration = mediaPlayer.duration
        val startPos = if (duration > 5000) duration / 2 else 0
        mediaPlayer.seekTo(startPos)
        mediaPlayer.start()

        Thread {
            Thread.sleep(5000)
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.release()
            } catch (e: Exception) {
                // Ignore exceptions on release
            }
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

    private fun showIncomingAlertNotification() {
        val channelId = "alert_events"
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId,
            "Alert Events",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Alerts App")
            .setContentText("New alert received")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}