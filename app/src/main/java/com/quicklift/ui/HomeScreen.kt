package com.quicklift.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quicklift.data.MuscleGroup
import com.quicklift.data.title
import com.quicklift.data.workoutTitle
import com.quicklift.export.CsvExporter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val historyFormat = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(state: UiState, vm: WorkoutViewModel, snackbar: SnackbarHostState) {
    val recent by vm.recentWorkouts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }

    val export: () -> Unit = {
        if (!exporting) scope.launch {
            exporting = true
            try {
                val csv = vm.buildExportCsv()
                if (csv == null) vm.showMessage("Nothing to export yet") else CsvExporter.share(context, csv)
            } catch (e: Exception) {
                vm.showMessage("Export failed: ${e.message}")
            } finally {
                exporting = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QuickLift", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = export, enabled = !exporting) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Export CSV")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.activeWorkout?.let { active ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Workout in progress", style = MaterialTheme.typography.labelLarge)
                                Text(active.title(), style = MaterialTheme.typography.titleMedium)
                            }
                            Button(onClick = vm::resumeWorkout) { Text("Resume") }
                        }
                    }
                }
            }

            item {
                Column {
                    Text("Primary muscle groups", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Pick two",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        MuscleGroup.entries.forEach { group ->
                            val selected = group in state.selectedGroups
                            FilterChip(
                                selected = selected,
                                onClick = { vm.toggleGroup(group) },
                                label = { Text(group.label, style = MaterialTheme.typography.titleSmall) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                                } else null,
                                modifier = Modifier.height(44.dp),
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ToggleRow("Abs", state.includeAbs, vm::setAbs)
                    HorizontalDivider()
                    ToggleRow("Run", state.includeRun, vm::setRun)
                }
            }

            item {
                Button(
                    onClick = vm::startWorkout,
                    enabled = state.canStart,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Start Workout", style = MaterialTheme.typography.titleMedium)
                }
            }

            if (recent.isNotEmpty()) {
                item {
                    Text(
                        "Recent workouts",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(recent, key = { it.id }) { w ->
                    ListItem(
                        headlineContent = { Text(workoutTitle(w.muscleGroups, w.includeAbs, w.includeRun)) },
                        supportingContent = {
                            Text(historyFormat.format(Instant.ofEpochMilli(w.startedAt).atZone(ZoneId.systemDefault())))
                        },
                        trailingContent = { Text("${w.setCount} sets") },
                    )
                }
                item {
                    OutlinedButton(onClick = export, enabled = !exporting, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Export all history as CSV")
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.titleMedium) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.fillMaxWidth(),
    )
}
