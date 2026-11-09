# QuickLift

A fast, offline workout logger for Android, built with Jetpack Compose and Room.

## Get the APK

**Option A: GitHub (no Android tools needed)**
1. Create a new GitHub repository and upload everything in this folder, including the `.github` folder.
2. Open the **Actions** tab. The "Build APK" workflow runs on every push (or press **Run workflow**).
3. When it finishes (about 5 minutes), download the **QuickLift-apk** artifact, unzip it and copy `app-release.apk` to your phone.

**Option B: Android Studio**
Open this folder in Android Studio, then go to **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
Or run `./gradlew assembleRelease` from a terminal. The APK is written to `app/build/outputs/apk/release/`.

**Install on the phone:** open the APK file and allow "Install unknown apps" for your file manager or browser when Android asks.
The release build is optimized with R8 and signed with the standard debug key, so it installs without any extra signing setup.

## Project layout

```
app/src/main/java/com/quicklift/
├── MainActivity.kt            Entry point, edge-to-edge Compose host
├── data/
│   ├── Entities.kt            Room entities (Workout, WorkoutSet) + query projections
│   ├── WorkoutDao.kt          All queries: last-session lookup, history, CSV export
│   ├── AppDatabase.kt         Room database singleton
│   └── Exercises.kt           Muscle groups, exercise catalog, formatting helpers
├── export/CsvExporter.kt      CSV builder + FileProvider share sheet
└── ui/
    ├── WorkoutViewModel.kt    UI state (StateFlow), all actions
    ├── QuickLiftApp.kt        Screen switching + snackbar events
    ├── HomeScreen.kt          Muscle group picker, Abs/Run toggles, start, history, export
    ├── WorkoutScreen.kt       Exercise picker + set logger
    └── theme/Theme.kt         Material 3, with dynamic color on Android 12+
```

## How logging works
- Choosing an exercise shows every set from the **last session** of that exercise, and pre-fills the weight.
- **Complete Set** saves instantly and resets the row for the next set. The weight carries over, reps are cleared,
  and the cursor jumps to Reps. The keyboard's Done key also completes the set.
- Use the ±5 lb and ±1 rep buttons to adjust without typing.
- For **Run**, the two fields become Distance (mi) and Time (min).
- An unfinished workout survives the app being closed. Use **Resume** on the home screen.
- Finishing a workout with no sets discards it.

## CSV columns
`workout_id, date, workout_start, workout, exercise, type, set_number, weight_lb, reps, distance_mi, duration_min, completed_at`
