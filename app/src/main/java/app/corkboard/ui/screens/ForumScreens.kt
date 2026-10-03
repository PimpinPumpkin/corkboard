package app.corkboard.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.corkboard.data.ClApi
import app.corkboard.data.Forum
import app.corkboard.data.ForumPost
import app.corkboard.data.ForumThread
import app.corkboard.data.ForumUrls
import app.corkboard.data.Store
import app.corkboard.data.ThreadLayout
import app.corkboard.ui.Format
import app.corkboard.ui.ForumState
import app.corkboard.ui.ThreadState
import app.corkboard.ui.components.Panel
import app.corkboard.ui.components.RefreshBox
import app.corkboard.ui.components.SearchPill
import app.corkboard.ui.components.SectionTitle
import kotlinx.coroutines.CancellationException

/** The forum list, read once per run of the app: it changes about once a decade. */
private object ForumList {
    var forums: List<Forum>? = null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumsScreen(api: ClApi, store: Store, onBack: () -> Unit, onOpen: (Forum) -> Unit) {
    val pinned by store.pinnedForums.collectAsStateWithLifecycle()
    var forums by remember { mutableStateOf(ForumList.forums) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(attempt) {
        if (forums != null) return@LaunchedEffect
        error = null
        try {
            forums = api.forums().also { ForumList.forums = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Could not load the forums"
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Forums") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }) },
    ) { pad ->
        val shown = forums.orEmpty().filter { filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true) }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 32.dp)) {
            item { SearchPill(value = filter, onValueChange = { filter = it }, placeholder = "Find a forum", onSearch = {}) }
            if (pinned.isNotEmpty() && filter.isBlank()) item(key = "pinned") {
                SectionTitle("Pinned")
                Panel(Modifier.animateContentSize(spring(dampingRatio = 0.85f, stiffness = 380f))) {
                    pinned.forEach { f -> ForumRow(f, pinned = true, onPin = { store.togglePinnedForum(f) }) { onOpen(f) } }
                }
            }
            item(key = "all") {
                SectionTitle(if (filter.isBlank()) "All forums" else "Matching forums")
                when {
                    forums == null && error == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    forums == null -> Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { attempt++ }) { Text("Try again") }
                    }
                    shown.isEmpty() -> Text("No forum is called that.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
                    else -> Panel { shown.forEach { f -> ForumRow(f, pinned = pinned.any { it.id == f.id }, onPin = { store.togglePinnedForum(f) }) { onOpen(f) } } }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ForumRow(f: Forum, pinned: Boolean, onPin: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onPin).padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(f.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onPin) {
            Icon(
                if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                if (pinned) "Unpin ${f.name}" else "Pin ${f.name}",
                Modifier.size(20.dp).rotate(if (pinned) 0f else 35f),
                tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumScreen(state: ForumState, onBack: () -> Unit, onOpen: (ForumThread) -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.forum.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = { OverflowMenu(listOf("Open on craigslist" to { openInBrowser(context, ForumUrls.forumWeb(state.forum.id)) })) },
            )
        },
        bottomBar = {
            BottomAction("New thread on craigslist", Icons.Outlined.Edit) { openInBrowser(context, ForumUrls.compose(state.forum.id)) }
        },
    ) { pad ->
        // The next page loads as the end of the list comes into view.
        val nearEnd by remember { derivedStateOf { (state.list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.threads.size - 4 } }
        LaunchedEffect(nearEnd, state.threads.size) { if (nearEnd && state.threads.isNotEmpty()) state.more() }

        RefreshBox(state.refreshing, state::refresh, Modifier.padding(top = pad.calculateTopPadding())) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = state.list,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = pad.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.threads, key = { it.root.id }) { t -> ThreadCard(t) { onOpen(t) } }
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        when {
                            state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = { if (state.threads.isEmpty()) state.refresh() else state.more() }) { Text("Try again") }
                            }
                            state.loading && !state.refreshing -> CircularProgressIndicator()
                            state.older == null && state.threads.isNotEmpty() -> Text("That is everything in this forum.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadCard(t: ForumThread, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(t.root.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val who = handleColor(t.root.handle, op = true)
            val replies = when (t.replies) {
                0 -> "no replies yet"
                1 -> "1 reply"
                else -> "${t.replies} replies" + if (t.people > 2) " from ${t.people - 1} people" else ""
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = who)) { append(t.root.handle ?: "anonymous") }
                    append(" · $replies")
                },
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text(Format.ago(t.lastAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(state: ThreadState, onBack: () -> Unit) {
    val context = LocalContext.current
    val root = state.rows.firstOrNull()?.post
    val forum = state.forum
    val web = ForumUrls.web(state.rootId)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val people = state.posts.map { it.handle }.distinct().size
                        if (state.rows.isNotEmpty()) Text(
                            listOfNotNull(forum?.name, "${state.rows.size - 1} ${if (state.rows.size == 2) "reply" else "replies"}", "$people ${if (people == 1) "person" else "people"}").joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, web).putExtra(Intent.EXTRA_SUBJECT, state.title), null))
                    }) { Icon(Icons.Outlined.Share, "Share") }
                    OverflowMenu(listOf("Open on craigslist" to { openInBrowser(context, web) }))
                },
            )
        },
        bottomBar = {
            if (root != null) BottomAction("Reply on craigslist") { openInBrowser(context, forum?.let { ForumUrls.reply(it.id, root.id) } ?: web) }
        },
    ) { pad ->
        RefreshBox(state.refreshing, state::refresh, Modifier.padding(top = pad.calculateTopPadding())) {
            val visible = remember(state.rows, state.folded) { ThreadLayout.visible(state.rows, state.folded) }
            LazyColumn(Modifier.fillMaxSize(), state = state.list, contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
                items(visible, key = { it.post.id }) { row ->
                    PostRow(row, state, opHandle = root?.handle, isRoot = row.post.id == root?.id, forum = forum)
                }
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        when {
                            state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = state::refresh) { Text("Try again") }
                            }
                            state.loading && state.rows.isEmpty() -> CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

/**
 * One post. The lines down its left edge are the branches it sits in; tapping one folds that
 * branch. Tapping the post opens a long body out in full, or unfolds the branch it heads.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostRow(row: ThreadLayout.Row, state: ThreadState, opHandle: String?, isRoot: Boolean, forum: Forum?) {
    val context = LocalContext.current
    val post = row.post
    val folded = row.startsBranch && post.id in state.folded
    var open by rememberSaveable(post.id) { mutableStateOf(isRoot) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(post.id) { state.body(post) }
    val body = state.bodies[post.id]
    val colors = MaterialTheme.colorScheme

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = 12.dp)) {
        row.branches.forEachIndexed { level, branch ->
            Box(
                Modifier.width(20.dp).fillMaxHeight().clickable(onClickLabel = "Fold this branch") { state.toggle(branch) },
                contentAlignment = Alignment.TopCenter,
            ) {
                // A branch's line starts a little below its first post's top edge, so branches read as separate.
                Box(
                    Modifier.padding(top = if (branch == post.id) 12.dp else 0.dp).width(2.dp).fillMaxHeight().clip(CircleShape)
                        .background(if (branch in state.folded) colors.primary else railColor(level)),
                )
            }
        }
        Box(Modifier.weight(1f)) {
            Column(
                Modifier.fillMaxWidth()
                    .combinedClickable(
                        onClick = { if (folded) state.toggle(post.id) else open = !open },
                        onLongClick = { menu = true },
                    )
                    .padding(start = 8.dp, end = 16.dp, top = if (row.startsBranch) 12.dp else 6.dp, bottom = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val op = post.handle != null && post.handle == opHandle
                    Text(post.handle ?: "anonymous", style = MaterialTheme.typography.labelLarge, color = handleColor(post.handle, op), maxLines = 1)
                    if (op) Text(
                        "started it",
                        style = MaterialTheme.typography.labelSmall, color = colors.onPrimaryContainer,
                        modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(6.dp)).background(colors.primaryContainer).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                    // Who a reply answers, when that post is not the one just above it and not the thread's first.
                    Text(
                        row.replyTo?.takeIf { it.depth > 0 }?.let { "to ${it.handle ?: "anonymous"}" }.orEmpty(),
                        style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 6.dp),
                    )
                    Text(Format.ago(post.postedAt), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    post.title,
                    style = if (isRoot) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                )
                if (post.hasBody && !folded) {
                    when {
                        body == null -> Box(Modifier.padding(top = 6.dp).fillMaxWidth(0.6f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(colors.surfaceContainerHigh))
                        body.isNotEmpty() -> Text(
                            linked(body, colors.primary),
                            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                            maxLines = if (open) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp).animateContentSize(),
                        )
                    }
                }
                if (folded) Text(
                    if (row.hidden == 1) "1 more reply" else "${row.hidden} more replies",
                    style = MaterialTheme.typography.labelLarge, color = colors.primary,
                    modifier = Modifier.padding(top = 6.dp).clip(CircleShape).background(colors.secondaryContainer).padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Reply on craigslist") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Reply, null) },
                    onClick = { menu = false; openInBrowser(context, forum?.let { ForumUrls.reply(it.id, post.id) } ?: ForumUrls.web(post.id)) },
                )
                DropdownMenuItem(text = { Text("Copy text") }, onClick = {
                    menu = false
                    val text = listOfNotNull(post.title, body?.takeIf { it.isNotEmpty() }).joinToString("\n\n")
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(post.title, text))
                })
                row.branches.lastOrNull()?.let { branch ->
                    DropdownMenuItem(text = { Text(if (branch in state.folded) "Unfold this branch" else "Fold this branch") }, onClick = { menu = false; state.toggle(branch) })
                }
                DropdownMenuItem(text = { Text("Open on craigslist") }, onClick = { menu = false; openInBrowser(context, ForumUrls.web(post.id)) })
            }
        }
    }
}

/** A post's text with its web addresses made tappable. */
private fun linked(text: String, color: Color): AnnotatedString = buildAnnotatedString {
    val style = TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))
    var at = 0
    for (m in Regex("""(?:https?://|www\.)[^\s<>"]+[^\s<>".,;:!?)\]]""").findAll(text)) {
        append(text.substring(at, m.range.first))
        val url = if (m.value.startsWith("www.")) "https://${m.value}" else m.value
        withLink(LinkAnnotation.Url(url, style)) { append(m.value) }
        at = m.range.last + 1
    }
    append(text.substring(at))
}

