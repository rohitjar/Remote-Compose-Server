package com.remotecompose.server

import com.remotecompose.rc.core.ClientContext
import com.remotecompose.rc.core.EndpointRef
import com.remotecompose.rc.core.RenderContext
import com.remotecompose.rc.core.Screen
import com.remotecompose.rc.core.ScreenManifest
import com.remotecompose.rc.core.ScreenRequest
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder

/**
 * Server-driven-UI HTTP server, manifest-first ("static API") design.
 *
 * The consumer hardcodes exactly ONE URL shape — the manifest:
 *
 *   GET /v1/screens/{key}                       → ScreenManifest JSON (layoutVersion + links)
 *
 * Every other URL is taken from the manifest verbatim:
 *
 *   GET  /v1/screens/{key}/binary/{version}?density=&width=&height=
 *          → binary .rc skeleton. Version-pinned → served with `Cache-Control: immutable`.
 *            A stale {version} answers 409 so the consumer re-fetches the manifest.
 *   GET  /v1/screens/{key}/data                 → per-user data JSON (raw shape; consumer
 *          flattens by path and binds USER: slots by flat key). Screens backed by real
 *          product APIs skip this: their manifest lists external data links instead.
 *   POST /v1/analytics                          → host-action analytics events (ingest stub)
 *
 * Ad-hoc document authoring (no registered Screen involved):
 *
 *   POST /v1/parse                              → body is a RemoteCompose JSON document
 *          (the official androidx-main JSON format) → binary .rc bytes, parsed by
 *          androidx.compose.remote.creation.json.RemoteComposeJsonParser.
 *   POST /v1/parse/{name}                       → same body, but SAVES the binary as
 *          {name}.rc in the documents dir ($RC_DOCUMENTS_DIR, default ./rc-documents)
 *          and returns JSON {name, bytes, path, url}.
 *   GET  /v1/documents                          → list saved documents (JSON)
 *   GET  /v1/documents/{name}.rc                → serve a saved binary
 *
 * Legacy convenience endpoint (CLI / manual curl, not used by the app):
 *
 *   GET /{key}?density=&width=&height=          → binary .rc
 *
 * Endpoints are derived from [ScreenRegistry], so adding a screen exposes its full v1
 * surface automatically — no per-endpoint wiring.
 */
fun startScreenServer(port: Int) {
    val server = HttpServer.create(InetSocketAddress(port), 0)

    server.createContext("/health") { exchange ->
        exchange.cors()
        if (exchange.isPreflight()) return@createContext
        exchange.respondJson(200, """{"status":"ok","screens":${ScreenRegistry.screens.keys.jsonArray()}}""")
    }

    server.createContext("/v1/analytics") { exchange -> exchange.serveAnalytics() }

    server.createContext("/v1/parse") { exchange -> exchange.serveParse() }

    server.createContext("/v1/documents") { exchange -> exchange.serveDocument() }

    ScreenRegistry.screens.forEach { (key, screen) ->
        // JDK HttpServer routes by longest prefix; the deeper contexts win over the manifest.
        server.createContext("/v1/screens/$key") { exchange -> exchange.serveManifest(key, screen) }
        server.createContext("/v1/screens/$key/binary") { exchange -> exchange.serveBinary(key, screen) }
        server.createContext("/v1/screens/$key/data") { exchange -> exchange.serveData(key, screen) }
        // Legacy binary endpoint, kept for the CLI and manual inspection.
        server.createContext("/$key") { exchange -> exchange.serveLegacyBinary(key, screen) }
    }

    server.executor = null
    server.start()

    val endpoints = ScreenRegistry.screens.keys.sorted().joinToString("\n") {
        "    GET  http://localhost:$port/v1/screens/$it   (manifest → binary/{v}, data)"
    }
    println(
        """
        🚀 Screen server running on port $port

        Consumer API (manifest-first):
        $endpoints
            POST http://localhost:$port/v1/analytics
            POST http://localhost:$port/v1/parse   (RemoteCompose JSON document → .rc binary)
            GET  http://localhost:$port/health

        Binary query params (optional): density, width, height
        e.g. curl http://localhost:$port/v1/screens/profile

        Press Ctrl+C to stop
        """.trimIndent()
    )
}

// ─── v1: manifest ─────────────────────────────────────────────────────────────

