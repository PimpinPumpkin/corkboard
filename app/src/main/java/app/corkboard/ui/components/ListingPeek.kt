package app.corkboard.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import app.corkboard.data.Images
import app.corkboard.data.Listing
import app.corkboard.data.Units
import app.corkboard.ui.Format
import app.corkboard.ui.theme.PriceFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What pressing and holding a listing brings up: the listing lifted out of the list as a larger
 * preview, its photos swipeable, with a row of quick actions floating over it the way reactions
 * float over a message. Tapping the preview opens the listing; tapping anywhere else puts it back.
 */
@Composable
fun ListingPeek(
    listing: Listing,
    favorite: Boolean,
    note: String?,
    units: Units,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onLists: () -> Unit,
    onHide: () -> Unit,
    onShare: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // The card springs up first; the actions pop out of it a beat later.
        val card = remember { Animatable(0.86f) }
        val actions = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            launch { card.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 420f)) }
            delay(70)
            actions.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 520f))
        }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .systemBarsPadding().padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    Modifier
                        .graphicsLayer { scaleX = actions.value; scaleY = actions.value; alpha = actions.value.coerceIn(0f, 1f); transformOrigin = TransformOrigin(0.5f, 1f) }
                        .clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Action(if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, if (favorite) "Unlike" else "Like", if (favorite) Color(0xFFE5484D) else null, onFavorite)
                    Action(Icons.AutoMirrored.Outlined.PlaylistAdd, "Lists", null) { onDismiss(); onLists() }
                    Action(Icons.Outlined.VisibilityOff, "Hide", null) { onDismiss(); onHide() }
                    Action(Icons.Outlined.Share, "Share", null) { onDismiss(); onShare() }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .graphicsLayer { scaleX = card.value; scaleY = card.value }
                        .fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface)
                        .clickable { onDismiss(); onOpen() },
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                        if (listing.imageIds.isEmpty()) {
                            Icon(Icons.Outlined.ImageNotSupported, null, tint = MaterialTheme.colorScheme.outline)
                        } else {
                            val pager = rememberPagerState { listing.imageIds.size }
                            HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                                AsyncImage(model = Images.url(listing.imageIds[i], Images.MEDIUM), contentDescription = null, contentScale = if (LocalPhotoFit.current.listing) ContentScale.Fit else ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            if (listing.imageIds.size > 1) Text(
                                "${pager.currentPage + 1} / ${listing.imageIds.size}",
                                color = Color.White, style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Format.price(listing)?.let { Text(it, style = MaterialTheme.typography.headlineMedium, fontFamily = PriceFont) }
                        Text(listing.title.orEmpty(), style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        val line = listOf(Format.facts(listing, units), Format.ago(listing.postedAt), listing.place).filter { it.isNotBlank() }.joinToString(" · ")
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        if (!note.isNullOrBlank()) Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                        Text("Tap to open", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, tint: Color?, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(26.dp), tint = tint ?: MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
