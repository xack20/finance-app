package app.hisaab

import app.hisaab.auth.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

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
    fun `initial state is Loading`() = runTest {
        val vm = AppViewModel(fakeAuth, hasMasterSecret = { false })
        assertIs<AppState.Loading>(vm.state.value)
    }

    @Test
    fun `no session emits Unauthenticated`() = runTest {
        fakeAuth.signedIn = false
        val vm = AppViewModel(fakeAuth, hasMasterSecret = { false })
        vm.init()
        dispatcher.scheduler.advanceUntilIdle()
        assertIs<AppState.Unauthenticated>(vm.state.value)
    }

    @Test
    fun `session with master secret emits Locked on cold start (DB must be unlocked to open)`() = runTest {
        fakeAuth.signedIn = true
        val vm = AppViewModel(fakeAuth, hasMasterSecret = { true })
        vm.init()
        dispatcher.scheduler.advanceUntilIdle()
        // Cold start with a persisted secret routes to Locked: the SQLCipher DB is not open yet, and
        // the unlock path (LockScreen) is what actually opens it. Going straight to Authenticated
        // would render the main graph against a closed DB and crash.
        assertIs<AppState.Locked>(vm.state.value)
    }

    @Test
    fun `onAppBackground transitions Authenticated to Locked`() = runTest {
        fakeAuth.signedIn = true
        val vm = AppViewModel(fakeAuth, hasMasterSecret = { true }, lockTimeoutMs = 1000L)
        vm.init()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onBiometricUnlockSuccess() // simulate unlock → Authenticated
        vm.onAppBackground()
        dispatcher.scheduler.advanceUntilIdle()
        assertIs<AppState.Locked>(vm.state.value)
    }

    @Test
    fun `onAppBackground starts lock timer and transitions to Locked after timeout`() = runTest {
        fakeAuth.signedIn = true
        val vm = AppViewModel(fakeAuth, hasMasterSecret = { true }, lockTimeoutMs = 1000L)
        vm.init()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onBiometricUnlockSuccess() // unlock → Authenticated
        assertIs<AppState.Authenticated>(vm.state.value)

        vm.onAppBackground()
        dispatcher.scheduler.advanceTimeBy(999L)
        assertIs<AppState.Authenticated>(vm.state.value)
        dispatcher.scheduler.advanceTimeBy(2L)
        assertIs<AppState.Locked>(vm.state.value)
    }

    @Test
    fun `onAppForeground before timeout cancels the lock`() = runTest {
        fakeAuth.signedIn = true
        val vm = AppViewModel(fakeAuth, hasMasterSecret = { true }, lockTimeoutMs = 1000L)
        vm.init()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onBiometricUnlockSuccess() // unlock → Authenticated

        vm.onAppBackground()
        dispatcher.scheduler.advanceTimeBy(500L)
        vm.onAppForeground()
        dispatcher.scheduler.advanceTimeBy(1000L)
        assertIs<AppState.Authenticated>(vm.state.value)
    }
}