private fun HttpExchange.serveManifest(key: String, screen: Screen) {
    cors()
    if (isPreflight()) return
    // This context is the prefix fallback for /v1/screens/{key}/*; only the exact path is the manifest.
    if (requestURI.path.trimEnd('/') != "/v1/screens/$key") {
        respondJson(404, """{"error":"Unknown resource ${requestURI.path}"}""")
        return
    }
    serveGetJsonBody {
        val base = publicBaseUrl()
        // Both binary and data accept GET (query params) AND POST (the standard
        // {args, context} envelope); the method advertised here is what shipped clients
        // will use — flipping a verb is a server-side change, no app release.
        // Binary stays GET so its version-pinned URL remains cacheable at every layer.
        // Data is a LIST: screens backed by real product APIs advertise those links
        // (Screen.dataEndpoints); the rest fall back to the self-served /data endpoint.
        ScreenManifest(
            screen = key,
            layoutVersion = screen.layoutVersion,
            binary = EndpointRef("$base/v1/screens/$key/binary/${screen.layoutVersion}"),
            data = screen.dataEndpoints
                ?: listOf(EndpointRef("$base/v1/screens/$key/data", method = "POST")),
            analytics = EndpointRef("$base/v1/analytics", method = "POST"),
        ).toJson()
    }
}

/**
 * Builds the [ScreenRequest] from either transport:
 *  - POST → body is the standard `{args, context}` envelope.
 *  - GET  → `density`/`width`/`height` query params become the context; an optional
 *           `args` query param carries URL-encoded JSON.
 */
private fun HttpExchange.screenRequest(): ScreenRequest {
    if (requestMethod == "POST") {
        val body = requestBody.readBytes().decodeToString()
        return if (body.isBlank()) ScreenRequest() else ScreenRequest.fromJson(body)
    }
    val q = requestURI.rawQuery.parseQuery()
    val argsJson = q["args"]?.let { URLDecoder.decode(it, Charsets.UTF_8) }
    val args = argsJson?.let { ScreenRequest.fromJson("""{"args":$it}""").args }
    return ScreenRequest(
        args = args ?: ScreenRequest().args,
        context = ClientContext(
            density = q["density"]?.toFloatOrNull(),
            widthDp = q["width"]?.toIntOrNull(),
            heightDp = q["height"]?.toIntOrNull(),
            safeAreaTop = q["safeAreaTop"]?.toIntOrNull(),
            safeAreaBottom = q["safeAreaBottom"]?.toIntOrNull(),
        ),
    )
}

// ─── v1: per-user data (GET or POST envelope) ─────────────────────────────────

private fun HttpExchange.serveData(key: String, screen: Screen) {
    cors()
    if (isPreflight()) return
    if (requestMethod != "GET" && requestMethod != "POST") {
        respondJson(405, """{"error":"Method not allowed. Use GET or POST."}""")
        return
    }
    try {
        val request = screenRequest()
        val json = screen.data(request).toString()
        respondJson(200, json)
        println("✓ $requestMethod /v1/screens/$key/data args=${request.args} → ${json.length} bytes")
    } catch (e: Exception) {
        respondJson(400, """{"error":"${e.message?.replace("\"", "'")}"}""")
        System.err.println("✗ /v1/screens/$key/data failed: ${e.message}")
    }
}

/**
 * Base URL for the links we hand out, reconstructed from the request so the manifest works
 * unchanged through localhost, adb-reverse, LAN IPs, or an HTTPS tunnel.
 *
 * When the server sits behind a path-prefixed reverse proxy (e.g. nginx routing
 * `https://host/rc/...` → `localhost:8091/...`), the prefix the proxy strips must be
 * restored, or the links point at the wrong upstream. Two ways, in precedence order:
 *  1. `RC_PUBLIC_BASE_URL` env var — used verbatim (e.g. `https://host/rc`); no proxy
 *     config needed.
 *  2. `X-Forwarded-Prefix` header — set by the proxy (`proxy_set_header X-Forwarded-Prefix /rc;`).
 */
private fun HttpExchange.publicBaseUrl(): String {
    System.getenv("RC_PUBLIC_BASE_URL")?.takeIf { it.isNotBlank() }?.let { return it.trimEnd('/') }
    val proto = requestHeaders.getFirst("X-Forwarded-Proto") ?: "http"
    val host = requestHeaders.getFirst("Host") ?: "localhost"
    val prefix = requestHeaders.getFirst("X-Forwarded-Prefix")
        ?.trim('/')?.takeIf { it.isNotEmpty() }?.let { "/$it" }.orEmpty()
    return "$proto://$host$prefix"
}

// ─── v1: version-pinned binary ────────────────────────────────────────────────

