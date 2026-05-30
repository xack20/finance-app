@file:OptIn(ExperimentalForeignApi::class)

package app.hisaab.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification

/**
 * iOS lifecycle via NSNotificationCenter — the analogue of Android's ProcessLifecycleOwner observer.
 * Emits [LifecycleEvent.Background] on enter-background and [LifecycleEvent.Foreground] on
 * will-enter-foreground so AppViewModel's lock timer behaves the same on both platforms.
 */
actual class AppLifecycle {
    private val flow = MutableSharedFlow<LifecycleEvent>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { flow.tryEmit(LifecycleEvent.Background) }
        center.addObserverForName(
            name = UIApplicationWillEnterForegroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { flow.tryEmit(LifecycleEvent.Foreground) }
    }

    actual fun events(): Flow<LifecycleEvent> = flow
}
