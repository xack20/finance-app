package app.hisaab.screens.onboarding

import app.hisaab.auth.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Ignore("OnboardingViewModel now requires AppContainer which has Android-specific actuals; instrumented tests in P0c-3 Task 23 exercise this flow")
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var fakeAuth: FakeAuthRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
        fakeAuth = FakeAuthRepository()
    }

    @AfterTest
    fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `initial state has empty phone and otpSent false`() = runTest {
        val vm = OnboardingViewModel(fakeAuth)
        assertEquals("", vm.state.value.phone)
        assertFalse(vm.state.value.otpSent)
    }

    @Test
    fun `sendOtp success sets otpSent true`() = runTest {
        val vm = OnboardingViewModel(fakeAuth)
        vm.sendOtp("+8801700000000")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.otpSent)
    }

    @Test
    fun `sendOtp failure sets error`() = runTest {
        fakeAuth.otpError = RuntimeException("Network error")
        val vm = OnboardingViewModel(fakeAuth)
        vm.sendOtp("+8801700000000")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Network error", vm.state.value.error)
    }

    @Test
    fun `acknowledgePhraseWrittenDown sets phraseAcknowledged true`() = runTest {
        val vm = OnboardingViewModel(fakeAuth)
        assertFalse(vm.state.value.phraseAcknowledged)
        vm.acknowledgePhraseWrittenDown()
        assertTrue(vm.state.value.phraseAcknowledged)
    }
}
