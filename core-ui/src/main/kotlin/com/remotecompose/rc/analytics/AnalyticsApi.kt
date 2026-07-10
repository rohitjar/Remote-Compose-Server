package com.remotecompose.rc.analytics

/**
 * Server-side mirror of the consumer app's `AnalyticsApi` (jarcoreanalytics).
 *
 * Signatures — method names, parameter names, defaults — match the consumer interface
 * exactly, so screen code here reads the same as ViewModel code there:
 *
 * ```kotlin
 * Modifier.onClick {
 *     analyticsApi.postEvent(
 *         event = AnalyticsKey.Feature_Experiment_FE,
 *         values = mapOf(
 *             AnalyticsKey.experimentName to variant,
 *             AnalyticsKey.cohort to cohort,
 *         )
 *     )
 *     analyticsApi.setUserProperty(listOf(AnalyticsKey.userCategory to cohort))
 * }
 * ```
 *
 * Nothing is posted on the server. The implementation ([HostActionAnalyticsApi],
 * injected into the `onClick { }` scope as `analyticsApi`) records each call as a
 * value-carrying host action on the [ANALYTICS_ACTION_NAME] channel whose STRING value
 * is a self-describing JSON payload:
 *
 * ```json
 * {"method":"postEvent","eventName":"e","shouldPushOncePerSession":false}
 * {"method":"postEvent","eventName":"e","value":"v","shouldPushOncePerSession":false}
 * {"method":"postEvent","eventName":"e","key":"k","value":"v","shouldPushOncePerSession":false}
 * {"method":"postEvent","eventName":"e","values":{...},"shouldPushOncePerSession":false}
 * {"method":"sendPurchaseEvent","value":49.0,"shouldPushOncePerSession":false}
 * {"method":"onUserLogin","id":"u1","values":{...},"shouldPushOncePerSession":false}
 * {"method":"setUserProperty","properties":{"k":"v"},"shouldPushOncePerSession":false}
 * {"method":"updateFcmToken","token":"t","shouldPushOncePerSession":false}
 * {"method":"onUserLogout","shouldPushOncePerSession":false}
 * {"method":"setAttributionData","attributionMap":{...},"shouldPushOncePerSession":false}
 * {"method":"tearDown"}
 * {"method":"clearFilterMap"}
 * {"method":"enableInternetSyncing"}
 * {"method":"disableInternetSyncing"}
 * ```
 *
 * A non-default [EventRestriction] adds `"restriction":"<name>"`; absent means
 * `Unrestricted`. The three `postEvent` shapes with `value`/`key` are distinguishable by
 * which fields are present.
 *
 * Consumer side, in `onNamedAction("analytics", json, _)`: decode, `when` on `method`,
 * and replay onto the real `AnalyticsApi` singleton. `init`/`getInstance` are not
 * mirrored — wiring up `IAppAnalyticsService`s is app-startup work that can only live on
 * the consumer.
 */
interface AnalyticsApi {

    /** Method to post event without any data. */
    fun postEvent(
        eventName: String,
        shouldPushOncePerSession: Boolean = false,
        restriction: EventRestriction = EventRestriction.Unrestricted,
    )

    /** Method to post event with single value without any key. */
    fun postEvent(
        event: String,
        value: String,
        shouldPushOncePerSession: Boolean = false,
        restriction: EventRestriction = EventRestriction.Unrestricted,
    )

    /** Method to post event with single value with key. */
    fun postEvent(
        event: String,
        key: String,
        value: String,
        shouldPushOncePerSession: Boolean = false,
        restriction: EventRestriction = EventRestriction.Unrestricted,
    )

    /** Method to post event with map data. */
    fun postEvent(
        event: String,
        values: Map<String, Any>,
        shouldPushOncePerSession: Boolean = false,
        restriction: EventRestriction = EventRestriction.Unrestricted,
    )

    /** Method to send the purchase data. */
    fun sendPurchaseEvent(
        value: Float,
        shouldPushOncePerSession: Boolean = false,
    )

    /** Must be called after successful login. */
    fun onUserLogin(
        id: String,
        values: Map<String, Any>,
        shouldPushOncePerSession: Boolean = false,
        restriction: EventRestriction = EventRestriction.Unrestricted,
    )

    /** Method to set user properties. */
    fun setUserProperty(
        properties: List<Pair<String, String>>,
        shouldPushOncePerSession: Boolean = false,
        restriction: EventRestriction = EventRestriction.Unrestricted,
    )

    /** Must be called whenever FCM token is updated. */
    fun updateFcmToken(
        token: String,
        shouldPushOncePerSession: Boolean = false,
    )

    /** Must be called on successful logout. */
    fun onUserLogout(shouldPushOncePerSession: Boolean = false)

    /** Method used to set attribution data. */
    fun setAttributionData(
        attributionMap: Map<String, String>,
        shouldPushOncePerSession: Boolean = false,
        restriction: EventRestriction = EventRestriction.Unrestricted,
    )

    /** Must be called when App is being destroyed. */
    fun tearDown()

    fun clearFilterMap()

    fun enableInternetSyncing()

    fun disableInternetSyncing()
}

/** Host-action name every analytics call travels on. */
const val ANALYTICS_ACTION_NAME = "analytics"