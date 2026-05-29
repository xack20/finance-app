package app.hisaab.llm

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.hisaab.domain.Category
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidOnDeviceProviderTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val categories = listOf(
        Category("food", "Food & dining", null, null, null, true),
        Category("salary", "Salary", null, null, null, true),
    )

    @Test
    fun availabilityProbeNeverThrows() {
        val manager = ModelManager(context)
        // Probe must be safe to call regardless of device.
        manager.isAnyModelAvailable()
        manager.isAiCorePresent()
    }

    @Test
    fun parsesWhenModelPresent() = runBlocking {
        val manager = ModelManager(context)
        assumeTrue("Gemma model not present on device; skipping on-device parse", manager.isGemmaPresent())

        val provider = AndroidOnDeviceProvider(context, manager)
        assertTrue(provider.isAvailable())
        val result = provider.parse(
            ParseRequest(
                text = "You have received Tk 1500 from 017XXXXXXXX. TrxID 9AB12CD34",
                senderHint = "bKash",
                categories = categories,
            ),
        )
        assertNotNull(result)
        // A money SMS should parse as financial; exact fields depend on the model.
        assertTrue(result.isFinancial)
    }

    @Test
    fun createOnDeviceProviderReturnsNullWithoutContextOrModel() {
        // With no model placed and no global context set, factory returns null.
        AndroidLlmContext.appContext = null
        assertTrue(createOnDeviceProvider() == null)
    }
}
