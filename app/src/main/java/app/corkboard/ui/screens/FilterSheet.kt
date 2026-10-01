package app.corkboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.corkboard.data.ClApi
import app.corkboard.data.Filter
import app.corkboard.data.SearchQuery
import app.corkboard.ui.components.SoftField
import kotlinx.coroutines.delay

/**
 * The filters for one category, drawn from the description the site sent with the results. The
 * sheet edits a draft; nothing is searched until Apply.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(filters: List<Filter>, query: SearchQuery, api: ClApi, onApply: (SearchQuery) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(query) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Text("Filters", style = MaterialTheme.typography.headlineMedium)
                // Consecutive on/off switches read better as one group of chips than as one row each.
                val groups = remember(filters) {
                    val out = mutableListOf<List<Filter>>()
                    for (f in filters) {
                        val last = out.lastOrNull()
                        if (f is Filter.Toggle && last != null && last.first() is Filter.Toggle) out[out.lastIndex] = last + f else out += listOf(f)
                    }
                    out
                }
                groups.forEach { group ->
                    when (val f = group.first()) {
                        is Filter.Toggle -> {
                            Label("Options")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                group.filterIsInstance<Filter.Toggle>().forEach { t ->
                                    val on = draft.params[t.name]?.isNotEmpty() == true
                                    FilterChip(selected = on, onClick = { draft = draft.with(t.name, if (on) emptyList() else listOf(t.value)) }, label = { Text(t.label) })
                                }
                            }
                        }
                        is Filter.Range -> {
                            Label(f.label)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                NumberField("min", f.prefix, draft.params[f.minName]?.firstOrNull().orEmpty(), Modifier.weight(1f)) { draft = draft.with(f.minName, listOf(it)) }
                                NumberField("max", f.prefix, draft.params[f.maxName]?.firstOrNull().orEmpty(), Modifier.weight(1f)) { draft = draft.with(f.maxName, listOf(it)) }
                            }
                        }
                        is Filter.Select -> {
                            Label(f.label)
                            val current = draft.params[f.name]?.firstOrNull() ?: f.options.first().value
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                f.options.forEachIndexed { i, o ->
                                    // The first option is always the "anything" choice: picking it clears the filter.
                                    FilterChip(selected = o.value == current, onClick = { draft = draft.with(f.name, if (i == 0) emptyList() else listOf(o.value)) }, label = { Text(o.label) })
                                }
                            }
                        }
                        is Filter.Multi -> {
                            Label(f.label)
                            val chosen = draft.params[f.name].orEmpty()
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                f.options.forEach { o ->
                                    val on = o.value in chosen
                                    FilterChip(selected = on, onClick = { draft = draft.with(f.name, if (on) chosen - o.value else chosen + o.value) }, label = { Text(o.label) })
                                }
                            }
                        }
                        is Filter.Text -> {
                            Label(f.label)
                            SuggestField(f, draft.params[f.name]?.firstOrNull().orEmpty(), api) { draft = draft.with(f.name, listOf(it)) }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Surface(tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Clearing keeps what is not in this sheet: the search text, the sort order and the place.
                    TextButton(onClick = { draft = draft.copy(params = draft.params.filterKeys { it in SearchQuery.OWN_UI }) }) { Text("Clear all") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { onApply(draft) }, modifier = Modifier.height(52.dp)) { Text("Show results", style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun NumberField(placeholder: String, prefix: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    SoftField(
        value = value,
        onValueChange = { s -> onChange(s.filter { it.isDigit() }.take(9)) },
        modifier = modifier,
        label = placeholder,
        prefix = prefix,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

/** A text filter. When the site offers completions for it (make and model), they appear underneath. */
@Composable
private fun SuggestField(f: Filter.Text, value: String, api: ClApi, onChange: (String) -> Unit) {
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var picked by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        suggestions = emptyList()
        if (f.autocomplete == null || value.length < 2 || value == picked) return@LaunchedEffect
        // Wait for a pause in typing: one request per word, not per letter.
        delay(350)
        suggestions = runCatching { api.suggest(f.autocomplete, value) }.getOrDefault(emptyList()).take(6)
    }
    SoftField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), label = "Any")
    suggestions.forEach { s ->
        Text(
            s,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().clickable { picked = s; onChange(s) }.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
