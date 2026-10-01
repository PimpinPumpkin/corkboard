package app.corkboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.tan

/** Where a point falls on the standard web map grid, in fractional tiles at a zoom level. */
object Tiles {
    const val ZOOM = 14

    /** The range craigslist's tile servers cover usefully: a continent down to a few streets. */
    const val MIN_ZOOM = 3
    const val MAX_ZOOM = 17

    fun x(lon: Double, zoom: Int = ZOOM): Double = (lon + 180.0) / 360.0 * (1 shl zoom)
    fun y(lat: Double, zoom: Int = ZOOM): Double = (1.0 - asinh(tan(lat * PI / 180.0)) / PI) / 2.0 * (1 shl zoom)

    /** craigslist serves its own OpenStreetMap tiles from ten interchangeable hosts. */
    fun url(x: Int, y: Int, zoom: Int = ZOOM): String = "https://map${(x + y).mod(10)}.craigslist.org/t09/$zoom/$x/$y.png"
}

/**
 * A small map around a listing that can be dragged and pinched like the one on the site's own
 * listing page, drawn from the same tiles, so it needs no maps app and talks to nobody new. The
 * circle is wide on purpose: the site's coordinates are a neighborhood, not an address.
 */
@Composable
fun MiniMap(lat: Double, lon: Double, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    // The view: a center in world coordinates (0..1 on both axes), a whole zoom level for which
    // tiles to fetch, and a stretch between 1 and 2 so pinching is smooth between levels.
    var zoom by remember(lat, lon) { mutableIntStateOf(Tiles.ZOOM) }
    var stretch by remember(lat, lon) { mutableFloatStateOf(1f) }
    var cx by remember(lat, lon) { mutableDoubleStateOf(Tiles.x(lon, 0)) }
    var cy by remember(lat, lon) { mutableDoubleStateOf(Tiles.y(lat, 0)) }

    fun zoomBy(factor: Float) {
        var s = stretch * factor
        var z = zoom
        while (s >= 2f && z < Tiles.MAX_ZOOM) { z++; s /= 2f }
        while (s < 1f && z > Tiles.MIN_ZOOM) { z--; s *= 2f }
        zoom = z
        stretch = s.coerceIn(1f, if (z == Tiles.MAX_ZOOM) 1f else 2f)
    }

    BoxWithConstraints(
        modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pointerInput(lat, lon) {
                detectTransformGestures { _, pan, change, _ ->
                    zoomBy(change)
                    val world = 128.dp.toPx() * stretch * (1 shl zoom)
                    cx = (cx - pan.x / world).mod(1.0)
                    cy = (cy - pan.y / world).coerceIn(0.0, 1.0)
                }
            },
    ) {
        val tile = 128.dp * stretch
        val n = 1 shl zoom
        val fx = cx * n
        val fy = cy * n
        val halfW = (maxWidth / tile) / 2.0
        val halfH = (maxHeight / tile) / 2.0
        for (tx in floor(fx - halfW).toInt()..ceil(fx + halfW).toInt() - 1) {
            for (ty in floor(fy - halfH).toInt()..ceil(fy + halfH).toInt() - 1) {
                if (ty !in 0 until n) continue
                // Keyed, so a tile keeps its image while the map moves under the finger.
                key(zoom, tx, ty) {
                    AsyncImage(
                        model = Tiles.url(tx.mod(n), ty, zoom),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        // A hair larger than its slot, so rounding never leaves a seam between tiles.
                        modifier = Modifier.size(tile + 1.dp).offset(x = maxWidth / 2 + tile * (tx - fx).toFloat(), y = maxHeight / 2 + tile * (ty - fy).toFloat()),
                    )
                }
            }
        }
        // The listing's spot. The circle covers the same ground at every zoom, down to a dot.
        val ring = (72.dp * stretch * Math.pow(2.0, (zoom - Tiles.ZOOM).toDouble()).toFloat()).coerceIn(14.dp, 400.dp)
        Box(
            Modifier.size(ring)
                .offset(x = maxWidth / 2 + tile * (Tiles.x(lon, zoom) - fx).toFloat() - ring / 2, y = maxHeight / 2 + tile * (Tiles.y(lat, zoom) - fy).toFloat() - ring / 2)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
        )
        Column(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MapButton(Icons.Outlined.Add, "Zoom in") { zoomBy(2f) }
            MapButton(Icons.Outlined.Remove, "Zoom out") { zoomBy(0.5f) }
            MapButton(Icons.AutoMirrored.Outlined.OpenInNew, "Open in a maps app", onOpen)
        }
        Text(
            "© craigslist · map data © OpenStreetMap",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black.copy(alpha = 0.75f),
            modifier = Modifier.align(Alignment.BottomEnd).background(Color.White.copy(alpha = 0.7f)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun MapButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.Black.copy(alpha = 0.8f), modifier = Modifier.size(20.dp)) }
}
