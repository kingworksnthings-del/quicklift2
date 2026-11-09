package com.quicklift

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quicklift.ui.QuickLiftApp
import com.quicklift.ui.WorkoutViewModel
import com.quicklift.ui.theme.QuickLiftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuickLiftTheme {
                val vm: WorkoutViewModel = viewModel()
                QuickLiftApp(vm)
            }
        }
    }
}
