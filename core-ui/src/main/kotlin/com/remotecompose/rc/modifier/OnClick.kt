@file:Suppress("RestrictedApi")

package com.remotecompose.rc.modifier

import androidx.compose.remote.creation.actions.Action
import androidx.compose.remote.creation.actions.HostAction
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.modifiers.RecordingModifier
import com.remotecompose.rc.analytics.ANALYTICS_ACTION_NAME
import com.remotecompose.rc.analytics.AnalyticsApi
import com.remotecompose.rc.analytics.HostActionAnalyticsApi

/**
 * Drop-in replacement for the vendor `Modifier.onClick` whose scope carries an
 * [analyticsApi] mirroring the consumer's `AnalyticsApi`, so tap handlers read like
 * consumer ViewModel code:
 *
 * ```kotlin
 * Modifier.onClick {
 *     hostAction("onBack")
 *     analyticsApi.postEvent(
 *         event = AnalyticsKey.Feature_Experiment_FE,
 *         values = mapOf(AnalyticsKey.cohort to cohort),
 *     )
 *     analyticsApi.setUserProperty(listOf(AnalyticsKey.userCategory to cohort))
 * }
 * ```
 *
 * Only the import changes vs the vendor DSL (`com.remotecompose.rc.modifier.onClick`
 * instead of `androidx.compose.remote.creation.dsl.onClick`); `hostAction(name)` keeps
 * the same shape. The vendor scope's `setValue` isn't mirrored here — chain a vendor
 * `onClick { }` alongside if a component needs value changes too.
 *
 * Each `analyticsApi.*` call is recorded, in call order, as a value-carrying host action
 * on the [ANALYTICS_ACTION_NAME] channel (the consumer sees `onNamedAction("analytics",
 * payloadJson, _)` per call and replays it onto its real `AnalyticsApi`). The vendor
 * `RcActionScope` can't do this — it is internal and its `hostAction` has no value
 * overload — hence this parallel scope, built on the same `RecordingModifier.onClick`
 * the vendor element uses.
 */
fun Modifier.onClick(block: RcClickScope.() -> Unit): Modifier =
    then(ClickScopeElement(block))

@DslMarker
annotation class RcClickDslMarker

@RcClickDslMarker
class RcClickScope internal constructor() {
    internal val actions = mutableListOf<Action>()

    /**
     * Analytics recorder injected into the scope — same name and call shapes as the
     * consumer's `analyticsApi`. Calls are encoded lazily at document write time
     * (`writer.addText` needs the writer), so they cost nothing to declare.
     */
    val analyticsApi: AnalyticsApi = HostActionAnalyticsApi { json ->
        actions += hostActionWithValue(ANALYTICS_ACTION_NAME, json)
    }

    /** Fires the named host action with no value — same as the vendor DSL. */
    fun hostAction(name: String) {
        actions += HostAction(name)
    }

    /** Fires the named host action carrying [payloadJson] as its STRING value. */
    fun hostAction(name: String, payloadJson: String) {
        actions += hostActionWithValue(name, payloadJson)
    }

    private fun hostActionWithValue(name: String, payloadJson: String) =
        Action { writer ->
            val valueId = writer.addText(payloadJson)
            HostAction(name, HOST_STRING_TYPE, valueId).write(writer)
        }
}

private class ClickScopeElement(
    private val block: RcClickScope.() -> Unit,
) : Modifier.Element {
    override fun applyTo(modifier: RecordingModifier) {
        val scope = RcClickScope().apply(block)
        modifier.onClick(*scope.actions.toTypedArray())
    }
}