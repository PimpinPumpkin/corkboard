package app.corkboard.net

import android.content.Context
import android.os.LocaleList
import android.util.Log
import app.corkboard.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HttpResponse(val code: Int, val body: ByteArray, val contentType: String?) {
    val ok: Boolean get() = code in 200..299
    fun text(): String = body.toString(Charsets.UTF_8)
}

/**
 * Every network request the app makes goes through here, and so through Cronet: Chromium's own
 * network stack. OkHttp's TLS handshake and HTTP/2 settings identify OkHttp whatever the user
 * agent says; Cronet's are Chrome's. The app talks to craigslist from the user's own phone at the
 * speed the user taps, as Chrome on Android, which is what it honestly is underneath.
 */
class Http(context: Context) {
    private val appContext = context.applicationContext
    private val executor = Executors.newCachedThreadPool { r -> Thread(r, "cronet-cb").apply { isDaemon = true } }
    val cookies = CookieStore(appContext.getSharedPreferences("cookies", Context.MODE_PRIVATE))

    private val engine: CronetEngine by lazy {
        val cache = File(appContext.cacheDir, "cronet").apply { mkdirs() }
        CronetEngine.Builder(appContext)
            .setUserAgent(BrowserHeaders.userAgent(BuildConfig.CRONET_VERSION))
            .enableHttp2(true).enableQuic(true).enableBrotli(true)
            .setStoragePath(cache.absolutePath)
            // The site marks search results cacheable for 15 minutes and images for much longer;
            // honoring that is both polite and what a browser does.
            .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, 96L * 1024 * 1024)
            .build()
            .also { Log.i(TAG, "engine ${it.versionString}") }
    }

    private fun acceptLanguage(): String {
        val locales = LocaleList.getDefault()
        return BrowserHeaders.acceptLanguage(List(locales.size()) { locales[it].toLanguageTag() })
    }

    /** [fresh] skips the cache, the way pulling down to reload a page does. */
    suspend fun get(url: String, kind: BrowserHeaders.Kind, fresh: Boolean = false): HttpResponse = suspendCancellableCoroutine { cont ->
        val out = ByteArrayOutputStream()
        val host = URI(url).host.orEmpty()
        val callback = object : UrlRequest.Callback() {
            override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) = request.followRedirect()
            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) = request.read(ByteBuffer.allocateDirect(64 * 1024))
            override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, buf: ByteBuffer) {
                buf.flip()
                val b = ByteArray(buf.remaining())
                buf.get(b)
                out.write(b)
                buf.clear()
                request.read(buf)
            }

            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                info.allHeadersAsList.filter { it.key.equals("set-cookie", true) }.forEach { cookies.save(host, it.value) }
                val type = info.allHeadersAsList.firstOrNull { it.key.equals("content-type", true) }?.value
                if (BuildConfig.DEBUG) Log.d(TAG, "${info.httpStatusCode} ${info.negotiatedProtocol}${if (info.wasCached()) " cached" else ""} ${url.take(140)}")
                if (cont.isActive) cont.resume(HttpResponse(info.httpStatusCode, out.toByteArray(), type))
            }

            override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: CronetException) {
                if (cont.isActive) cont.resumeWithException(IOException(error.message ?: "network error", error))
            }

            override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
                if (cont.isActive) cont.resumeWithException(IOException("canceled"))
            }
        }
        val builder = try {
            engine.newUrlRequestBuilder(url, callback, executor)
        } catch (t: Throwable) {
            cont.resumeWithException(IOException("network stack unavailable", t))
            return@suspendCancellableCoroutine
        }
        BrowserHeaders.headers(kind, BuildConfig.CRONET_VERSION, acceptLanguage()).forEach { (k, v) -> builder.addHeader(k, v) }
        // Images are fetched without credentials by a page, the API with them.
        if (kind == BrowserHeaders.Kind.Api) cookies.header(host)?.let { builder.addHeader("Cookie", it) }
        if (fresh) builder.disableCache()
        val request = builder.build()
        cont.invokeOnCancellation { request.cancel() }
        request.start()
    }

    /**
     * How a page answers, without downloading it when it is simply there: a 200 is reported and
     * dropped, anything else is read (it is short, and says why). Null when there was no answer.
     */
    suspend fun probe(url: String): HttpResponse? = suspendCancellableCoroutine { cont ->
        val out = ByteArrayOutputStream()
        val callback = object : UrlRequest.Callback() {
            override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) = request.followRedirect()
            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                if (info.httpStatusCode in 200..299) {
                    request.cancel()
                    if (cont.isActive) cont.resume(HttpResponse(info.httpStatusCode, ByteArray(0), null))
                } else request.read(ByteBuffer.allocateDirect(32 * 1024))
            }
            override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, buf: ByteBuffer) {
                buf.flip()
                val b = ByteArray(buf.remaining())
                buf.get(b)
                if (out.size() < 256 * 1024) out.write(b)
                buf.clear()
                request.read(buf)
            }
            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) { if (cont.isActive) cont.resume(HttpResponse(info.httpStatusCode, out.toByteArray(), null)) }
            override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: CronetException) { if (cont.isActive) cont.resume(null) }
            override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) { if (cont.isActive) cont.resume(null) }
        }
        val request = try {
            engine.newUrlRequestBuilder(url, callback, executor).apply {
                BrowserHeaders.headers(BrowserHeaders.Kind.Page, BuildConfig.CRONET_VERSION, acceptLanguage()).forEach { (k, v) -> addHeader(k, v) }
                cookies.header(URI(url).host.orEmpty())?.let { addHeader("Cookie", it) }
                disableCache()
            }.build()
        } catch (t: Throwable) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        cont.invokeOnCancellation { request.cancel() }
        request.start()
    }

    /**
     * Where [url] redirects to, without going there. craigslist's front door sends each visitor to
     * the site nearest their network address; the destination is the answer, the page is not needed.
     */
    suspend fun redirectTarget(url: String): String? = suspendCancellableCoroutine { cont ->
        val callback = object : UrlRequest.Callback() {
            override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
                request.cancel()
                if (cont.isActive) cont.resume(newLocationUrl)
            }
            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                request.cancel()
                if (cont.isActive) cont.resume(null)
            }
            override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, buf: ByteBuffer) {}
            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) { if (cont.isActive) cont.resume(null) }
            override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: CronetException) { if (cont.isActive) cont.resume(null) }
            override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) { if (cont.isActive) cont.resume(null) }
        }
        val request = try {
            engine.newUrlRequestBuilder(url, callback, executor).apply {
                BrowserHeaders.headers(BrowserHeaders.Kind.Page, BuildConfig.CRONET_VERSION, acceptLanguage()).forEach { (k, v) -> addHeader(k, v) }
            }.build()
        } catch (t: Throwable) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        cont.invokeOnCancellation { request.cancel() }
        request.start()
    }

    private companion object {
        const val TAG = "CorkboardHttp"
    }
}
