package com.remotecompose.rc.core

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

    /** Renders this screen's binary .rc document for the given device metrics. */
    fun render(ctx: RenderContext): ByteArray
}
