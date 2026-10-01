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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalFocusManager
import app.corkboard.data.Archive
import app.corkboard.data.GoneException
import app.corkboard.data.Parsers
import app.corkboard.data.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

private fun openInBrowser(context: Context, url: String) {
    runCatching { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url)) }
        .onFailure { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostingScreen(opened: Listing, api: ClApi, store: Store, archive: Archive, onBack: () -> Unit) {
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

    // What the archive knows: the saved copy, shown when the listing is gone, and any price change.
    var snapshot by remember { mutableStateOf<Snapshot?>(null) }
    var gone by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var listPicker by remember { mutableStateOf(false) }
    val lists by store.lists.collectAsStateWithLifecycle()

    // Every time the listing is opened it is asked for again, so an edit or a deletion shows up.
    // (The site lets a copy be reused for five minutes; within that, this costs no request.)
    LaunchedEffect(opened.uuid, attempt) {
        error = null
        val uuid = opened.uuid
        if (uuid == null) {
            error = "This listing cannot be opened"
            return@LaunchedEffect
        }
        try {
            val body = api.postingRaw(uuid, fresh = refreshing)
            val fresh = Parsers.posting(body)
            snapshot = withContext(Dispatchers.IO) { archive.save(uuid, body, fresh.priceText) }
            gone = false
            offline = false
            posting = fresh
            // The same thing posted again: bring the heart, note and lists over from the old one.
            fresh.repostOf?.let { old ->
                store.carryOver(old, opened.copy(postingId = fresh.postingId, postedAt = fresh.postedAt, title = fresh.title, priceText = fresh.priceText, imageIds = fresh.imageIds, place = fresh.place, lat = fresh.lat, lon = fresh.lon))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Gone from the site, or no network: fall back to the copy kept from last time.
            val saved = withContext(Dispatchers.IO) { archive.load(uuid) }
            val old = saved?.let { runCatching { Parsers.posting(it.body) }.getOrNull() }
            if (old != null) {
                snapshot = saved
                gone = e is GoneException
                offline = !gone
                posting = old
            } else {
                error = e.message ?: "Could not load this listing"
            }
        } finally {
            refreshing = false
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
                    IconButton(onClick = { listPicker = true }, enabled = listing.postingId != 0L) {
                        val inAny = lists.any { l -> l.items.any { it.postingId == listing.postingId } }
                        Icon(if (inAny) Icons.AutoMirrored.Filled.PlaylistAddCheck else Icons.AutoMirrored.Outlined.PlaylistAdd, "Add to a list")
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
                    Button(onClick = { webUrl?.let { openInBrowser(context, it) } }, enabled = webUrl != null && !gone, modifier = Modifier.weight(1f).height(60.dp), shape = RoundedCornerShape(24.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.Reply, null, Modifier.size(22.dp))
                        Spacer(Modifier.size(10.dp))
                        Text("Reply on craigslist", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) { pad ->
        // Pulling down asks the site for the listing again, past the five minutes a copy may be reused.
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; attempt++ }, modifier = Modifier.fillMaxSize().padding(pad)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (gone || offline) Row(
                Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (gone) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh).clickable(enabled = offline) { attempt++ }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val onBanner = if (gone) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                val savedAgo = snapshot?.let { Format.ago(it.savedAt) } ?: "earlier"
                Icon(Icons.Outlined.Inventory2, null, tint = onBanner)
                Text(
                    if (gone) "This listing has been deleted or has expired. This is the copy saved $savedAgo."
                    else "Could not reach craigslist. This is the copy saved $savedAgo. Tap to try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onBanner,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            if (imageIds.isNotEmpty()) Gallery(imageIds, photo = { i -> opened.uuid?.let { archive.photo(it, i) } }, onOpen = { viewer = it })
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                if (price != null) Text(price, style = MaterialTheme.typography.displaySmall, fontFamily = PriceFont)
                snapshot?.priceWas?.takeIf { it != price }?.let { was ->
                    Text(
                        "was $was" + (snapshot?.priceChangedAt?.let { " · changed ${Format.ago(it)}" } ?: ""),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
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
                // The user's own note about this listing. Typed as a draft and kept only when saved;
                // it never leaves the phone.
                val notes by store.notes.collectAsStateWithLifecycle()
                val savedNote = notes[listing.postingId].orEmpty()
                var note by remember(listing.postingId, savedNote) { mutableStateOf(savedNote) }
                val focus = LocalFocusManager.current
                TextField(
                    value = note,
                    onValueChange = { note = it.take(2000) },
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
                AnimatedVisibility(visible = note.trim() != savedNote.trim()) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        TextButton(onClick = { note = savedNote; focus.clearFocus() }) { Text("Cancel") }
                        Button(onClick = { store.setNote(listing, note.trim()); focus.clearFocus() }) {
                            Text(if (note.isBlank()) "Remove note" else "Save note")
                        }
                    }
                }
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
    }

    viewer?.let { start -> PhotoViewer(imageIds, start, photo = { i -> if (gone) opened.uuid?.let { archive.photo(it, i) } else null }, onClose = { viewer = null }) }

    if (listPicker) ListPicker(listing, store, onDismiss = { listPicker = false })
}

@Composable
private fun Gallery(imageIds: List<String>, photo: (Int) -> File?, onOpen: (Int) -> Unit) {
    val pager = rememberPagerState { imageIds.size }
    Box {
        HorizontalPager(pager, Modifier.fillMaxWidth().padding(horizontal = 12.dp).aspectRatio(4f / 3f).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) { i ->
            AsyncImage(
                // The archived copy when there is one: the site deletes photos along with the listing.
                model = remember(i) { photo(i) } ?: Images.url(imageIds[i], Images.MEDIUM),
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
private fun PhotoViewer(imageIds: List<String>, start: Int, photo: (Int) -> File?, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val pager = rememberPagerState(initialPage = start) { imageIds.size }
        var zoomed by remember { mutableStateOf(false) }
        // Dragging down pulls the photo away under the finger and lets go of it: far enough or fast
        // enough and it flies off and the viewer closes, otherwise it springs back.
        val scope = rememberCoroutineScope()
        val drag = remember { Animatable(0f) }
        var height by remember { mutableIntStateOf(1) }
        val away = (kotlin.math.abs(drag.value) / (height * 0.5f)).coerceIn(0f, 1f)
        Box(
            Modifier.fillMaxSize().onSizeChanged { height = it.height.coerceAtLeast(1) }.background(Color.Black.copy(alpha = 1f - 0.85f * away))
                .draggable(
                    state = rememberDraggableState { delta -> scope.launch { drag.snapTo(drag.value + delta) } },
                    orientation = Orientation.Vertical,
                    enabled = !zoomed,
                    onDragStopped = { velocity ->
                        val leave = kotlin.math.abs(drag.value) > height * 0.18f || kotlin.math.abs(velocity) > 1800f
                        if (leave) {
                            // Keeps the speed it was thrown with on the way out.
                            val target = if (drag.value + velocity * 0.1f >= 0) height.toFloat() else -height.toFloat()
                            drag.animateTo(target, spring(dampingRatio = 1f, stiffness = 300f), initialVelocity = velocity)
                            onClose()
                        } else {
                            drag.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f), initialVelocity = velocity)
                        }
                    },
                ),
        ) {
            // While a photo is zoomed in, dragging pans it instead of turning the page.
            HorizontalPager(pager, Modifier.fillMaxSize().graphicsLayer { translationY = drag.value; val k = 1f - 0.12f * away; scaleX = k; scaleY = k }, userScrollEnabled = !zoomed) { i ->
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                LaunchedEffect(pager.currentPage) {
                    if (pager.currentPage != i) { scale = 1f; offset = Offset.Zero }
                }
                AsyncImage(
                    model = remember(i) { photo(i) } ?: Images.url(imageIds[i], Images.LARGE),
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
            IconButton(onClick = onClose, modifier = Modifier.graphicsLayer { alpha = 1f - away }.statusBarsPadding().padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f))) {
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

/** Which of the user's lists this listing is in, with a way to start a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListPicker(listing: Listing, store: Store, onDismiss: () -> Unit) {
    val lists by store.lists.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text("Add to a list", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
            lists.forEach { l ->
                val inList = l.items.any { it.postingId == listing.postingId }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { store.toggleInList(l.id, listing) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = inList, onCheckedChange = { store.toggleInList(l.id, listing) })
                    Text(l.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${l.items.size}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("New list") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                )
                Button(enabled = name.isNotBlank(), onClick = { store.toggleInList(store.createList(name).id, listing); name = "" }) { Text("Create") }
            }
        }
    }
}
