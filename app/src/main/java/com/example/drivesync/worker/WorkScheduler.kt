package com.example.drivesync.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {
    const val PERIODIC_WORK_NAME = "drivesync_periodic_sync"

    fun updateSchedule(context: Context, interval: String, wifiOnly: Boolean) {
        val workManager = WorkManager.getInstance(context)

        if (interval == "OFF") {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }

        val repeatHours = when (interval) {
            "6H" -> 6L
            "12H" -> 12L
            "24H" -> 24L
            else -> return
        }

        val constraintsBuilder = Constraints.Builder()
        if (wifiOnly) {
            constraintsBuilder.setRequiredNetworkType(NetworkType.UNMETERED)
        } else {
            constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
        }

        val periodicWork = PeriodicWorkRequestBuilder<SyncWorker>(repeatHours, TimeUnit.HOURS)
            .setConstraints(constraintsBuilder.build())
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWork
        )
    }
}
