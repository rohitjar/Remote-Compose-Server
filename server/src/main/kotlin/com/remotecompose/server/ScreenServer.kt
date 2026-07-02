package com.remotecompose.server

import com.remotecompose.rc.core.RenderContext
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

/**
 * Server-driven-UI HTTP server. Exposes one endpoint per registered screen:
 *
 *   GET /<screen>?density=<float>&width=<dp>&height=<dp>   → binary .rc document
 *
 * e.g. GET /profile?density=3.0&width=360&height=800
 *
 * density/width/height are optional; each falls back to the [RenderContext] default.
 * Endpoints are derived from [COMPOSABLE_REGISTRY], so adding a screen there exposes it
 * automatically — no per-endpoint wiring.
 */
fun startScreenServer(port: Int) {
    val server = HttpServer.create(InetSocketAddress(port), 0)

    server.createContext("/health") { exchange ->
        exchange.cors()
        if (exchange.isPreflight()) return@createContext
        exchange.respondJson(200, """{"status":"ok","screens":${ScreenRegistry.screens.keys.jsonArray()}}""")
    }

    ScreenRegistry.screens.forEach { (key, screen) ->
        server.createContext("/$key") { exchange -> exchange.serveScreen(key, screen::render) }
    }

    server.executor = null
    server.start()

    val endpoints = ScreenRegistry.screens.keys.sorted().joinToString("\n") { "    GET  http://localhost:$port/$it" }
    println(
        """
        🚀 Screen server running on port $port

        Endpoints:
            GET  http://localhost:$port/health
        $endpoints

        Query params (optional): density, width, height
        e.g. http://localhost:$port/profile?density=3.0&width=360&height=800

        Press Ctrl+C to stop
        """.trimIndent()
    )
}

private fun HttpExchange.serveScreen(key: String, factory: (RenderContext) -> ByteArray) {
    cors()
    if (isPreflight()) return
    if (requestMethod != "GET") {
        respondJson(405, """{"error":"Method not allowed. Use GET."}""")
        return
    }
    try {
        val ctx = renderContext()
        val binary = factory(ctx)
        responseHeaders.set("Content-Type", "application/octet-stream")
        responseHeaders.set("Content-Disposition", "attachment; filename=\"$key.rc\"")
        sendResponseHeaders(200, binary.size.toLong())
        responseBody.use { it.write(binary) }
        println("✓ /$key density=${ctx.density} ${ctx.widthDp}x${ctx.heightDp}dp → ${binary.size} bytes")
    } catch (e: Exception) {
        respondJson(400, """{"error":"${e.message?.replace("\"", "'")}"}""")
        System.err.println("✗ /$key failed: ${e.message}")
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
    )
}

private fun String?.parseQuery(): Map<String, String> =
    this?.split("&")
        ?.mapNotNull { it.split("=", limit = 2).takeIf { p -> p.size == 2 } }
        ?.associate { (k, v) -> k to v }
        ?: emptyMap()

private fun HttpExchange.cors() {
    responseHeaders.set("Access-Control-Allow-Origin", "*")
    responseHeaders.set("Access-Control-Allow-Methods", "GET, OPTIONS")
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
