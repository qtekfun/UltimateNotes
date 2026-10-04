// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.qtekfun.ultimatenotes.R

/**
 * Runs one sync for both the one-time and the periodic work. The decisions live in [SyncRunner];
 * this only maps its outcome to WorkManager's. Retries use WorkManager's exponential backoff, set
 * from the BackoffPolicy's initial delay.
 */
class SyncWorker(context: Context, params: WorkerParameters, private val runner: SyncRunner) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (runner.run(runAttemptCount + 1)) {
        RunOutcome.DONE -> Result.success()
        RunOutcome.RETRY -> Result.retry()
        RunOutcome.STOP -> Result.failure()
    }

    /** Needed by expedited work before Android 12, which runs it as a foreground service. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.sync_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.sync_notification_title))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private companion object {
        const val CHANNEL_ID = "sync"
        const val NOTIFICATION_ID = 1
    }
}
