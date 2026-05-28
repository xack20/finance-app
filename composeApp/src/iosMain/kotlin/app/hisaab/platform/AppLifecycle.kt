package app.hisaab.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

actual class AppLifecycle {
    // TODO P0d: wire UIApplicationDidEnterBackground / WillEnterForeground notifications
    actual fun events(): Flow<LifecycleEvent> = emptyFlow()
}
