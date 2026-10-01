package app.corkboard.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import app.corkboard.data.Images
import app.corkboard.data.Listing
import app.corkboard.data.ListingStatus
import app.corkboard.data.Units
import app.corkboard.ui.Format
import app.corkboard.ui.theme.PriceFont

@Composable
private fun Photo(listing: Listing, modifier: Modifier) {
    val id = listing.imageIds.firstOrNull()
    // A whole photo sits on nothing: the space around it is just the page, not a gray frame.
    val framed = id == null || !LocalPhotoFit.current.results
    Box(if (framed) modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh) else modifier, contentAlignment = Alignment.Center) {
        if (id == null) {
            Icon(Icons.Outlined.ImageNotSupported, null, tint = MaterialTheme.colorScheme.outline)
        } else {
            AsyncImage(model = Images.url(id, Images.THUMB), contentDescription = null, contentScale = if (LocalPhotoFit.current.results) ContentScale.Fit else ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Heart(on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    // A small bounce when it turns on, so the tap visibly lands.
    val pop by animateFloatAsState(if (on) 1f else 0.85f, spring(dampingRatio = 0.35f, stiffness = 500f), label = "heart")
    Box(
        modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (on) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            if (on) "Remove from favorites" else "Add to favorites",
            tint = if (on) Color(0xFFFF6B6B) else Color.White,
            modifier = Modifier.size(22.dp).scale(pop),
        )
    }
}

@Composable
private fun Caption(listing: Listing, units: Units, note: String?, status: ListingStatus? = null) {
    val price = status?.priceNow ?: Format.price(listing)
    if (status?.gone == true) Text(
        "No longer listed",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.padding(bottom = 2.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 8.dp, vertical = 2.dp),
    )
    if (price != null) Text(price, style = MaterialTheme.typography.titleLarge, fontFamily = PriceFont)
    status?.priceWas?.let { Text("was $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary) }
    Text(listing.title.orEmpty(), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    val facts = Format.facts(listing, units)
    if (facts.isNotEmpty()) Text(facts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    Text(
        listOf(Format.ago(listing.postedAt), listing.place).filter { it.isNotBlank() }.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (!note.isNullOrBlank()) Text(
        note,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A result as a photo-first tile, two to a row. */
@Composable
fun ListingTile(listing: Listing, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit, modifier: Modifier = Modifier, units: Units = Units(), note: String? = null, onLongClick: (() -> Unit)? = null) {
    Column(modifier.clip(RoundedCornerShape(24.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box {
            Photo(listing, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)))
            Heart(favorite, Modifier.align(Alignment.TopEnd).padding(6.dp), onFavorite)
        }
        Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)) { Caption(listing, units, note) }
    }
}

/** A result as a compact row: more per screen, for categories where the title matters most. */
@Composable
fun ListingRow(listing: Listing, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit, modifier: Modifier = Modifier, units: Units = Units(), note: String? = null, onLongClick: (() -> Unit)? = null, status: ListingStatus? = null) {
    Row(modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Photo(listing, Modifier.size(104.dp).clip(RoundedCornerShape(20.dp)))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Caption(listing, units, note, status) }
        Icon(
            if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            if (favorite) "Remove from favorites" else "Add to favorites",
            tint = if (favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onFavorite).padding(8.dp),
        )
    }
}