private fun HttpExchange.serveBinary(key: String, screen: Screen) {
    cors()
    if (isPreflight()) return
    if (requestMethod != "GET" && requestMethod != "POST") {
        respondJson(405, """{"error":"Method not allowed. Use GET or POST."}""")
        return
    }
    val requested = requestURI.path.removePrefix("/v1/screens/$key/binary").trim('/')
    val version = requested.toIntOrNull()
    if (version == null) {
        respondJson(400, """{"error":"Missing or invalid version in path; expected /v1/screens/$key/binary/{version}"}""")
        return
    }
    // The URL identifies immutable bytes; a stale version means the client's manifest is
    // outdated — tell it to re-bootstrap rather than serving a layout it didn't expect.
    if (version != screen.layoutVersion) {
        respondJson(409, """{"error":"Stale layoutVersion $version","currentLayoutVersion":${screen.layoutVersion}}""")
        return
    }
    try {
        val request = screenRequest()
        val binary = screen.render(request)
        val ctx = request.context.toRenderContext()
        responseHeaders.set("Content-Type", "application/octet-stream")
        responseHeaders.set("Content-Disposition", "attachment; filename=\"$key-v$version.rc\"")
        // Only GET responses are cacheable; POST is for callers that need the envelope.
        if (requestMethod == "GET") {
            responseHeaders.set("Cache-Control", "public, max-age=31536000, immutable")
        }
        sendResponseHeaders(200, binary.size.toLong())
        responseBody.use { it.write(binary) }
        println("✓ $requestMethod /v1/screens/$key/binary/$version density=${ctx.density} ${ctx.widthDp}x${ctx.heightDp}dp → ${binary.size} bytes")
    } catch (e: Exception) {
        respondJson(400, """{"error":"${e.message?.replace("\"", "'")}"}""")
        System.err.println("✗ /v1/screens/$key/binary failed: ${e.message}")
    }
}

// ─── v1: RemoteCompose JSON document → binary ─────────────────────────────────

/** Where `POST /v1/parse/{name}` saves binaries and `GET /v1/documents/{name}.rc` serves them from. */
private val documentsDir: File
    get() = File(System.getenv("RC_DOCUMENTS_DIR")?.takeIf { it.isNotBlank() } ?: "rc-documents")

private val documentNameRegex = Regex("[A-Za-z0-9_-]+")

/**
 * Parses a RemoteCompose JSON document (the official androidx-main JSON format —
 * `{header, resources, root}`) straight into the binary .rc encoding via
 * [parseRemoteComposeJson], which also understands URL `bitmap` ids.
 *
 * `POST /v1/parse` returns the binary directly; `POST /v1/parse/{name}` saves it as
 * `{name}.rc` in [documentsDir] and returns JSON with the saved path and serving URL.
 */
private fun HttpExchange.serveParse() {
    cors()
    if (isPreflight()) return
    if (requestMethod != "POST") {
        respondJson(405, """{"error":"Method not allowed. Use POST."}""")
        return
    }
    try {
        val name = requestURI.path.removePrefix("/v1/parse").trim('/').removeSuffix(".rc")
        if (name.isNotEmpty() && !name.matches(documentNameRegex)) {
            respondJson(400, """{"error":"Invalid document name '$name'; use letters, digits, _ or -"}""")
            return
        }
        val json = requestBody.readBytes().decodeToString()
        if (json.isBlank()) {
            respondJson(400, """{"error":"Empty body; expected a RemoteCompose JSON document"}""")
            return
        }
        val binary = parseRemoteComposeJson(json)
        if (name.isEmpty()) {
            responseHeaders.set("Content-Type", "application/octet-stream")
            responseHeaders.set("Content-Disposition", "attachment; filename=\"document.rc\"")
            sendResponseHeaders(200, binary.size.toLong())
            responseBody.use { it.write(binary) }
            println("✓ POST /v1/parse ← ${json.length} chars JSON → ${binary.size} bytes")
            return
        }
        documentsDir.mkdirs()
        val file = File(documentsDir, "$name.rc")
        file.writeBytes(binary)
        val url = "${publicBaseUrl()}/v1/documents/$name.rc"
        respondJson(
            200,
            """{"name":"$name","bytes":${binary.size},"path":"${file.absolutePath}","url":"$url"}"""
        )
        println("✓ POST /v1/parse/$name ← ${json.length} chars JSON → ${binary.size} bytes saved to ${file.absolutePath}")
    } catch (e: Exception) {
        respondJson(400, """{"error":"${e.message?.replace("\"", "'")}"}""")
        System.err.println("✗ /v1/parse failed: ${e.message}")
    }
}

/**
 * `GET /v1/documents` lists saved documents; `GET /v1/documents/{name}.rc` serves one.
 * Names are validated against [documentNameRegex], which also rules out path traversal.
 */
private fun HttpExchange.serveDocument() {
    cors()
    if (isPreflight()) return
    if (requestMethod != "GET") {
        respondJson(405, """{"error":"Method not allowed. Use GET."}""")
        return
    }
    val name = requestURI.path.removePrefix("/v1/documents").trim('/').removeSuffix(".rc")
    if (name.isEmpty()) {
        val docs = documentsDir.listFiles { f -> f.extension == "rc" }
            ?.map { it.nameWithoutExtension }?.sorted() ?: emptyList()
        respondJson(200, """{"documents":${docs.jsonArray()}}""")
        return
    }
    if (!name.matches(documentNameRegex)) {
        respondJson(400, """{"error":"Invalid document name '$name'"}""")
        return
    }
    val file = File(documentsDir, "$name.rc")
    if (!file.isFile) {
        respondJson(404, """{"error":"No saved document named '$name'"}""")
        return
    }
    val binary = file.readBytes()
    responseHeaders.set("Content-Type", "application/octet-stream")
    responseHeaders.set("Content-Disposition", "attachment; filename=\"$name.rc\"")
    sendResponseHeaders(200, binary.size.toLong())
    responseBody.use { it.write(binary) }
    println("✓ GET /v1/documents/$name.rc → ${binary.size} bytes")
}

