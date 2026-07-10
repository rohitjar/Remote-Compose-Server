package com.remotecompose.rc.analytics

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * [AnalyticsApi] that serializes every call to the wire JSON documented on the
 * interface and hands it to [emit], one payload per call, preserving call order.
 *
 * Payloads are built with kotlinx-serialization's [buildJsonObject], so escaping and
 * structure are correct by construction, and nested maps/lists inside a
 * `values: Map<String, Any>` encode as real JSON objects/arrays (see [toJsonElement]).
 *
 * The `onClick { }` scope supplies an [emit] that appends a value-carrying host action
 * on the [ANALYTICS_ACTION_NAME] channel; encoding into the document is deferred to
 * write time, so declaring calls costs nothing.
 */
class HostActionAnalyticsApi(private val emit: (payloadJson: String) -> Unit) : AnalyticsApi {

    override fun postEvent(
        eventName: String,
        shouldPushOncePerSession: Boolean,
        restriction: EventRestriction,
    ) = emit(payload("postEvent", shouldPushOncePerSession, restriction) {
        put("eventName", eventName)
    })

    override fun postEvent(
        event: String,
        value: String,
        shouldPushOncePerSession: Boolean,
        restriction: EventRestriction,
    ) = emit(payload("postEvent", shouldPushOncePerSession, restriction) {
        put("eventName", event)
        put("value", value)
    })

    override fun postEvent(
        event: String,
        key: String,
        value: String,
        shouldPushOncePerSession: Boolean,
        restriction: EventRestriction,
    ) = emit(payload("postEvent", shouldPushOncePerSession, restriction) {
        put("eventName", event)
        put("key", key)
        put("value", value)
    })

    override fun postEvent(
        event: String,
        values: Map<String, Any>,
        shouldPushOncePerSession: Boolean,
        restriction: EventRestriction,
    ) = emit(payload("postEvent", shouldPushOncePerSession, restriction) {
        put("eventName", event)
        put("values", toJsonElement(values))
    })

    override fun sendPurchaseEvent(value: Float, shouldPushOncePerSession: Boolean) =
        emit(payload("sendPurchaseEvent", shouldPushOncePerSession) {
            put("value", toJsonElement(value))
        })

    override fun onUserLogin(
        id: String,
        values: Map<String, Any>,
        shouldPushOncePerSession: Boolean,
        restriction: EventRestriction,
    ) = emit(payload("onUserLogin", shouldPushOncePerSession, restriction) {
        put("id", id)
        put("values", toJsonElement(values))
    })

    override fun setUserProperty(
        properties: List<Pair<String, String>>,
        shouldPushOncePerSession: Boolean,
        restriction: EventRestriction,
    ) = emit(payload("setUserProperty", shouldPushOncePerSession, restriction) {
        put("properties", buildJsonObject {
            properties.forEach { (k, v) -> put(k, v) }
        })
    })

    override fun updateFcmToken(token: String, shouldPushOncePerSession: Boolean) =
        emit(payload("updateFcmToken", shouldPushOncePerSession) {
            put("token", token)
        })

    override fun onUserLogout(shouldPushOncePerSession: Boolean) =
        emit(payload("onUserLogout", shouldPushOncePerSession))

    override fun setAttributionData(
        attributionMap: Map<String, String>,
        shouldPushOncePerSession: Boolean,
        restriction: EventRestriction,
    ) = emit(payload("setAttributionData", shouldPushOncePerSession, restriction) {
        put("attributionMap", toJsonElement(attributionMap))
    })

    override fun tearDown() = emit(payload("tearDown"))

    override fun clearFilterMap() = emit(payload("clearFilterMap"))

    override fun enableInternetSyncing() = emit(payload("enableInternetSyncing"))

    override fun disableInternetSyncing() = emit(payload("disableInternetSyncing"))

    /**
     * `{"method":"<m>", <fields()>, "shouldPushOncePerSession":<b>?, "restriction":"<r>"?}`
     * — the two trailing fields are omitted at their defaults (no-arg methods like
     * `tearDown` carry just `method`).
     */
    private fun payload(
        method: String,
        shouldPushOncePerSession: Boolean? = null,
        restriction: EventRestriction = EventRestriction.Unrestricted,
        fields: JsonObjectBuilder.() -> Unit = {},
    ): String = buildJsonObject {
        put("method", method)
        fields()
        if (shouldPushOncePerSession != null) {
            put("shouldPushOncePerSession", shouldPushOncePerSession)
        }
        if (restriction != EventRestriction.Unrestricted) {
            put("restriction", restriction.wireName)
        }
    }.toString()
}

/**
 * Maps the `Any` values the consumer signatures allow onto [JsonElement]s, recursing
 * into maps and collections so nested structures survive the wire as real JSON (the
 * consumer's `Map<String, Any>` gets objects/arrays back, not their `toString()`).
 * Non-finite floats aren't representable in JSON, so they degrade to strings.
 */
private fun toJsonElement(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JsonElement -> v
    is String -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Float -> if (v.isFinite()) JsonPrimitive(v) else JsonPrimitive(v.toString())
    is Double -> if (v.isFinite()) JsonPrimitive(v) else JsonPrimitive(v.toString())
    is Number -> JsonPrimitive(v)
    is Map<*, *> -> buildJsonObject {
        v.forEach { (k, value) -> put(k.toString(), toJsonElement(value)) }
    }
    is Iterable<*> -> buildJsonArray { v.forEach { add(toJsonElement(it)) } }
    is Array<*> -> buildJsonArray { v.forEach { add(toJsonElement(it)) } }
    else -> JsonPrimitive(v.toString())
}