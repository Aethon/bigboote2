package com.bigboote.coordinator.proxy

/**
 * Marker interface for WebSocket-connected human (external) collaborators.
 *
 * ExternalProxy has no control plane — only [deliverMessage] inherited from
 * [CollaboratorProxy]. Concrete instances are created by
 * [com.bigboote.coordinator.messaging.NativeMessagingAdapter] when a human user
 * opens a WebSocket connection to the messaging endpoint.
 *
 * See Architecture doc Section 9.1.
 */
interface ExternalProxy : CollaboratorProxy
