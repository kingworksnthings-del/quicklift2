package com.quicklift.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quicklift.data.AppDatabase
import com.quicklift.data.Exercise
import com.quicklift.data.ExerciseKind
import com.quicklift.data.MuscleGroup
import com.quicklift.data.Workout
import com.quicklift.data.WorkoutSet
import com.quicklift.data.WorkoutSummary
import com.quicklift.data.decodeGroups
import com.quicklift.data.encodeGroups
import com.quicklift.data.formatNumber
import com.quicklift.export.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

enum class Screen { HOME, WORKOUT }

data class UiState(
    val screen: Screen = Screen.HOME,
    val selectedGroups: List<MuscleGroup> = emptyList(),
    val includeAbs: Boolean = false,
    val includeRun: Boolean = false,
    val activeWorkout: Workout? = null,
    val selectedExercise: Exercise? = null,
    /** Sets from the last previous session of the selected exercise. */
    val previousSets: List<WorkoutSet> = emptyList(),
    val weightInput: String = "",
    val repsInput: String = "",
) {
    val canStart: Boolean get() = selectedGroups.isNotEmpty() || includeAbs || includeRun
}

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).workoutDao()
    private val writeLock = Mutex()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    /** Every set logged in the active workout, live from Room. */
    val sessionSets: StateFlow<List<WorkoutSet>> = _state
        .map { it.activeWorkout?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.observeSets(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentWorkouts: StateFlow<List<WorkoutSummary>> = dao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val knownExercises: StateFlow<List<String>> = dao.observeExerciseNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Resume an unfinished workout (e.g. after the app was killed mid-session).
        viewModelScope.launch {
            dao.activeWorkout()?.let { w ->
                _state.update {
                    it.copy(
                        activeWorkout = w,
                        selectedGroups = decodeGroups(w.muscleGroups),
                        includeAbs = w.includeAbs,
                        includeRun = w.includeRun,
                    )
                }
            }
        }
    }

    // ---------------- Home ----------------

    /** Keeps at most two primary groups; picking a third replaces the oldest pick. */
    fun toggleGroup(group: MuscleGroup) = _state.update { s ->
        val groups = when {
            group in s.selectedGroups -> s.selectedGroups - group
            s.selectedGroups.size < 2 -> s.selectedGroups + group
            else -> s.selectedGroups.drop(1) + group
        }
        s.copy(selectedGroups = groups)
    }

    fun setAbs(on: Boolean) = _state.update { it.copy(includeAbs = on) }
    fun setRun(on: Boolean) = _state.update { it.copy(includeRun = on) }

    fun startWorkout() {
        val s = _state.value
        if (!s.canStart) return
        viewModelScope.launch {
            s.activeWorkout?.let { closeWorkout(it.id) }
            val workout = Workout(
                startedAt = System.currentTimeMillis(),
                muscleGroups = encodeGroups(s.selectedGroups),
                includeAbs = s.includeAbs,
                includeRun = s.includeRun,
            )
            val id = dao.insertWorkout(workout)
            _state.update {
                it.copy(
                    activeWorkout = workout.copy(id = id),
                    screen = Screen.WORKOUT,
                    selectedExercise = null,
                    previousSets = emptyList(),
                    weightInput = "",
                    repsInput = "",
                )
            }
        }
    }

    fun resumeWorkout() = _state.update { if (it.activeWorkout != null) it.copy(screen = Screen.WORKOUT) else it }

    fun goHome() = _state.update { it.copy(screen = Screen.HOME) }

    fun finishWorkout() {
        val w = _state.value.activeWorkout ?: return
        viewModelScope.launch {
            val saved = closeWorkout(w.id)
            _state.update {
                it.copy(
                    screen = Screen.HOME,
                    activeWorkout = null,
                    selectedExercise = null,
                    previousSets = emptyList(),
                    weightInput = "",
                    repsInput = "",
                )
            }
            _events.tryEmit(if (saved) "Workout saved" else "Empty workout discarded")
        }
    }

    /** Ends the workout; deletes it if nothing was logged. Returns true if it was kept. */
    private suspend fun closeWorkout(id: Long): Boolean {
        return if (dao.setCount(id) == 0) {
            dao.deleteWorkout(id); false
        } else {
            dao.finishWorkout(id, System.currentTimeMillis()); true
        }
    }

    // ---------------- Logging ----------------

    fun selectExercise(exercise: Exercise) {
        val w = _state.value.activeWorkout ?: return
        viewModelScope.launch {
            val previous = dao.previousSession(exercise.name, w.id)
            val today = dao.setsFor(w.id, exercise.name)
            // Pre-fill weight: last set done today, else the first set from last time.
            val prefill = today.lastOrNull()?.weight ?: previous.firstOrNull()?.weight
            _state.update {
                it.copy(
                    selectedExercise = exercise,
                    previousSets = previous,
                    weightInput = prefill?.let(::formatNumber) ?: "",
                    repsInput = "",
                )
            }
        }
    }

    fun clearExercise() = _state.update { it.copy(selectedExercise = null, previousSets = emptyList()) }

    private val weightPattern = Regex("""\d{0,4}(\.\d{0,2})?""")
    private val repsPattern = Regex("""\d{0,3}""")

    fun onWeightChange(text: String) {
        if (weightPattern.matches(text)) _state.update { it.copy(weightInput = text) }
    }

    fun onRepsChange(text: String) {
        if (repsPattern.matches(text)) _state.update { it.copy(repsInput = text) }
    }

    fun adjustWeight(delta: Double) = _state.update {
        val current = it.weightInput.toDoubleOrNull() ?: 0.0
        val next = ((current + delta).coerceAtLeast(0.0) * 100).roundToLong() / 100.0
        it.copy(weightInput = formatNumber(next))
    }

    fun adjustReps(delta: Int) = _state.update {
        val next = ((it.repsInput.toIntOrNull() ?: 0) + delta).coerceIn(0, 999)
        it.copy(repsInput = if (next == 0) "" else next.toString())
    }

    /**
     * Saves the current set, then resets the input row for the next set with the
     * weight carried over and reps cleared. Returns false if input was invalid.
     */
    fun completeSet(): Boolean {
        val s = _state.value
        val workout = s.activeWorkout ?: return false
        val exercise = s.selectedExercise ?: return false
        val reps = s.repsInput.toIntOrNull()
        if (reps == null || reps <= 0) {
            _events.tryEmit(if (exercise.kind == ExerciseKind.CARDIO) "Enter a time" else "Enter reps")
            return false
        }
        val weight = s.weightInput.toDoubleOrNull() ?: 0.0

        // Update UI immediately so the next set row is ready with no lag.
        _state.update { it.copy(weightInput = formatNumber(weight), repsInput = "") }

        viewModelScope.launch {
            writeLock.withLock {
                val setNumber = dao.setsFor(workout.id, exercise.name).size + 1
                dao.insertSet(
                    WorkoutSet(
                        workoutId = workout.id,
                        exercise = exercise.name,
                        setNumber = setNumber,
                        weight = weight,
                        reps = reps,
                        completedAt = System.currentTimeMillis(),
                    )
                )
            }
        }
        return true
    }

    fun deleteSet(set: WorkoutSet) {
        viewModelScope.launch { writeLock.withLock { dao.deleteSetAndRenumber(set) } }
    }

    // ---------------- Export ----------------

    /** Returns the full history as CSV, or null if nothing has been logged. */
    suspend fun buildExportCsv(): String? {
        val rows = dao.exportRows()
        if (rows.isEmpty()) return null
        return withContext(Dispatchers.Default) { CsvExporter.buildCsv(rows) }
    }

    fun showMessage(message: String) {
        _events.tryEmit(message)
    }
}
