package app.corkboard.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import app.corkboard.data.Attribute
import app.corkboard.data.ClApi
import app.corkboard.data.ClUrls
import app.corkboard.data.Images
import app.corkboard.data.Listing
import app.corkboard.data.Posting
import app.corkboard.data.Store
import app.corkboard.ui.Format
import app.corkboard.ui.components.MiniMap
import app.corkboard.ui.components.Panel
import app.corkboard.ui.theme.PriceFont
import kotlinx.coroutines.CancellationException

private fun openInBrowser(context: Context, url: String) {
    runCatching { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url)) }
        .onFailure { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostingScreen(opened: Listing, api: ClApi, store: Store, onBack: () -> Unit) {
    val context = LocalContext.current
    val favorites by store.favorites.collectAsStateWithLifecycle()
    var posting by remember { mutableStateOf<Posting?>(null) }
    // A listing opened from a link arrives as nothing but its address. Once it has loaded, it is
    // filled in from the page so it can be hearted, hidden and noted like one opened from a search.
    val listing = remember(opened, posting) {
        val p = posting
        if (opened.postingId != 0L || p == null) opened
        else opened.copy(postingId = p.postingId, postedAt = p.postedAt, title = p.title, priceText = p.priceText, imageIds = p.imageIds, place = p.place, lat = p.lat, lon = p.lon)
    }
    val isFavorite = favorites.any { it.postingId == listing.postingId }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(listing.uuid, attempt) {
        error = null
        try {
            posting = api.posting(listing.uuid ?: throw IllegalStateException("This listing cannot be opened"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Could not load this listing"
        }
    }

    val p = posting
    val webUrl = p?.url?.takeIf { it.isNotEmpty() } ?: listing.uuid?.let { ClUrls.web(it, listing.slug) }
    // Until the full listing arrives, show what the search result already knew.
    val imageIds = p?.imageIds ?: listing.imageIds
    val title = p?.title ?: listing.title.orEmpty()
    val price = p?.priceText ?: Format.price(listing)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { store.toggleFavorite(listing) }) {
                        Icon(
                            if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            if (isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (webUrl != null) IconButton(onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, webUrl).putExtra(Intent.EXTRA_SUBJECT, title), null))
                    }) { Icon(Icons.Outlined.Share, "Share") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Hide this listing") }, onClick = { menu = false; store.setHidden(listing.postingId, true); onBack() })
                            if (webUrl != null) DropdownMenuItem(text = { Text("Open on craigslist") }, onClick = { menu = false; openInBrowser(context, webUrl) })
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Replying means solving the site's own check and seeing the seller's contact
                    // details, which belongs in a real browser.
                    Button(onClick = { webUrl?.let { openInBrowser(context, it) } }, enabled = webUrl != null, modifier = Modifier.weight(1f).height(60.dp), shape = RoundedCornerShape(24.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.Reply, null, Modifier.size(22.dp))
                        Spacer(Modifier.size(10.dp))
                        Text("Reply on craigslist", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            if (imageIds.isNotEmpty()) Gallery(imageIds, onOpen = { viewer = it })
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                if (price != null) Text(price, style = MaterialTheme.typography.displaySmall, fontFamily = PriceFont)
                SelectionContainer { Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 2.dp)) }
                val postedAt = p?.postedAt ?: listing.postedAt
                // Only a real edit or renewal counts. An untouched listing carries an updated time
                // equal to its posted time, or one second after it; anything later is the seller's doing.
                val updated = p?.updatedAt?.takeIf { it > postedAt + 1 }
                Text(
                    listOfNotNull(
                        (p?.place?.takeIf { it.isNotBlank() } ?: listing.place).takeIf { it.isNotBlank() },
                        "posted ${Format.ago(postedAt)}".takeIf { postedAt > 0 },
                        updated?.let { "updated ${Format.ago(it)}" },
                        "repost".takeIf { p?.repostOf != null },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                // The user's own note about this listing. Saved as it is typed; never leaves the phone.
                val notes by store.notes.collectAsStateWithLifecycle()
                var note by remember(listing.postingId) { mutableStateOf(notes[listing.postingId].orEmpty()) }
                TextField(
                    value = note,
                    onValueChange = { note = it.take(2000); store.setNote(listing, note) },
                    enabled = listing.postingId != 0L,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    placeholder = { Text("Your note") },
                    leadingIcon = { Icon(Icons.Outlined.EditNote, null) },
                    maxLines = 6,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
                when {
                    p != null -> {
                        if (p.attributes.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            // Facts in two columns, label over value, the way a spec sheet reads.
                            Panel {
                                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                                    // Long values such as a VIN get a row to themselves instead of wrapping.
                                    val rows = buildList<List<Attribute>> {
                                        var open: Attribute? = null
                                        for (a in p.attributes) {
                                            if (a.value.length > 16) { add(listOf(a)); continue }
                                            open = if (open == null) a else { add(listOf(open, a)); null }
                                        }
                                        open?.let { add(listOf(it)) }
                                    }
                                    rows.forEach { pair ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                            pair.forEach { a ->
                                                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                                    if (a.label.isNotEmpty()) Text(a.label.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    SelectionContainer { Text(a.value, style = MaterialTheme.typography.bodyLarge) }
                                                }
                                            }
                                            if (pair.size == 1 && pair[0].value.length <= 16) Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                        // Above the description: some run to pages, and where the thing is matters sooner.
                        val lat = p.lat ?: listing.lat
                        val lon = p.lon ?: listing.lon
                        if (lat != null && lon != null && (lat != 0.0 || lon != 0.0)) {
                            Spacer(Modifier.height(12.dp))
                            MiniMap(lat, lon, onOpen = {
                                // A maps app if the phone has one; otherwise OpenStreetMap in the browser.
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon"))) }
                                    .onFailure { openInBrowser(context, "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=14/$lat/$lon") }
                            })
                        }
                        Spacer(Modifier.height(12.dp))
                        val link = MaterialTheme.colorScheme.primary
                        val body = remember(p.bodyHtml, link) {
                            AnnotatedString.fromHtml(Format.bodyHtml(p.bodyHtml), linkStyles = TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline)))
                        }
                        Panel { SelectionContainer { Text(body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(20.dp)) } }
                        p.notices.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)) }
                        Text(
                            listOfNotNull(
                                "Posted ${Format.date(p.postedAt)}",
                                updated?.let { "Updated ${Format.date(it)}" },
                                p.repostOf?.let { "A repost of an earlier listing ($it)" },
                                "Posting ID ${p.postingId}",
                            ).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 20.dp, start = 4.dp),
                        )
                    }
                    error != null -> Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { attempt++ }, modifier = Modifier.padding(top = 12.dp)) { Text("Try again") }
                    }
                    else -> Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
    }

    viewer?.let { start -> PhotoViewer(imageIds, start, onClose = { viewer = null }) }
}

