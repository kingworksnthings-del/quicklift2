package com.quicklift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WorkoutDao {

    // ---- Workouts ----

    @Insert
    abstract suspend fun insertWorkout(workout: Workout): Long

    @Query("SELECT * FROM workouts WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    abstract suspend fun activeWorkout(): Workout?

    @Query("UPDATE workouts SET endedAt = :endedAt WHERE id = :id")
    abstract suspend fun finishWorkout(id: Long, endedAt: Long)

    @Query("DELETE FROM workouts WHERE id = :id")
    abstract suspend fun deleteWorkout(id: Long)

    @Query(
        """
        SELECT w.id AS id, w.startedAt AS startedAt, w.muscleGroups AS muscleGroups,
               w.includeAbs AS includeAbs, w.includeRun AS includeRun, COUNT(s.id) AS setCount
        FROM workouts w LEFT JOIN workout_sets s ON s.workoutId = w.id
        WHERE w.endedAt IS NOT NULL
        GROUP BY w.id
        ORDER BY w.startedAt DESC
        LIMIT 20
        """
    )
    abstract fun observeRecent(): Flow<List<WorkoutSummary>>

    // ---- Sets ----

    @Insert
    abstract suspend fun insertSet(set: WorkoutSet): Long

    @Update
    abstract suspend fun updateSets(sets: List<WorkoutSet>)

    @Query("DELETE FROM workout_sets WHERE id = :id")
    abstract suspend fun deleteSetById(id: Long)

    @Query("SELECT COUNT(*) FROM workout_sets WHERE workoutId = :workoutId")
    abstract suspend fun setCount(workoutId: Long): Int

    @Query("SELECT * FROM workout_sets WHERE workoutId = :workoutId ORDER BY completedAt")
    abstract fun observeSets(workoutId: Long): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM workout_sets WHERE workoutId = :workoutId AND exercise = :exercise ORDER BY completedAt")
    abstract suspend fun setsFor(workoutId: Long, exercise: String): List<WorkoutSet>

    /** All sets of [exercise] from the most recent *other* workout that included it. */
    @Query(
        """
        SELECT * FROM workout_sets
        WHERE exercise = :exercise AND workoutId = (
            SELECT workoutId FROM workout_sets
            WHERE exercise = :exercise AND workoutId != :excludeWorkoutId
            ORDER BY completedAt DESC LIMIT 1
        )
        ORDER BY setNumber
        """
    )
    abstract suspend fun previousSession(exercise: String, excludeWorkoutId: Long): List<WorkoutSet>

    @Query("SELECT DISTINCT exercise FROM workout_sets ORDER BY exercise")
    abstract fun observeExerciseNames(): Flow<List<String>>

    @Query(
        """
        SELECT w.id AS workoutId, w.startedAt AS workoutStart, w.muscleGroups AS muscleGroups,
               w.includeAbs AS includeAbs, w.includeRun AS includeRun,
               s.exercise AS exercise, s.setNumber AS setNumber, s.weight AS weight,
               s.reps AS reps, s.completedAt AS completedAt
        FROM workout_sets s INNER JOIN workouts w ON w.id = s.workoutId
        ORDER BY w.startedAt, s.completedAt
        """
    )
    abstract suspend fun exportRows(): List<ExportRow>

    @Transaction
    open suspend fun deleteSetAndRenumber(set: WorkoutSet) {
        deleteSetById(set.id)
        val remaining = setsFor(set.workoutId, set.exercise)
        updateSets(remaining.mapIndexed { i, s -> s.copy(setNumber = i + 1) })
    }
}
