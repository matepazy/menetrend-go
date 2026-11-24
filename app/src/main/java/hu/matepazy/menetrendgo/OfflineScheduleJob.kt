package hu.matepazy.menetrendgo

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class MenetrendGoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        OfflineSchedules.init(this)
    }
}

/** Runs queued offline timetable downloads, and the weekly refresh (unmetered network, charging) when enabled. */
class OfflineScheduleJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        OfflineSchedules.init(this)
        jobs[params.jobId] = scope.launch {
            try {
                when (params.jobId) {
                    DOWNLOAD -> drainQueue()
                    REFRESH -> {
                        val catalog = runCatching { OfflineSchedules.catalog().first }.getOrDefault(emptyList())
                        for (feed in OfflineSchedules.installedAsFeeds(catalog)) OfflineSchedules.process(feed, onlyIfChanged = true)
                        drainQueue()
                    }
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) { jobs.remove(params.jobId); jobFinished(params, false) }
            }
        }
        return true
    }

    private suspend fun drainQueue() {
        while (true) {
            running = true
            while (true) OfflineSchedules.process(OfflineSchedules.nextQueued() ?: break)
            running = false
            // A feed queued while the last one finished would otherwise wait for the next job.
            if (OfflineSchedules.nextQueued() == null) return
        }
    }

    override fun onStopJob(params: JobParameters): Boolean {
        jobs.remove(params.jobId)?.cancel()
        running = false
        return true
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        private const val DOWNLOAD = 7600
        private const val REFRESH = 7601
        @Volatile private var running = false

        fun startDownloads(context: Context) {
            if (running) return
            val scheduler = context.getSystemService(JobScheduler::class.java)
            scheduler.schedule(JobInfo.Builder(DOWNLOAD, ComponentName(context, OfflineScheduleJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build())
        }

        fun scheduleRefresh(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (!OfflineSchedules.autoRefresh || OfflineSchedules.installed.isEmpty()) { scheduler.cancel(REFRESH); return }
            if (scheduler.getPendingJob(REFRESH) != null) return
            scheduler.schedule(JobInfo.Builder(REFRESH, ComponentName(context, OfflineScheduleJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED).setRequiresCharging(true)
                .setPeriodic(TimeUnit.DAYS.toMillis(7)).setPersisted(true).build())
        }
    }
}
