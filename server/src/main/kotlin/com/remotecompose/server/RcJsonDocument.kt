@file:Suppress("RestrictedApi")

package com.remotecompose.server

import androidx.compose.remote.creation.JvmRcPlatformServices
import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.json.RemoteComposeJsonParser
import androidx.compose.remote.creation.json.UrlBitmapSupport

/**
 * Parses a RemoteCompose JSON document (the official androidx-main `{header, resources, root}`
 * format) into the binary .rc encoding.
 *
 * Replicates [RemoteComposeJsonParser.parse]'s static factory so [UrlBitmapSupport] can be
 * installed first — the stock `bitmap` component crashes on URL ids, which is the only image
 * form a JSON-authoring client can express. Shared by the `--parse` CLI mode and the
 * `POST /v1/parse` endpoint.
 */
fun parseRemoteComposeJson(json: String): ByteArray {
    val apiLevel = RemoteComposeJsonParser.parseApiLevel(json)
    val tags = RemoteComposeJsonParser.parseHeaderOnly(json)
    val writer = RemoteComposeWriter(JvmRcPlatformServices(), apiLevel, *tags)
    val parser = RemoteComposeJsonParser(writer)
    UrlBitmapSupport.install(parser)
    parser.parse(json)
    return writer.encodeToByteArray()
}