package com.quicklift.data

import java.math.BigDecimal

enum class MuscleGroup(val label: String) {
    BACK("Back"), CHEST("Chest"), LEGS("Legs"), SHOULDERS("Shoulders"), ARMS("Arms")
}

enum class ExerciseKind { STRENGTH, CARDIO }

data class Exercise(val name: String, val kind: ExerciseKind = ExerciseKind.STRENGTH)

data class ExerciseSection(val title: String, val exercises: List<Exercise>)

object ExerciseCatalog {
    private fun s(vararg names: String) = names.map { Exercise(it) }

    val byGroup: Map<MuscleGroup, List<Exercise>> = mapOf(
        MuscleGroup.BACK to s(
            "Deadlift", "Barbell Row", "Pull-Up", "Lat Pulldown",
            "Seated Cable Row", "Dumbbell Row", "T-Bar Row", "Face Pull",
        ),
        MuscleGroup.CHEST to s(
            "Bench Press", "Incline Bench Press", "Dumbbell Press", "Incline Dumbbell Press",
            "Chest Fly", "Cable Crossover", "Dips", "Push-Up",
        ),
        MuscleGroup.LEGS to s(
            "Squat", "Front Squat", "Leg Press", "Romanian Deadlift",
            "Lunge", "Leg Extension", "Leg Curl", "Calf Raise",
        ),
        MuscleGroup.SHOULDERS to s(
            "Overhead Press", "Dumbbell Shoulder Press", "Lateral Raise", "Front Raise",
            "Rear Delt Fly", "Arnold Press", "Upright Row", "Shrug",
        ),
        MuscleGroup.ARMS to s(
            "Barbell Curl", "Dumbbell Curl", "Hammer Curl", "Preacher Curl",
            "Tricep Pushdown", "Skull Crusher", "Overhead Tricep Extension", "Close-Grip Bench Press",
        ),
    )

    val abs: List<Exercise> = s("Crunch", "Hanging Leg Raise", "Cable Crunch", "Ab Wheel", "Russian Twist", "Plank")

    val run: List<Exercise> = listOf(Exercise("Run", ExerciseKind.CARDIO))

    private val all: Map<String, Exercise> by lazy {
        (byGroup.values.flatten() + abs + run).associateBy { it.name }
    }

    fun find(name: String): Exercise = all[name] ?: Exercise(name)

    fun sectionsFor(
        groups: List<MuscleGroup>,
        includeAbs: Boolean,
        includeRun: Boolean,
        knownNames: List<String>,
    ): List<ExerciseSection> = buildList {
        groups.forEach { g -> add(ExerciseSection(g.label, byGroup.getValue(g))) }
        if (includeAbs) add(ExerciseSection("Abs", abs))
        if (includeRun) add(ExerciseSection("Run", run))
        val custom = knownNames.filter { it !in all }.map { Exercise(it) }
        if (custom.isNotEmpty()) add(ExerciseSection("Your exercises", custom))
    }
}

fun encodeGroups(groups: List<MuscleGroup>): String = groups.joinToString(",") { it.name }

fun decodeGroups(stored: String): List<MuscleGroup> =
    stored.split(',').mapNotNull { name -> MuscleGroup.entries.firstOrNull { it.name == name.trim() } }

fun workoutTitle(storedGroups: String, includeAbs: Boolean, includeRun: Boolean): String {
    val parts = decodeGroups(storedGroups).map { it.label } +
        listOfNotNull("Abs".takeIf { includeAbs }, "Run".takeIf { includeRun })
    return parts.joinToString(" + ").ifEmpty { "Workout" }
}

fun Workout.title(): String = workoutTitle(muscleGroups, includeAbs, includeRun)

/** 135.0 -> "135", 137.5 -> "137.5" */
fun formatNumber(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

fun describeSet(weight: Double, reps: Int, kind: ExerciseKind): String = when {
    kind == ExerciseKind.CARDIO -> "${formatNumber(weight)} mi · $reps min"
    weight == 0.0 -> "BW × $reps"
    else -> "${formatNumber(weight)} lb × $reps"
}
