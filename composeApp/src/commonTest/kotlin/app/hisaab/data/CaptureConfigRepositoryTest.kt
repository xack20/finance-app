package app.hisaab.data

import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.CloudProvider
import app.hisaab.domain.EngineMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureConfigRepositoryTest {

    @Test
    fun `get returns defaults on a fresh db`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureConfigRepository(db)
        val cfg = repo.get()
        assertFalse(cfg.captureEnabled)
        assertEquals(EngineMode.ON_DEVICE, cfg.engineMode)
        assertEquals("auto", cfg.onDeviceModel)
        assertNull(cfg.cloudProvider)
        assertTrue(cfg.redactionEnabled)
        assertFalse(cfg.alwaysReview)
        assertEquals(0.85, cfg.autoPostThreshold)
        assertNull(cfg.cloudConsentAt)
        assertTrue(cfg.retainRawBody)
        assertEquals(0L, cfg.lastSmsCursor)
    }

    @Test
    fun `setEngineMode persists`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureConfigRepository(db)
        repo.setEngineMode(EngineMode.CLOUD)
        assertEquals(EngineMode.CLOUD, repo.get().engineMode)
    }

    @Test
    fun `setCloudProvider persists provider and model`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureConfigRepository(db)
        repo.setCloudProvider(CloudProvider.CLAUDE, "claude-sonnet")
        val cfg = repo.get()
        assertEquals(CloudProvider.CLAUDE, cfg.cloudProvider)
        assertEquals("claude-sonnet", cfg.cloudModel)
        repo.setCloudProvider(null, null)
        val cleared = repo.get()
        assertNull(cleared.cloudProvider)
        assertNull(cleared.cloudModel)
    }

    @Test
    fun `setRedaction setAlwaysReview setCaptureEnabled toggle booleans`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureConfigRepository(db)
        repo.setRedaction(false)
        repo.setAlwaysReview(true)
        repo.setCaptureEnabled(true)
        val cfg = repo.get()
        assertFalse(cfg.redactionEnabled)
        assertTrue(cfg.alwaysReview)
        assertTrue(cfg.captureEnabled)
    }

    @Test
    fun `setAutoPostThreshold persists`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureConfigRepository(db)
        repo.setAutoPostThreshold(0.5)
        assertEquals(0.5, repo.get().autoPostThreshold)
    }

    @Test
    fun `recordConsent and setCursor persist`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureConfigRepository(db)
        repo.recordConsent(12_345L)
        repo.setCursor(99_999L)
        val cfg = repo.get()
        assertEquals(12_345L, cfg.cloudConsentAt)
        assertEquals(99_999L, cfg.lastSmsCursor)
        repo.clearConsent()
        assertEquals(null, repo.get().cloudConsentAt)
    }

    @Test
    fun `observe reflects updates`() = runTest {
        val db = TestDatabase.create()
        val repo = CaptureConfigRepository(db)
        repo.setEngineMode(EngineMode.CLOUD)
        assertEquals(EngineMode.CLOUD, repo.observe().first().engineMode)
    }
}
