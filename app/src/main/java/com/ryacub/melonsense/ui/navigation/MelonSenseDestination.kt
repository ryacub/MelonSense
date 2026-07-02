package com.ryacub.melonsense.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.ui.graphics.vector.ImageVector
import com.ryacub.melonsense.R

enum class MelonSenseDestination(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
) {
    Scan("scan", R.string.scan_title, Icons.Filled.PhotoCamera),
    KnockTest("knock_test", R.string.knock_test_title, Icons.Filled.GraphicEq),
    Result("result", R.string.result_title, Icons.Filled.TaskAlt),
    History("history", R.string.history_title, Icons.Filled.History),
    Settings("settings", R.string.settings_title, Icons.Filled.Settings),
}
