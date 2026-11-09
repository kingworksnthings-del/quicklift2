package com.quicklift.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.quicklift.data.ExerciseCatalog
import com.quicklift.data.ExerciseKind
import com.quicklift.data.ExportRow
import com.quicklift.data.formatNumber
import com.quicklift.data.workoutTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvExporter {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    private const val HEADER =
        "workout_id,date,workout_start,workout,exercise,type,set_number,weight_lb,reps,distance_mi,duration_min,completed_at"

    fun buildCsv(rows: List<ExportRow>): String {
        val zone = ZoneId.systemDefault()
        return buildString {
            append(HEADER).append('\n')
            for (r in rows) {
                val start = Instant.ofEpochMilli(r.workoutStart).atZone(zone)
                val done = Instant.ofEpochMilli(r.completedAt).atZone(zone)
                val cardio = ExerciseCatalog.find(r.exercise).kind == ExerciseKind.CARDIO
                val fields = listOf(
                    r.workoutId.toString(),
                    dateFmt.format(start),
                    timeFmt.format(start),
                    workoutTitle(r.muscleGroups, r.includeAbs, r.includeRun),
                    r.exercise,
                    if (cardio) "cardio" else "strength",
                    r.setNumber.toString(),
                    if (cardio) "" else formatNumber(r.weight),
                    if (cardio) "" else r.reps.toString(),
                    if (cardio) formatNumber(r.weight) else "",
                    if (cardio) r.reps.toString() else "",
                    "${dateFmt.format(done)} ${timeFmt.format(done)}",
                )
                fields.joinTo(this, ",") { escape(it) }
                append('\n')
            }
        }
    }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value

    /** Writes the CSV to the app cache and opens the Android share sheet. */
    suspend fun share(context: Context, csv: String) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            File(dir, "quicklift_${LocalDate.now()}.csv").apply { writeText(csv) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "QuickLift workout export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Export workouts"))
    }
}
