package com.remotecompose.rc.analytics

/**
 * Server-side mirror of the consumer's
 * `com.jar.internal.library.jarcoreanalytics.impl.model.EventRestriction`, so
 * [AnalyticsApi] signatures (and call sites) match the consumer app character-for-character.
 *
 * On the wire it travels as `"restriction":"<wireName>"`, and only when it is NOT
 * [Unrestricted] — an absent field means unrestricted, which keeps the common-case
 * payload small. When the consumer decodes a payload it maps the wireName back onto its
 * real `EventRestriction`.
 *
 * Only [Unrestricted] is mirrored today; add a sibling `data object` per consumer
 * variant as screens start needing them.
 */
sealed class EventRestriction(internal val wireName: String) {
    data object Unrestricted : EventRestriction("Unrestricted")
}