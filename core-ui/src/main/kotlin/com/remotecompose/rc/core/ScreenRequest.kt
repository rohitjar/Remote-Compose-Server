package com.remotecompose.rc.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * The standard request envelope for parameterized screen endpoints.
 *
 * Every v1 link that declares `method: POST` receives exactly this shape as its body;
 * links that stay on GET receive the same information as query params (`density`, `width`,
 * `height`, plus `args` as URL-encoded JSON). One envelope for every screen, forever —
 * new screens with new argument schemas need zero client changes because the client
 * relays [args] as an opaque blob it never parses.
 *
 * - [args] — screen arguments (e.g. `{"txnId":"12345"}`). Authored server-side into the
 *   host action that navigates to this screen, relayed verbatim by the consumer.
 *   UNTRUSTED INPUT: it transited the client, so screens must authorize it (a txnId is a
 *   lookup key, never an identity claim).
 * - [context] — standard client capabilities (render metrics, locale, app version).
 */
@Serializable
data class ScreenRequest(
    val args: JsonObject = JsonObject(emptyMap()),
    val context: ClientContext = ClientContext(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(body: String): ScreenRequest = json.decodeFromString(serializer(), body)
    }
}

/** Client capabilities; every field optional so old clients omitting new ones keep working. */
@Serializable
data class ClientContext(
    val density: Float? = null,
    val widthDp: Int? = null,
    val heightDp: Int? = null,
    val safeAreaTop: Int? = null,
    val safeAreaBottom: Int? = null,
    val locale: String? = null,
    val appVersion: String? = null,
) {
    /** Maps the render-relevant subset onto [RenderContext], falling back to its defaults. */
    fun toRenderContext(): RenderContext {
        val default = RenderContext()
        return RenderContext(
            density = density ?: default.density,
            widthDp = widthDp ?: default.widthDp,
            heightDp = heightDp ?: default.heightDp,
            safeAreaTop = safeAreaTop ?: default.safeAreaTop,
            safeAreaBottom = safeAreaBottom ?: default.safeAreaBottom,
        )
    }
}
