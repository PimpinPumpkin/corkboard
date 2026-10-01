package app.corkboard.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Where the phone is, roughly, from the platform's own location service. No Google services: on a
 * phone without them this is the network or GPS provider, whichever the system has. Only the
 * approximate permission is asked for, because a town is all a classifieds search needs.
 */
object DeviceLocation {
    private const val FRESH_MS = 6 * 60 * 60 * 1000L

    /** A recent fix if the system has one, otherwise a new one, or null after [timeoutMs]. */
    @SuppressLint("MissingPermission")
    suspend fun get(context: Context, timeoutMs: Long = 20_000): Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        val now = System.currentTimeMillis()
        providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { now - it.time < FRESH_MS }
            .maxByOrNull { it.time }
            ?.let { return it }
        // Cheapest first: a network fix is plenty and arrives without a view of the sky.
        val order = listOf(LocationManager.NETWORK_PROVIDER, "fused", LocationManager.GPS_PROVIDER).filter { it in providers }
        if (Build.VERSION.SDK_INT < 30 || order.isEmpty()) return null
        return withTimeoutOrNull(timeoutMs) {
            for (provider in order) {
                val fix = current(manager, context, provider)
                if (fix != null) return@withTimeoutOrNull fix
            }
            null
        }
    }

    @SuppressLint("MissingPermission", "NewApi")
    private suspend fun current(manager: LocationManager, context: Context, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            runCatching {
                manager.getCurrentLocation(provider, signal, context.mainExecutor) { if (cont.isActive) cont.resume(it) }
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }
}
