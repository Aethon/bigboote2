package com.bigboote.events.eventstore

/**
 * Handle to a running event subscription. Call [stop] to cancel.
 */
interface EventSubscription {
    /** Stops the subscription and releases resources. */
    fun stop()
}
