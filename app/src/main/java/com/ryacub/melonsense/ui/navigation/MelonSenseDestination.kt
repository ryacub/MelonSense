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
    val isTopLevel: Boolean = false,
    val requiresVisualResult: Boolean = false,
) {
    Scan("scan", R.string.scan_title, Icons.Filled.PhotoCamera, isTopLevel = true),
    KnockTest("knock_test", R.string.knock_test_title, Icons.Filled.GraphicEq, requiresVisualResult = true),
    Result("result", R.string.result_title, Icons.Filled.TaskAlt),
    History("history", R.string.history_title, Icons.Filled.History, isTopLevel = true),
    Settings("settings", R.string.settings_title, Icons.Filled.Settings, isTopLevel = true),
    ;

    companion object {
        val topLevelEntries: List<MelonSenseDestination> = entries.filter { destination -> destination.isTopLevel }
    }
}
