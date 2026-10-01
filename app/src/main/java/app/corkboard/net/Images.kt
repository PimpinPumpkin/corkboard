package app.corkboard.net

import android.content.Context
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.memory.MemoryCache
import coil.request.Options
import okio.Buffer
import java.io.IOException

/**
 * Coil decodes and keeps images in memory; the bytes come through Cronet like every other request.
 * Coil's own network fetcher would speak OkHttp, a second network stack with a second fingerprint
 * beside the first, so it is replaced. Cronet's disk cache does the caching.
 */
private class CronetFetcher(private val url: String, private val options: Options, private val http: Http) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val r = http.get(url, BrowserHeaders.Kind.Image)
        if (!r.ok) throw IOException("image ${r.code}")
        return SourceResult(
            source = ImageSource(Buffer().write(r.body), options.context),
            mimeType = r.contentType,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory(private val http: Http) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == "https" || data.scheme == "http") CronetFetcher(data.toString(), options, http) else null
    }
}

fun imageLoader(context: Context, http: Http): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(CronetFetcher.Factory(http)) }
        .memoryCache { MemoryCache.Builder(context).maxSizePercent(0.2).build() }
        .diskCache(null)
        .crossfade(true)
        .build()
