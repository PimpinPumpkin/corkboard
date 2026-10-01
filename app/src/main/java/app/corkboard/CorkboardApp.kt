package app.corkboard

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import app.corkboard.data.Archive
import app.corkboard.data.Calibration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import app.corkboard.data.ClApi
import app.corkboard.data.Store
import app.corkboard.net.Http
import app.corkboard.net.imageLoader

class CorkboardApp : Application(), ImageLoaderFactory {
    val http: Http by lazy { Http(this) }
    val api: ClApi by lazy { ClApi(http) }
    val store: Store by lazy { Store(this) }

    val archive: Archive by lazy { Archive(this, http) }

    override fun onCreate() {
        super.onCreate()
        store.applyLocale()
        store.onKept = { archive.keep(it, api) }
        // The calibration in force: the last one fetched if newer than what is compiled in, then a
        // look for a newer one, at most once a day.
        val saved = getSharedPreferences("calibration", MODE_PRIVATE)
        saved.getString("json", null)?.let { Calibration.adopt(it, saved.getString("signature", null)) }
        if (System.currentTimeMillis() - saved.getLong("checked", 0) > 24 * 60 * 60 * 1000L) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val fresh = api.refreshCalibration()
                saved.edit().putLong("checked", System.currentTimeMillis()).apply { if (fresh != null) putString("json", fresh.first).putString("signature", fresh.second) }.apply()
            }
        }
        archive.prune(store.keptUuids())
    }

    override fun newImageLoader(): ImageLoader = imageLoader(this, http)
}
