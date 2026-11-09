package com.quicklift.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    /** Comma-separated [MuscleGroup] names, e.g. "BACK,CHEST". */
    val muscleGroups: String,
    val includeAbs: Boolean,
    val includeRun: Boolean,
)

/**
 * One completed set. For cardio exercises (Run), [weight] holds distance in miles
 * and [reps] holds duration in minutes.
 */
@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutId"), Index("exercise")],
)
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exercise: String,
    val setNumber: Int,
    val weight: Double,
    val reps: Int,
    val completedAt: Long,
)

/** Projection used by the home screen history list. */
data class WorkoutSummary(
    val id: Long,
    val startedAt: Long,
    val muscleGroups: String,
    val includeAbs: Boolean,
    val includeRun: Boolean,
    val setCount: Int,
)

/** Flat row joined across workouts + sets for CSV export. */
data class ExportRow(
    val workoutId: Long,
    val workoutStart: Long,
    val muscleGroups: String,
    val includeAbs: Boolean,
    val includeRun: Boolean,
    val exercise: String,
    val setNumber: Int,
    val weight: Double,
    val reps: Int,
    val completedAt: Long,
)
