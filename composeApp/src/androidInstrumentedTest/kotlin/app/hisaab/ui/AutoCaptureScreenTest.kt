package app.hisaab.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.hisaab.design.HisaabColors
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.domain.EngineMode
import app.hisaab.screens.settings.EnginePicker
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AutoCaptureScreenTest {

    @get:org.junit.Rule
    val composeRule = createComposeRule()

    @Test
    fun enginePicker_selectingCloud_invokesCallbackAndReflectsSelection() {
        var captured: EngineMode? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalHisaabPalette provides HisaabColors.Light) {
                var mode by remember { mutableStateOf(EngineMode.ON_DEVICE) }
                EnginePicker(
                    mode = mode,
                    palette = HisaabColors.Light,
                    onSelect = { captured = it; mode = it },
                )
            }
        }
        composeRule.onNodeWithText("Cloud (your own API key)").assertIsDisplayed()
        composeRule.onNodeWithText("Cloud (your own API key)").performClick()
        assertEquals(EngineMode.CLOUD, captured)
        // The real row re-renders with the selection check next to the chosen mode.
        composeRule.onNodeWithText("✓").assertIsDisplayed()
    }
}
