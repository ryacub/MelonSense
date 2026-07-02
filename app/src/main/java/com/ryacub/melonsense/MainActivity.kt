package com.ryacub.melonsense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ryacub.melonsense.ui.navigation.MelonSenseApp
import com.ryacub.melonsense.ui.theme.MelonSenseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MelonSenseTheme {
                MelonSenseApp()
            }
        }
    }
}
