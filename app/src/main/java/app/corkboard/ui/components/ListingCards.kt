package app.corkboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import app.corkboard.ui.Format

@Composable
private fun Photo(listing: Listing, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        val id = listing.imageIds.firstOrNull()
        if (id == null) {
            Icon(Icons.Outlined.ImageNotSupported, null, tint = MaterialTheme.colorScheme.outline)
        } else {
            AsyncImage(model = Images.url(id, Images.THUMB), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Heart(on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (on) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            if (on) "Remove from favorites" else "Add to favorites",
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun Caption(listing: Listing) {
    val price = Format.price(listing)
    if (price != null) Text(price, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(listing.title.orEmpty(), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    val facts = Format.facts(listing)
    if (facts.isNotEmpty()) Text(facts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    Text(
        listOf(Format.ago(listing.postedAt), listing.place).filter { it.isNotBlank() }.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A result as a photo-first tile, two to a row. */
@Composable
fun ListingTile(listing: Listing, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        Box {
            Photo(listing, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)))
            Heart(favorite, Modifier.align(Alignment.TopEnd).padding(6.dp), onFavorite)
        }
        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) { Caption(listing) }
    }
}

/** A result as a compact row: more per screen, for categories where the title matters most. */
@Composable
fun ListingRow(listing: Listing, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Photo(listing, Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Caption(listing) }
        Icon(
            if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            if (favorite) "Remove from favorites" else "Add to favorites",
            tint = if (favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onFavorite).padding(8.dp),
        )
    }
}
