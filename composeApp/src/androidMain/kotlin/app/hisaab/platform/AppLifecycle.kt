package app.hisaab.platform

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

actual class AppLifecycle {
    private val flow = MutableSharedFlow<LifecycleEvent>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                flow.tryEmit(LifecycleEvent.Foreground)
            }
            override fun onStop(owner: LifecycleOwner) {
                flow.tryEmit(LifecycleEvent.Background)
            }
        })
    }

    actual fun events(): Flow<LifecycleEvent> = flow
}
