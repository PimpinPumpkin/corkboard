package app.corkboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

    fun x(lon: Double, zoom: Int = ZOOM): Double = (lon + 180.0) / 360.0 * (1 shl zoom)
    fun y(lat: Double, zoom: Int = ZOOM): Double = (1.0 - asinh(tan(lat * PI / 180.0)) / PI) / 2.0 * (1 shl zoom)

    /** craigslist serves its own OpenStreetMap tiles from ten interchangeable hosts. */
    fun url(x: Int, y: Int, zoom: Int = ZOOM): String = "https://map${(x + y).mod(10)}.craigslist.org/t09/$zoom/$x/$y.png"
}

/**
 * A small, still map around a listing, drawn from the same tiles the site's own listing page
 * uses, so it needs no maps app and talks to nobody new. The circle is wide on purpose: the
 * site's coordinates are a neighborhood, not an address.
 */
@Composable
fun MiniMap(lat: Double, lon: Double, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tile = 128.dp
    BoxWithConstraints(
        modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClickLabel = "Open in a maps app", onClick = onClick),
    ) {
        val fx = Tiles.x(lon)
        val fy = Tiles.y(lat)
        val halfW = (maxWidth / tile) / 2.0
        val halfH = (maxHeight / tile) / 2.0
        val max = (1 shl Tiles.ZOOM) - 1
        for (tx in floor(fx - halfW).toInt()..ceil(fx + halfW).toInt() - 1) {
            for (ty in floor(fy - halfH).toInt()..ceil(fy + halfH).toInt() - 1) {
                if (ty !in 0..max) continue
                AsyncImage(
                    model = Tiles.url(tx.mod(max + 1), ty),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.size(tile).offset(x = maxWidth / 2 + tile * (tx - fx).toFloat(), y = maxHeight / 2 + tile * (ty - fy).toFloat()),
                )
            }
        }
        Box(
            Modifier.align(Alignment.Center).size(72.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
        )
        Text(
            "© craigslist · map data © OpenStreetMap",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black.copy(alpha = 0.75f),
            modifier = Modifier.align(Alignment.BottomEnd).background(Color.White.copy(alpha = 0.7f)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
