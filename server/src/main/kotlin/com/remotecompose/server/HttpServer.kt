    package com.remotecompose.server

    import com.sun.net.httpserver.HttpExchange
    import com.sun.net.httpserver.HttpServer
    import kotlinx.serialization.json.Json
    import kotlinx.serialization.encodeToString
    import com.remotecompose.shared.LayoutConfig
    import java.io.ByteArrayOutputStream
    import java.net.InetSocketAddress
    import java.util.zip.ZipEntry
    import java.util.zip.ZipOutputStream

    /**
     * Lightweight HTTP server for the web editor to communicate with.
     * Uses JDK's built-in com.sun.net.httpserver — zero external dependencies.
     *
     * Endpoints:
     *   GET  /health         → 200 OK with server info
     *   POST /convert        → JSON body → binary .rc response
     *   POST /convert-batch  → JSON array → ZIP of .rc files
     */

    private val json = Json { ignoreUnknownKeys = true }

    fun startHttpServer(port: Int) {
        val server = HttpServer.create(InetSocketAddress(port), 0)

        // ─── Health Check ────────────────────────────────────────────
        server.createContext("/health") { exchange ->
            setCorsHeaders(exchange)
            if (exchange.requestMethod == "OPTIONS") {
                exchange.sendResponseHeaders(204, -1)
                return@createContext
            }

            val response = """{"status":"ok","version":"1.0.0","engine":"AndroidX Remote Compose"}"""
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.write(response.toByteArray())
            exchange.responseBody.close()
        }

        // ─── Single Conversion ───────────────────────────────────────
        server.createContext("/convert") { exchange ->
            setCorsHeaders(exchange)
            if (exchange.requestMethod == "OPTIONS") {
                exchange.sendResponseHeaders(204, -1)
                return@createContext
            }

            if (exchange.requestMethod != "POST") {
                sendError(exchange, 405, "Method not allowed. Use POST.")
                return@createContext
            }

            try {
                val body = exchange.requestBody.bufferedReader().readText()
                val config = json.decodeFromString<LayoutConfig>(body)
                val binary = buildDocument(config)
    
                exchange.responseHeaders.set("Content-Type", "application/octet-stream")
                exchange.responseHeaders.set("Content-Disposition", "attachment; filename=\"output.rc\"")
                exchange.sendResponseHeaders(200, binary.size.toLong())
                exchange.responseBody.write(binary)
                exchange.responseBody.close()

                println("✓ Converted config → ${binary.size} bytes")
            } catch (e: Exception) {
                sendError(exchange, 400, "Conversion error: ${e.message}")
                System.err.println("✗ Conversion failed: ${e.message}")
                e.printStackTrace()
            }
        }

        // ─── Batch Conversion ────────────────────────────────────────
        server.createContext("/convert-batch") { exchange ->
            setCorsHeaders(exchange)
            if (exchange.requestMethod == "OPTIONS") {
                exchange.sendResponseHeaders(204, -1)
                return@createContext
            }

            if (exchange.requestMethod != "POST") {
                sendError(exchange, 405, "Method not allowed. Use POST.")
                return@createContext
            }

            try {
                val body = exchange.requestBody.bufferedReader().readText()
                val configs = json.decodeFromString<List<LayoutConfig>>(body)

                // Create ZIP with all .rc files
                val zipBytes = ByteArrayOutputStream()
                ZipOutputStream(zipBytes).use { zip ->
                    configs.forEachIndexed { index, config ->
                        val name = config.name?.lowercase()?.replace(Regex("\\s+"), "_") ?: "screen_$index"
                        val binary = buildDocument(config)
                        zip.putNextEntry(ZipEntry("$name.rc"))
                        zip.write(binary)
                        zip.closeEntry()
                        println("  ✓ $name.rc → ${binary.size} bytes")
                    }
                }

                val result = zipBytes.toByteArray()
                exchange.responseHeaders.set("Content-Type", "application/zip")
                exchange.responseHeaders.set("Content-Disposition", "attachment; filename=\"remote_compose_screens.zip\"")
                exchange.sendResponseHeaders(200, result.size.toLong())
                exchange.responseBody.write(result)
                exchange.responseBody.close()

                println("✓ Batch converted ${configs.size} screens → ${result.size} bytes ZIP")
            } catch (e: Exception) {
                sendError(exchange, 400, "Batch conversion error: ${e.message}")
                System.err.println("✗ Batch conversion failed: ${e.message}")
                e.printStackTrace()
            }
        }

        server.executor = null
        server.start()

        println("""
        ╔══════════════════════════════════════════════════════════════╗
        ║  🚀 Remote Compose Server running on port $port              ║
        ║                                                              ║
        ║  Endpoints:                                                  ║
        ║    GET  http://localhost:$port/health         → Health check ║
        ║    POST http://localhost:$port/convert        → JSON → .rc   ║
        ║    POST http://localhost:$port/convert-batch  → JSON[] → ZIP ║
        ║                                                              ║
        ║  Open the web editor and point it to:                        ║
        ║    http://localhost:$port                                    ║
        ║                                                              ║
        ║  Press Ctrl+C to stop                                        ║
        ╚══════════════════════════════════════════════════════════════╝
        """.trimIndent())
    }

    private fun setCorsHeaders(exchange: HttpExchange) {
        exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
        exchange.responseHeaders.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        exchange.responseHeaders.set("Access-Control-Allow-Headers", "Content-Type")
        exchange.responseHeaders.set("Access-Control-Max-Age", "86400")
    }

    private fun sendError(exchange: HttpExchange, code: Int, message: String) {
        val errorJson = """{"error":"${message.replace("\"", "'")}"}"""
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(code, errorJson.toByteArray().size.toLong())
        exchange.responseBody.write(errorJson.toByteArray())
        exchange.responseBody.close()
    }
