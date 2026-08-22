package com.matrixmessenger.feature.call.presentation.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.matrixmessenger.MainActivity
import com.matrixmessenger.feature.call.domain.repository.CallRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@AndroidEntryPoint
class CallService : Service() {

    @Inject
    lateinit var callRepository: CallRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeActiveCall()
    }

    private fun observeActiveCall() {
        callRepository.activeCallId.onEach { callId ->
            if (callId != null) {
                startForeground(NOTIFICATION_ID, createNotification("Ongoing Call"))
            } else {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.launchIn(serviceScope)
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Matrix Messenger")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ongoing Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Shows notifications for active calls"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "call_service_channel"
        private const val NOTIFICATION_ID = 101
    }
}
