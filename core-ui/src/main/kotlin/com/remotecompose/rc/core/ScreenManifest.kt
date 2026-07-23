package com.remotecompose.rc.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The bootstrap document for a screen — the ONLY thing a consumer constructs a URL for.
 *
 * `GET /v1/screens/{key}` returns this; every other URL (binary, data, analytics) is taken
 * from it verbatim and treated as opaque. That indirection is what lets the backend move
 * artifacts around — binaries to a CDN, data to another service, new endpoints — without
 * any client change.
 *
 * The binary URL is pinned to [layoutVersion] (`…/binary/{version}`), so it identifies
 * immutable bytes and can be cached forever at any layer; a redesign changes the URL.
 * Consumers append their render metrics (`density`/`width`/`height`) as query params.
 */
@Serializable
data class ScreenManifest(
    val screen: String,
    val layoutVersion: Int,
    val binary: EndpointRef,
    val data: List<EndpointRef>,
    val analytics: EndpointRef,
) {
    fun toJson(): String = Json.encodeToString(serializer(), this)
}

/** A link the consumer follows verbatim. */
@Serializable
data class EndpointRef(
    val url: String,
    val method: String = "GET",
)
