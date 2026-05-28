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
    fun `session with master secret emits Authenticated`() = runTest {
        fakeAuth.signedIn = true
        val vm = AppViewModel(fakeAuth, hasMasterSecret = { true })
        vm.init()
        dispatcher.scheduler.advanceUntilIdle()
        assertIs<AppState.Authenticated>(vm.state.value)
    }

    @Test
    fun `onAppBackground transitions Authenticated to Locked`() = runTest {
        fakeAuth.signedIn = true
        val vm = AppViewModel(fakeAuth, hasMasterSecret = { true })
        vm.init()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onAppBackground()
        assertIs<AppState.Locked>(vm.state.value)
    }
}
