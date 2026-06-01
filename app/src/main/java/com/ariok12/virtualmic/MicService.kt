package com.ariok12.virtualmic

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
// removed audiofx imports
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class MicService : Service() {
    private var isRecording = false
    private var socket: DatagramSocket? = null
    private var audioRecordMic: AudioRecord? = null
    private var audioRecordMedia: AudioRecord? = null
    private var pcIpAddress: String? = null
    private var mediaProjection: MediaProjection? = null
    private var streamingThread: Thread? = null
    private val port = 8765

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    // removed audiofx variables

    companion object {
        const val ACTION_STOP = "STOP_SERVICE"
        const val ACTION_START_MIC = "START_MIC"
        const val ACTION_START_MEDIA = "START_MEDIA"
        const val ACTION_START_BOTH = "START_BOTH"
        
        const val ACTION_STATE_CHANGED = "com.ariok12.virtualmic.STATE_CHANGED"
        const val EXTRA_IS_STREAMING = "EXTRA_IS_STREAMING"
        
        var isServiceRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VirtualMic::StreamingWakeLock")
        
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "VirtualMic::StreamingWifiLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ip = intent?.getStringExtra("EXTRA_IP")
        if (!ip.isNullOrEmpty()) pcIpAddress = ip
        
        // removed audiofx intent parsing
        val micGain = intent?.getFloatExtra("EXTRA_MIC_GAIN", 1f) ?: 1f
        val isStereo = intent?.getBooleanExtra("EXTRA_STEREO", false) ?: false
        val is48k = intent?.getBooleanExtra("EXTRA_48K", false) ?: false

        when (val action = intent?.action) {
            ACTION_STOP -> {
                stopStreamingInternal()
                return START_NOT_STICKY
            }
            ACTION_START_MIC -> {
                updateNotification(active = true, type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                startStreaming(null, action, micGain, isStereo, is48k)
            }
            ACTION_START_MEDIA, ACTION_START_BOTH -> {
                val resultCode = intent.getIntExtra("RESULT_CODE", 0)
                val resultData = IntentCompat.getParcelableExtra(intent, "RESULT_DATA", Intent::class.java)

                if ((resultCode != 0) && (resultData != null)) {
                    val type = if (action == ACTION_START_BOTH) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    } else {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    }

                    updateNotification(true, type)

                    val mpm = getSystemService(MediaProjectionManager::class.java)
                    mediaProjection = mpm.getMediaProjection(resultCode, resultData)

                    startStreaming(mediaProjection, action, micGain, isStereo, is48k)
                }
            }
        }
        return START_STICKY
    }

    private fun stopStreamingInternal() {
        isRecording = false
        isServiceRunning = false
        sendStateBroadcast(false)
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()
        updateNotification(false, 0)
        stopSelf()
    }

    private fun sendStateBroadcast(streaming: Boolean) {
        val intent = Intent(ACTION_STATE_CHANGED).apply {
            putExtra(EXTRA_IS_STREAMING, streaming)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun updateNotification(active: Boolean, type: Int) {
        val stopIntent = Intent(this, MicService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val status = if (active) "Connected & Streaming" else "Disconnected"

        val notification = NotificationCompat.Builder(this, "MicChannel")
            .setContentTitle("Virtual Audio Stream")
            .setContentText("Status: $status ($pcIpAddress)")
            .setSmallIcon(R.drawable.ic_mic_streaming)
            .setOngoing(active)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (active) addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", stopPendingIntent)
            }
            .build()

        if (active) {
            startForeground(1, notification, type)
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startStreaming(
        projection: MediaProjection?, 
        mode: String?,
        micGain: Float,
        isStereo: Boolean,
        is48k: Boolean
    ) {
        if (isRecording) {
            isRecording = false
            streamingThread?.join(500)
        }

        wakeLock?.acquire(10 * 60 * 1000L)
        wifiLock?.acquire()

        isRecording = true
        isServiceRunning = true
        sendStateBroadcast(true)
        streamingThread = thread {
            try {
                socket = DatagramSocket()
                val address = InetAddress.getByName(pcIpAddress)

                val sampleRate = if (is48k) 48000 else 44100
                val channelConfig = if (isStereo) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

                val format = AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()

                val useMic = (mode == ACTION_START_MIC || mode == ACTION_START_BOTH)
                val useMedia = (mode == ACTION_START_MEDIA || mode == ACTION_START_BOTH) && projection != null

                if (useMic) {
                    audioRecordMic = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize)
                    audioRecordMic?.startRecording()
                }

                if (useMedia) {
                    val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build()

                    audioRecordMedia = AudioRecord.Builder()
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(bufferSize)
                        .setAudioPlaybackCaptureConfig(config)
                        .build()
                    audioRecordMedia?.startRecording()
                }

                val micBuffer = ShortArray(2048)
                val mediaBuffer = ShortArray(2048)
                val outBytes = ByteArray(8192)

                val mediaReadMode = if (useMic && useMedia) {
                    AudioRecord.READ_NON_BLOCKING
                } else {
                    AudioRecord.READ_BLOCKING
                }

                val channelsByte = (if (isStereo) 2 else 1).toByte()
                val rateByte = (if (is48k) 2 else 1).toByte()

                while (isRecording) {
                    var micRead = 0
                    var mediaRead = 0

                    if (useMic && audioRecordMic != null) {
                        micRead = audioRecordMic?.read(micBuffer, 0, 1024, AudioRecord.READ_BLOCKING) ?: 0
                        if (micGain != 1f && micRead > 0) {
                            for (i in 0 until micRead) {
                                var sample = (micBuffer[i].toInt() * micGain).toInt()
                                if (sample > 32767) sample = 32767
                                else if (sample < -32768) sample = -32768
                                micBuffer[i] = sample.toShort()
                            }
                        }
                    }

                    if (useMedia && audioRecordMedia != null) {
                        mediaRead = audioRecordMedia?.read(mediaBuffer, 0, 1024, mediaReadMode) ?: 0
                    }

                    val maxRead = maxOf(micRead, mediaRead)

                    if (maxRead > 0) {
                        outBytes[0] = 0xAA.toByte()
                        outBytes[1] = 0xBB.toByte()
                        outBytes[2] = channelsByte
                        outBytes[3] = rateByte
                        
                        for (i in 0 until maxRead) {
                            val sample1 = if (i < micRead) micBuffer[i].toInt() else 0
                            val sample2 = if (i < mediaRead) mediaBuffer[i].toInt() else 0

                            var mixed = sample1 + sample2

                            if (mixed > 32767) mixed = 32767
                            else if (mixed < -32768) mixed = -32768

                            outBytes[4 + i * 2] = (mixed and 0xFF).toByte()
                            outBytes[4 + i * 2 + 1] = ((mixed shr 8) and 0xFF).toByte()
                        }

                        val packet = DatagramPacket(outBytes, 4 + maxRead * 2, address, port)
                        socket?.send(packet)

                    } else {
                        Thread.sleep(2)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // removed audiofx release
                audioRecordMic?.stop()
                audioRecordMic?.release()
                audioRecordMic = null

                audioRecordMedia?.stop()
                audioRecordMedia?.release()
                audioRecordMedia = null

                mediaProjection?.stop()
                mediaProjection = null

                socket?.close()
                isRecording = false
                isServiceRunning = false
                sendStateBroadcast(false)
                
                if (wakeLock?.isHeld == true) wakeLock?.release()
                if (wifiLock?.isHeld == true) wifiLock?.release()
                
                updateNotification(false, 0)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("MicChannel", "Virtual Mic Service", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        isRecording = false
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}