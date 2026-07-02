package com.remotecompose.server

import com.remotecompose.rc.core.RenderContext
import java.io.File

/**
 * Remote Compose Server — server-driven-UI entry point.
 *
 * Modes:
 *   HTTP server (default):  ./gradlew :server:run --args="--serve 8080"
 *   Render to file (CLI):   ./gradlew :server:run --args="--compose profile out.rc"
 *   Inspect a .rc file:     ./gradlew :server:run --args="--test-inflate"
 *
 * (The JSON editor / DocumentBuilder path lives in the standalone :dashboard-server.)
 */
fun main(args: Array<String>) {
    when (args.getOrNull(0)) {
        "--test-inflate" -> testInflate()

        "--compose" -> compose(args)

        "--help", "-h" -> printUsage()

        null, "--serve" -> {
            val port = args.getOrNull(1)?.toIntOrNull() ?: 8080
            startScreenServer(port)
        }

        else -> printUsage()
    }
}

/** `--compose <screen> [output.rc]` — render a registered screen to a file using default metrics. */
private fun compose(args: Array<String>) {
    val name = args.getOrNull(1) ?: run {
        System.err.println("Usage: --compose <screen> [output.rc]")
        System.err.println("Available: ${ScreenRegistry.screens.keys.joinToString()}")
        return
    }
    val screen = ScreenRegistry.screens[name] ?: run {
        System.err.println("Unknown screen: '$name'")
        System.err.println("Available: ${ScreenRegistry.screens.keys.joinToString()}")
        return
    }
    val outputFile = File(args.getOrElse(2) { "$name.rc" })
    println("Composing: $name → ${outputFile.name}")
    val binary = screen.render(RenderContext())
    outputFile.writeBytes(binary)
    println("✓ Success! ${binary.size} bytes written to ${outputFile.absolutePath}")
}

private fun printUsage() {
    println(
        """
        Remote Compose Server (server-driven UI)

        Usage:
          HTTP server:      ./gradlew :server:run --args="--serve 8080"
          Render to file:   ./gradlew :server:run --args="--compose <screen> [out.rc]"

        Screens: ${ScreenRegistry.screens.keys.joinToString()}
        """.trimIndent()
    )
}
