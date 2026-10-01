package app.corkboard.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.corkboard.CorkboardApp
import app.corkboard.MainActivity
import app.corkboard.R
import app.corkboard.data.ClUrls
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Saved-search alerts, done on the phone: every few hours the app runs each saved search once and
 * says so if something new turned up. There is no server and no push service behind this, which
 * is the point, and it is deliberately slow: a person refreshing a search a few times a day, not
 * a machine polling it.
 */
object Alerts {
    private const val WORK = "saved-search-alerts"
    private const val CHANNEL = "new-listings"
    const val EXTRA_OPEN_SAVED = "open_saved"

    /** Starts the periodic check if any saved search wants alerts, and stops it if none does. */
    fun schedule(context: Context) {
        val app = context.applicationContext as CorkboardApp
        val manager = WorkManager.getInstance(app)
        if (app.store.saved.value.none { it.alerts }) {
            manager.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<AlertWorker>(3, TimeUnit.HOURS, 1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(Random.nextLong(30, 90), TimeUnit.MINUTES)
            .build()
        manager.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Runs the check once, right now. Debug builds only: it exists to test alerts without waiting hours. */
    fun runNow(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueue(OneTimeWorkRequestBuilder<AlertWorker>().build())
    }

    fun notify(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "New listings", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_SAVED, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }
}

class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CorkboardApp
        val searches = app.store.saved.value.filter { it.alerts }
        for ((i, saved) in searches.withIndex()) {
            // Spread the searches out instead of firing them in one burst.
            if (i > 0) delay(Random.nextLong(8_000, 25_000))
            val page = runCatching { app.api.search(saved.query, ClUrls.sortId(saved.query.sort)) }.getOrNull() ?: continue
            val fresh = page.items.count { it.postingId > saved.newestSeen }
            if (fresh == 0 || fresh == saved.unseen) continue
            app.store.updateSaved(saved.id) { it.copy(unseen = fresh) }
            val what = saved.query.text.ifEmpty { saved.query.categoryName }
            Alerts.notify(
                applicationContext, (saved.id % Int.MAX_VALUE).toInt(),
                // A full first page of new rows means there are more than were counted.
                if (fresh == 1) "1 new listing" else if (fresh >= page.items.size && page.total > fresh) "$fresh+ new listings" else "$fresh new listings",
                "$what in ${saved.query.areaName}",
            )
        }
        return Result.success()
    }
}
