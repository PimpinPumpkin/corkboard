package app.corkboard

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import app.corkboard.data.Archive
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
        archive.prune(store.keptUuids())
    }

    override fun newImageLoader(): ImageLoader = imageLoader(this, http)
}
