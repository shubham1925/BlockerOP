package com.example.blockerop.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/**
 * Periodic watchdog scheduled via JobScheduler (minimum 15-minute interval).
 * JobScheduler jobs are managed by the OS and survive process death, so this
 * provides a reliable heartbeat that restarts the foreground service if it
 * was killed by the system or the user.
 */
class GuardJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        BlockerForegroundService.start(applicationContext)
        // Reschedule for next run
        schedule(applicationContext)
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean {
        return true  // reschedule if interrupted
    }

    companion object {
        private const val JOB_ID = 1002

        fun schedule(context: Context) {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            // Don't double-schedule
            if (js.getPendingJob(JOB_ID) != null) return

            val job = JobInfo.Builder(JOB_ID, ComponentName(context, GuardJobService::class.java))
                .setPeriodic(15 * 60 * 1000L)   // 15 min minimum on Android
                .setPersisted(true)              // survives reboot
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .build()

            js.schedule(job)
        }
    }
}
