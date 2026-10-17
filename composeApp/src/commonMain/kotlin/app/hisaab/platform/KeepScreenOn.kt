package app.hisaab.platform

import androidx.compose.runtime.Composable

/**
 * Keeps the display awake while this is in composition. Used during history imports: the encrypted DB
 * closes when the app backgrounds and locks, so a screen timeout would otherwise pause the import.
 */
@Composable
expect fun KeepScreenOn()
