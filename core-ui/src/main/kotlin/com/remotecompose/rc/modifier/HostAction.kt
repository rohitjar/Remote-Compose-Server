@file:Suppress("RestrictedApi")

package com.remotecompose.rc.modifier

import androidx.compose.remote.creation.actions.Action
import androidx.compose.remote.creation.actions.HostAction
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.modifiers.RecordingModifier

/**
 * Value-carrying host actions for the high-level `rcDocument { }` DSL.
 *
 * The built-in `onClick { hostAction("name") }` DSL can only bake a *name* — its
 * `RcActionScope.hostAction(String)` has no value overload, so it always emits
 * `HostNamedActionOperation` with NONE_TYPE (null value on the consumer).
 *
 * These helpers let a tapped component also carry ONE typed value that surfaces as
 * the `value` argument of the consumer's `onNamedAction(name, value, stateUpdater)`.
 * Type constants are `HostNamedActionOperation.*_TYPE` (verified against alpha13).
 */
const val HOST_FLOAT_TYPE = 0
const val HOST_INT_TYPE = 1
const val HOST_STRING_TYPE = 2

/**
 * A `dsl.Modifier.Element` that attaches a value-carrying host action. `applyTo` runs
 * during modifier folding (no writer yet); the real work is deferred to encode time via
 * a lazy [Action], where the writer IS available — so `addText` returns a real `Int` id
 * (sidestepping `remoteText`, which returns a boxed `RcText`, not an `Int`).
 */
private class HostActionValueElement(
    private val name: String,
    private val type: Int,
    private val payloadJson: String,
) : Modifier.Element {
    override fun applyTo(modifier: RecordingModifier) {
        modifier.onClick({ writer ->
            val valueId = writer.addText(payloadJson)
            HostAction(name, type, valueId).write(writer)
        })
    }
}

/**
 * Fires the named host action [name] carrying [payloadJson] as a STRING value on tap.
 *
 * Consumer receives it as `onNamedAction(name, payloadJson, stateUpdater)`. Use [name]
 * as a generic channel/verb ("analytics", "navigate", "deeplink", …) and put everything
 * else — analytics eventName + params, a route, a upiId, etc. — inside the JSON payload.
 *
 * If `value` arrives null on the consumer, swap `writer.addText` for
 * `writer.addNamedString("USER:evtPayload", payloadJson)` (also returns `Int`).
 */
fun Modifier.hostActionValue(
    name: String,
    payloadJson: String,
    type: Int = HOST_STRING_TYPE,
): Modifier = then(HostActionValueElement(name, type, payloadJson))
