package com.remotecompose.rc.core

import kotlinx.serialization.json.JsonObject

/**
 * A server-driven screen, discovered at runtime via [java.util.ServiceLoader].
 *
 * Each :feature-* module implements this for the screens it owns and registers them in
 * `META-INF/services/com.remotecompose.rc.core.Screen`. The server discovers every
 * implementation on the classpath — it never imports or names individual screens, so
 * adding a screen requires no change to the server or any central registry.
 *
 * Implementations must have a public no-arg constructor (ServiceLoader requirement).
 */
interface Screen {
    /** Stable key — used as the HTTP endpoint path and the `--compose` CLI name. */
    val key: String

    /**
     * Bumped on every layout redesign. The binary is cached client-side keyed by this
     * version, so a bump is what makes consumers re-download the skeleton.
     */
    val layoutVersion: Int get() = 1

    /** Renders this screen's binary .rc document for the given device metrics. */
    fun render(ctx: RenderContext): ByteArray

    /**
     * Request-aware render. The default ignores [ScreenRequest.args] and delegates to
     * [render] with the envelope's metrics — the normal case, since screen ARGUMENTS
     * should parameterize data, not layout (that's what keeps the binary shared and
     * cacheable). Override only for the rare screen whose layout legitimately depends
     * on the request.
     */
    fun render(request: ScreenRequest): ByteArray = render(request.context.toRenderContext())

    /**
     * External data links to advertise in the manifest INSTEAD of the server's own
     * `/v1/screens/<key>/data`. The consumer calls every listed endpoint in parallel,
     * flattens each JSON response by path, and binds the document's `USER:` slots by those
     * flat keys — so a screen can be fed by existing product APIs (in their natural response
     * shape, `{success, data:{…}}` envelope and all) with zero SDUI-specific backend code.
     * Slot names in [render]'s document must be the flattened paths of those responses.
     * Null (the default) keeps the self-served data endpoint backed by [data].
     */
    val dataEndpoints: List<EndpointRef>? get() = null

    /**
     * Per-user values for the named `USER:` slots declared in [render]'s document — raw JSON
     * in whatever shape is natural; the consumer flattens it by path and binds slots by flat
     * key (a slot named `profile.name` reads `{profile:{name:…}}` — no type envelope, types
     * live in the binary). Served from `/v1/screens/<key>/data`; ignored when [dataEndpoints]
     * routes the screen to external APIs. [ScreenRequest.args] carries the screen's arguments
     * (e.g. an entity id baked into the host action that navigated here) — treat it as
     * untrusted input. Screens with no dynamic slots keep the empty default.
     */
    fun data(request: ScreenRequest): JsonObject = JsonObject(emptyMap())
}