@Composable
private fun Gallery(imageIds: List<String>, onOpen: (Int) -> Unit) {
    val pager = rememberPagerState { imageIds.size }
    Box {
        HorizontalPager(pager, Modifier.fillMaxWidth().padding(horizontal = 12.dp).aspectRatio(4f / 3f).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) { i ->
            AsyncImage(
                model = Images.url(imageIds[i], Images.MEDIUM),
                contentDescription = "Photo ${i + 1} of ${imageIds.size}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clickable { onOpen(i) },
            )
        }
        if (imageIds.size > 1) Text(
            "${pager.currentPage + 1} / ${imageIds.size}",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 12.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Full-screen photos at the largest size the site keeps, with pinch or double tap to zoom. */
@Composable
private fun PhotoViewer(imageIds: List<String>, start: Int, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val pager = rememberPagerState(initialPage = start) { imageIds.size }
        var zoomed by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // While a photo is zoomed in, dragging pans it instead of turning the page.
            HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed) { i ->
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                LaunchedEffect(pager.currentPage) {
                    if (pager.currentPage != i) { scale = 1f; offset = Offset.Zero }
                }
                AsyncImage(
                    model = Images.url(imageIds[i], Images.LARGE),
                    contentDescription = "Photo ${i + 1} of ${imageIds.size}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            // Panning stops at the photo's edges, so it can never be dragged out of sight.
                            fun clamp(o: Offset): Offset {
                                val maxX = size.width * (scale - 1f) / 2f
                                val maxY = size.height * (scale - 1f) / 2f
                                return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
                            }
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    // Two fingers always zoom. One finger pans a zoomed photo and is
                                    // otherwise left alone, so the pager underneath can turn the page.
                                    if (event.changes.count { it.pressed } > 1 || scale > 1f) {
                                        scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                                        offset = if (scale == 1f) Offset.Zero else clamp(offset + event.calculatePan())
                                        zoomed = scale > 1f
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                scale = if (scale > 1f) 1f else 2.5f
                                offset = Offset.Zero
                                zoomed = scale > 1f
                            })
                        }
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.statusBarsPadding().padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f))) {
                Icon(Icons.Outlined.Close, "Close", tint = Color.White)
            }
            Text(
                "${pager.currentPage + 1} / ${imageIds.size}",
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
            )
        }
    }
}