// ─── v1: analytics ingest (stub) ──────────────────────────────────────────────

/**
 * Accepts the JSON payloads baked by the authoring analytics DSL (HostActionAnalyticsApi)
 * and forwarded by the consumer. Stub implementation: logs and acknowledges — swap the
 * println for a queue/warehouse write without touching the consumer.
 */
private fun HttpExchange.serveAnalytics() {
    cors()
    if (isPreflight()) return
    if (requestMethod != "POST") {
        respondJson(405, """{"error":"Method not allowed. Use POST."}""")
        return
    }
    val body = requestBody.readBytes().decodeToString()
    println("📊 /v1/analytics ← $body")
    respondJson(202, """{"accepted":true}""")
}

// ─── legacy binary (CLI / manual) ─────────────────────────────────────────────

private fun HttpExchange.serveLegacyBinary(key: String, screen: Screen) {
    cors()
    if (isPreflight()) return
    if (requestMethod != "GET") {
        respondJson(405, """{"error":"Method not allowed. Use GET."}""")
        return
    }
    try {
        val ctx = renderContext()
        val binary = screen.render(ctx)
        responseHeaders.set("Content-Type", "application/octet-stream")
        responseHeaders.set("Content-Disposition", "attachment; filename=\"$key.rc\"")
        sendResponseHeaders(200, binary.size.toLong())
        responseBody.use { it.write(binary) }
        println("✓ /$key (legacy) density=${ctx.density} ${ctx.widthDp}x${ctx.heightDp}dp → ${binary.size} bytes")
    } catch (e: Exception) {
        respondJson(400, """{"error":"${e.message?.replace("\"", "'")}"}""")
        System.err.println("✗ /$key failed: ${e.message}")
    }
}

// ─── shared plumbing ──────────────────────────────────────────────────────────

/** GET-only JSON endpoint with CORS; body built lazily so errors surface as 500 JSON. */
private fun HttpExchange.serveGetJson(body: () -> String) {
    cors()
    if (isPreflight()) return
    serveGetJsonBody(body)
}

private fun HttpExchange.serveGetJsonBody(body: () -> String) {
    if (requestMethod != "GET") {
        respondJson(405, """{"error":"Method not allowed. Use GET."}""")
        return
    }
    try {
        val json = body()
        respondJson(200, json)
        println("✓ ${requestURI.path} → ${json.length} bytes")
    } catch (e: Exception) {
        respondJson(500, """{"error":"${e.message?.replace("\"", "'")}"}""")
        System.err.println("✗ ${requestURI.path} failed: ${e.message}")
    }
}

/** Parses `density` / `width` / `height` query params into a [RenderContext], falling back to defaults. */
private fun HttpExchange.renderContext(): RenderContext {
    val q = requestURI.rawQuery.parseQuery()
    val default = RenderContext()
    return RenderContext(
        density = q["density"]?.toFloatOrNull() ?: default.density,
        widthDp = q["width"]?.toIntOrNull() ?: default.widthDp,
        heightDp = q["height"]?.toIntOrNull() ?: default.heightDp,
        safeAreaTop = q["safeAreaTop"]?.toIntOrNull() ?: default.safeAreaTop,
        safeAreaBottom = q["safeAreaBottom"]?.toIntOrNull() ?: default.safeAreaBottom
    )
}

private fun String?.parseQuery(): Map<String, String> =
    this?.split("&")
        ?.mapNotNull { it.split("=", limit = 2).takeIf { p -> p.size == 2 } }
        ?.associate { (k, v) -> k to v }
        ?: emptyMap()

private fun HttpExchange.cors() {
    responseHeaders.set("Access-Control-Allow-Origin", "*")
    responseHeaders.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
    responseHeaders.set("Access-Control-Allow-Headers", "Content-Type")
}

private fun HttpExchange.isPreflight(): Boolean {
    if (requestMethod != "OPTIONS") return false
    sendResponseHeaders(204, -1)
    close()
    return true
}

private fun HttpExchange.respondJson(code: Int, body: String) {
    val bytes = body.toByteArray()
    responseHeaders.set("Content-Type", "application/json")
    sendResponseHeaders(code, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}

private fun Collection<String>.jsonArray(): String =
    sorted().joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
