package com.remotecompose.rc.modifier

import androidx.compose.remote.creation.dsl.Modifier

/**
 * Typed analytics DSL over [hostActionValue] — mirrors the consumer's `AnalyticsApi`
 * `postEvent` overloads so screens never hand-write payload JSON.
 *
 * Every variant funnels into ONE canonical wire contract on the "analytics" channel:
 *
 * ```json
 * {"eventName":"...","params":{...},"shouldPushOncePerSession":false}
 * ```
 *
 * On the consumer, handle it in `onNamedAction("analytics", value, _)` by decoding the
 * JSON and calling the map overload — it subsumes the no-data and single-key ones:
 *
 * ```kotlin
 * analyticsApi.postEvent(p.eventName, p.params, p.shouldPushOncePerSession)
 * ```
 */
const val ANALYTICS_ACTION_NAME = "analytics"

/**
 * A server-side analytics event. Param values may be [String], [Boolean], or numbers —
 * they are encoded as native JSON types so the consumer's `Map<String, Any>` keeps them.
 */
class AnalyticsEvent(
    val eventName: String,
    val params: Map<String, Any> = emptyMap(),
    val shouldPushOncePerSession: Boolean = false,
) {
    fun toJson(): String = buildString {
        append("{\"eventName\":").append(jsonString(eventName))
        append(",\"params\":{")
        params.entries.joinTo(this, ",") { (k, v) -> "${jsonString(k)}:${jsonValue(v)}" }
        append("},\"shouldPushOncePerSession\":").append(shouldPushOncePerSession)
        append('}')
    }
}

/** Builder counterpart for events with conditional params. */
class AnalyticsEventScope(private val eventName: String) {
    private val params = LinkedHashMap<String, Any>()
    var shouldPushOncePerSession: Boolean = false

    fun param(key: String, value: Any) {
        params[key] = value
    }

    fun params(vararg pairs: Pair<String, Any>) {
        params.putAll(pairs)
    }

    fun build(): AnalyticsEvent = AnalyticsEvent(eventName, params, shouldPushOncePerSession)
}

fun analyticsEvent(eventName: String, block: AnalyticsEventScope.() -> Unit = {}): AnalyticsEvent =
    AnalyticsEventScope(eventName).apply(block).build()

/** Fires [event] on the "analytics" channel when the component is tapped. */
fun Modifier.postEvent(event: AnalyticsEvent): Modifier =
    hostActionValue(ANALYTICS_ACTION_NAME, event.toJson())

/**
 * Mirrors `AnalyticsApi.postEvent(eventName)` / the map overload:
 *
 * ```kotlin
 * Modifier.postEvent("profile_back_clicked", "screen" to "profile", "source" to "top_bar")
 * ```
 */
fun Modifier.postEvent(
    eventName: String,
    vararg params: Pair<String, Any>,
    shouldPushOncePerSession: Boolean = false,
): Modifier = postEvent(AnalyticsEvent(eventName, params.toMap(), shouldPushOncePerSession))

/** Mirrors `AnalyticsApi.postEvent(event, key, value)`. */
fun Modifier.postEvent(
    eventName: String,
    key: String,
    value: String,
    shouldPushOncePerSession: Boolean = false,
): Modifier = postEvent(AnalyticsEvent(eventName, mapOf(key to value), shouldPushOncePerSession))

/** Builder form, for events whose params depend on screen state. */
fun Modifier.postEvent(
    eventName: String,
    block: AnalyticsEventScope.() -> Unit,
): Modifier = postEvent(analyticsEvent(eventName, block))

private fun jsonValue(v: Any): String = when (v) {
    is Boolean -> v.toString()
    is Int, is Long, is Short, is Byte -> v.toString()
    is Float -> if (v.isFinite()) v.toString() else jsonString(v.toString())
    is Double -> if (v.isFinite()) v.toString() else jsonString(v.toString())
    else -> jsonString(v.toString())
}

private fun jsonString(s: String): String = buildString(s.length + 2) {
    append('"')
    for (c in s) when {
        c == '"' -> append("\\\"")
        c == '\\' -> append("\\\\")
        c == '\n' -> append("\\n")
        c == '\r' -> append("\\r")
        c == '\t' -> append("\\t")
        c < ' ' -> append("\\u%04x".format(c.code))
        else -> append(c)
    }
    append('"')
}