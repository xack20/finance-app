package app.hisaab.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

actual class AppLifecycle {
    actual fun events(): Flow<LifecycleEvent> = emptyFlow()
}
