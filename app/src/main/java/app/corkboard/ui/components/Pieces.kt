package app.corkboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * The search bar: a filled pill, no outline, the way the system's own search fields look.
 * [trailing] sits at the right end when the field is empty (a settings gear on the home screen).
 */
@Composable
fun SearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Search,
    leading: ImageVector = Icons.Outlined.Search,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(CircleShape).background(colors.surfaceContainerHigh).padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(leading, null, tint = colors.onSurfaceVariant)
        Box(Modifier.weight(1f).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyLarge).copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }, onDone = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) IconButton(onClick = { onValueChange(""); onClear?.invoke() }) { Icon(Icons.Outlined.Close, "Clear", tint = colors.onSurfaceVariant) }
        else trailing?.invoke()
    }
}

/** A rounded, tinted panel that groups related rows or facts, the way a settings page does. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer).then(modifier), content = content)
}

/** A text box in the app's own style: filled, rounded, no underline. Used wherever something is typed. */
@Composable
fun SoftField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    prefix: String? = null,
    suffix: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val clear = androidx.compose.ui.graphics.Color.Transparent
    androidx.compose.material3.TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = label?.let { { Text(it) } },
        prefix = prefix?.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
        suffix = suffix?.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        keyboardOptions = keyboardOptions,
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = clear, unfocusedIndicatorColor = clear, disabledIndicatorColor = clear,
        ),
    )
}

/** The heading over a group: large and round, with room above it. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 28.dp, bottom = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** A round, tinted icon button: for actions that float over content or sit beside a title. */
@Composable
fun TonalIcon(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Box(modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick) { Icon(icon, label, tint = tint) }
    }
}

/**
 * Pull down to reload, with the two cues that make it feel like it did something: a tick under
 * the finger at the point where letting go will reload, and a brief wash of light over the
 * content when the new answer lands.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RefreshBox(isRefreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val view = androidx.compose.ui.platform.LocalView.current
    // The detent: felt once each time the pull crosses the point of no return, in either direction.
    val armed = state.distanceFraction >= 1f
    androidx.compose.runtime.LaunchedEffect(armed) {
        if (armed || state.distanceFraction > 0.5f) {
            view.performHapticFeedback(
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    if (armed) android.view.HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else android.view.HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE
                } else android.view.HapticFeedbackConstants.CLOCK_TICK,
            )
        }
    }
    // The flash: only when a reload finishes, never on first appearance.
    val flash = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    var wasRefreshing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(isRefreshing) {
        if (wasRefreshing && !isRefreshing) {
            view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM else android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            flash.snapTo(0.22f)
            flash.animateTo(0f, androidx.compose.animation.core.tween(450))
        }
        wasRefreshing = isRefreshing
    }
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier, state = state) {
        content()
        if (flash.value > 0f) Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = flash.value)))
    }
}

/**
 * Whether photos are shown whole, with empty space around them where their shape does not match
 * the frame, or cropped to fill it. One switch for search results, one for a listing's own page.
 */
data class PhotoFit(val results: Boolean = false, val listing: Boolean = false)

val LocalPhotoFit = androidx.compose.runtime.compositionLocalOf { PhotoFit() }
