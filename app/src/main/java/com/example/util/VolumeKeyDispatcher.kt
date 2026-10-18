package com.example.util

/** Which hardware key fired. Down advances, up goes back — the way a page turner behaves. */
enum class VolumeKey { UP, DOWN }

/**
 * Routes hardware volume presses to whichever screen is currently in front.
 *
 * Key events arrive at the Activity, not at a composable, so the viewer registers a handler for
 * as long as it is on screen. When nothing is registered the dispatcher reports the event as
 * unhandled and the system keeps its normal volume behaviour.
 */
object VolumeKeyDispatcher {

    /** Receives the key and whether it came from auto-repeat (the user is holding it down). */
    fun interface Handler {
        fun onVolumeKey(key: VolumeKey, repeat: Boolean): Boolean
    }

    @Volatile
    private var handler: Handler? = null

    val isActive: Boolean get() = handler != null

    fun register(handler: Handler) {
        this.handler = handler
    }

    fun unregister(handler: Handler) {
        if (this.handler === handler) this.handler = null
    }

    fun dispatch(key: VolumeKey, repeat: Boolean): Boolean = handler?.onVolumeKey(key, repeat) ?: false
}
