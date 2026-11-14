package com.scrollbuddy.service

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Stands in for the activity a ComposeView normally finds above it. The overlay is attached
 * straight to WindowManager from a service, and Compose refuses to start without a lifecycle
 * owner and a saved-state owner in its view tree.
 */
class ServiceLifecycleOwner : SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    /** Call from the service's onCreate. */
    fun start() {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    /** Call from the service's onDestroy; also cancels coroutines launched in lifecycleScope. */
    fun stop() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}