/**
 * A handle's color: the same handle is always the same color, so a person can be followed down a
 * thread. The thread's starter gets the theme's own color; anonymous posts stay plain, because
 * "anonymous" is everyone and nobody.
 */
@Composable
private fun handleColor(handle: String?, op: Boolean): Color {
    val colors = MaterialTheme.colorScheme
    if (handle == null) return colors.onSurfaceVariant
    if (op) return colors.primary
    val dark = colors.surface.luminance() < 0.5f
    val hues = floatArrayOf(15f, 40f, 95f, 150f, 185f, 220f, 280f, 320f)
    val hue = hues[(handle.hashCode() and 0x7fffffff) % hues.size]
    return Color.hsl(hue, if (dark) 0.5f else 0.55f, if (dark) 0.72f else 0.38f)
}

@Composable
private fun railColor(level: Int): Color = MaterialTheme.colorScheme.outline.copy(alpha = if (level % 2 == 0) 0.55f else 0.4f)

@Composable
private fun OverflowMenu(items: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, "More") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (label, action) -> DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() }) }
        }
    }
}

@Composable
private fun BottomAction(label: String, icon: ImageVector = Icons.AutoMirrored.Outlined.Reply, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp)) {
            Icon(icon, null, Modifier.size(18.dp))
            Text(label, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
