package com.remotecompose.server

import com.remotecompose.rc.core.RenderContext
import java.io.File

/** Default output directory for `--compose` when no output path is given. */
private const val DEFAULT_OUTPUT_DIR = "/Users/rohitkumar/AndroidStudioProjects/RemoteCompose/app/src/main/assets"

/**
 * Remote Compose Server — server-driven-UI entry point.
 *
 * Modes:
 *   HTTP server (default):  ./gradlew :server:run --args="--serve 8080"
 *   Render to file (CLI):   ./gradlew :server:run --args="--compose profile out.rc --density 3.0"
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

/**
 * `--compose <screen> [output.rc] [--density D] [--width W] [--height H]`
 * — render a registered screen to a file, optionally overriding [RenderContext] metrics.
 */
private fun compose(args: Array<String>) {
    val name = args.getOrNull(1) ?: run {
        System.err.println("Usage: --compose <screen> [output.rc] [--density D] [--width W] [--height H]")
        System.err.println("Available: ${ScreenRegistry.screens.keys.joinToString()}")
        return
    }
    val screen = ScreenRegistry.screens[name] ?: run {
        System.err.println("Unknown screen: '$name'")
        System.err.println("Available: ${ScreenRegistry.screens.keys.joinToString()}")
        return
    }
    val positional = args.drop(1).filterNot { it.startsWith("--") }
    val outputFile = File(positional.getOrElse(1) { "$DEFAULT_OUTPUT_DIR/$name.rc" })
    val flags = parseFlags(args)
    val default = RenderContext()
    val ctx = RenderContext(
        density = flags["density"]?.toFloatOrNull() ?: default.density,
        widthDp = flags["width"]?.toIntOrNull() ?: default.widthDp,
        heightDp = flags["height"]?.toIntOrNull() ?: default.heightDp,
    )
    println("Composing: $name → ${outputFile.name} (density=${ctx.density} ${ctx.widthDp}x${ctx.heightDp}dp)")
    val binary = screen.render(ctx)
    outputFile.writeBytes(binary)
    println("✓ Success! ${binary.size} bytes written to ${outputFile.absolutePath}")
}

/** Parses `--flag value` pairs out of the raw CLI args, e.g. `--density 3.0`. */
private fun parseFlags(args: Array<String>): Map<String, String> =
    args.withIndex()
        .filter { (_, a) -> a.startsWith("--") }
        .mapNotNull { (i, a) -> args.getOrNull(i + 1)?.let { a.removePrefix("--") to it } }
        .toMap()

private fun printUsage() {
    println(
        """
        Remote Compose Server (server-driven UI)

        Usage:
          HTTP server:      ./gradlew :server:run --args="--serve 8080"
          Render to file:   ./gradlew :server:run --args="--compose <screen> [out.rc] [--density D] [--width W] [--height H]"

        Screens: ${ScreenRegistry.screens.keys.joinToString()}
        """.trimIndent()
    )
}
