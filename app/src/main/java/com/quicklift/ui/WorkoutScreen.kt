package com.quicklift.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quicklift.data.Exercise
import com.quicklift.data.ExerciseCatalog
import com.quicklift.data.ExerciseKind
import com.quicklift.data.ExerciseSection
import com.quicklift.data.WorkoutSet
import com.quicklift.data.describeSet
import com.quicklift.data.title
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val lastTimeFormat = DateTimeFormatter.ofPattern("EEE, MMM d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(state: UiState, vm: WorkoutViewModel, snackbar: SnackbarHostState) {
    val workout = state.activeWorkout ?: return
    val sets by vm.sessionSets.collectAsStateWithLifecycle()
    val known by vm.knownExercises.collectAsStateWithLifecycle()
    var confirmFinish by remember { mutableStateOf(false) }

    val exercise = state.selectedExercise
    val onBack: () -> Unit = { if (exercise != null) vm.clearExercise() else vm.goHome() }
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            exercise?.name ?: workout.title(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (exercise != null) workout.title() else "${sets.size} sets logged",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = { TextButton(onClick = { confirmFinish = true }) { Text("Finish") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        if (exercise == null) {
            val sections = remember(state.selectedGroups, state.includeAbs, state.includeRun, known) {
                ExerciseCatalog.sectionsFor(state.selectedGroups, state.includeAbs, state.includeRun, known)
            }
            ExercisePicker(sections, sets, onPick = vm::selectExercise, modifier = contentModifier)
        } else {
            SetLogger(
                state = state,
                exercise = exercise,
                todaySets = sets.filter { it.exercise == exercise.name },
                vm = vm,
                modifier = contentModifier,
            )
        }
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish workout?") },
            text = { Text("${sets.size} sets logged.") },
            confirmButton = {
                TextButton(onClick = { confirmFinish = false; vm.finishWorkout() }) { Text("Finish") }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Keep going") } },
        )
    }
}

// ---------------------------------------------------------------------------
// Exercise picker
// ---------------------------------------------------------------------------

@Composable
private fun ExercisePicker(
    sections: List<ExerciseSection>,
    sessionSets: List<WorkoutSet>,
    onPick: (Exercise) -> Unit,
    modifier: Modifier = Modifier,
) {
    val countByExercise = remember(sessionSets) { sessionSets.groupingBy { it.exercise }.eachCount() }
    var customName by rememberSaveable { mutableStateOf("") }

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
        sections.forEach { section ->
            item(key = "header-${section.title}") {
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(section.exercises, key = { "${section.title}-${it.name}" }) { ex ->
                val done = countByExercise[ex.name] ?: 0
                ListItem(
                    headlineContent = { Text(ex.name) },
                    trailingContent = if (done > 0) {
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("$done", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else null,
                    modifier = Modifier.clickable { onPick(ex) },
                )
            }
        }
        item(key = "custom") {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Other exercise",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it.take(40) },
                        placeholder = { Text("e.g. Hack Squat") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(onGo = {
                            val name = customName.trim()
                            if (name.isNotEmpty()) { onPick(ExerciseCatalog.find(name)); customName = "" }
                        }),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val name = customName.trim()
                            if (name.isNotEmpty()) { onPick(ExerciseCatalog.find(name)); customName = "" }
                        },
                        enabled = customName.isNotBlank(),
                    ) { Text("Log") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Set logger
// ---------------------------------------------------------------------------

@Composable
private fun SetLogger(
    state: UiState,
    exercise: Exercise,
    todaySets: List<WorkoutSet>,
    vm: WorkoutViewModel,
    modifier: Modifier = Modifier,
) {
    val cardio = exercise.kind == ExerciseKind.CARDIO
    val repsFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Keep the newest set visible.
    LaunchedEffect(todaySets.size) {
        if (todaySets.isNotEmpty()) listState.animateScrollToItem(todaySets.size - 1)
    }

    val complete: () -> Unit = {
        if (vm.completeSet()) runCatching { repsFocus.requestFocus() }
    }

    Column(modifier.imePadding()) {
        PreviousSessionCard(state.previousSets, exercise.kind)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (todaySets.isEmpty()) {
                item {
                    Text(
                        "No sets yet. Log your first one below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            itemsIndexed(todaySets, key = { _, s -> s.id }) { index, set ->
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        Text(
                            "Set ${index + 1}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(64.dp),
                        )
                        Text(
                            describeSet(set.weight, set.reps, exercise.kind),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.deleteSet(set) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete set")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }

        // Input panel pinned to the bottom, right above the keyboard.
        Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Set ${todaySets.size + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                StepperField(
                    label = if (cardio) "Distance (mi)" else "Weight (lb)",
                    value = state.weightInput,
                    onValueChange = vm::onWeightChange,
                    step = if (cardio) 0.1 else 5.0,
                    onStep = vm::adjustWeight,
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                    onIme = { runCatching { repsFocus.requestFocus() } },
                    placeholder = "0",
                )
                StepperField(
                    label = if (cardio) "Time (min)" else "Reps",
                    value = state.repsInput,
                    onValueChange = vm::onRepsChange,
                    step = 1.0,
                    onStep = { vm.adjustReps(it.toInt()) },
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                    onIme = complete,
                    placeholder = (todaySets.lastOrNull()?.reps ?: state.previousSets.firstOrNull()?.reps)
                        ?.toString() ?: "0",
                    modifier = Modifier.focusRequester(repsFocus),
                )
                Button(onClick = complete, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Complete Set", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun PreviousSessionCard(previous: List<WorkoutSet>, kind: ExerciseKind) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            if (previous.isEmpty()) {
                Text("No history for this exercise yet", style = MaterialTheme.typography.bodyMedium)
            } else {
                val date = Instant.ofEpochMilli(previous.first().completedAt).atZone(ZoneId.systemDefault())
                Text(
                    "Last time · ${lastTimeFormat.format(date)}",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    previous.joinToString("   ") { describeSet(it.weight, it.reps, kind) },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun StepperField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    step: Double,
    onStep: (Double) -> Unit,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onIme: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val stepLabel = if (step % 1.0 == 0.0) step.toInt().toString() else step.toString()
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(
            onClick = { onStep(-step) },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(width = 64.dp, height = 56.dp),
        ) { Text("−$stepLabel") }
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onIme() }),
            modifier = modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(
            onClick = { onStep(step) },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(width = 64.dp, height = 56.dp),
        ) { Text("+$stepLabel") }
    }
}
