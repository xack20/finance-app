package app.hisaab.platform

import kotlinx.coroutines.flow.Flow

sealed class LifecycleEvent {
    data object Foreground : LifecycleEvent()
    data object Background : LifecycleEvent()
}

expect class AppLifecycle {
    fun events(): Flow<LifecycleEvent>
}
