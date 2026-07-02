package com.remotecompose.server

import com.remotecompose.shared.LayoutConfig
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Remote Compose Server — Entry Point
 *
 * Modes:
 *   1. CLI single file:  ./gradlew run --args="input.json output.rc"
 *   2. CLI batch:         ./gradlew run --args="--dir input/ output/"
 *   3. HTTP server:       ./gradlew run --args="--serve 8080"
 *   4. HTTP server (default port): ./gradlew run --args="--serve"
 */

private val json = Json { ignoreUnknownKeys = true }

fun main(args: Array<String>) {
    if (args.getOrNull(0) == "--test-inflate") {
        testInflate()
        return
    }
    if (args.isEmpty()) {
        printUsage()
        // Default: start HTTP server on port 8080
        println("\nNo arguments provided. Starting HTTP server on default port 8080...\n")
        startHttpServer(8080)
        return
    }

    when (args[0]) {
        "--serve" -> {
            val port = args.getOrNull(1)?.toIntOrNull() ?: 8080
            startHttpServer(port)
        }

        "--compose" -> {
            // --compose <name> [output.rc]
            // Runs a registered composable function and writes the binary output.
            val name = args.getOrNull(1) ?: run {
                System.err.println("Usage: --compose <name> [output.rc]")
                System.err.println("Available: ${COMPOSABLE_REGISTRY.keys.joinToString()}")
                System.exit(1); return
            }
            val builder = COMPOSABLE_REGISTRY[name] ?: run {
                System.err.println("Unknown composable: '$name'")
                System.err.println("Available: ${COMPOSABLE_REGISTRY.keys.joinToString()}")
                System.exit(1); return
            }
            val outputFile = File(args.getOrElse(2) {
                "${System.getProperty("user.home")}/AndroidStudioProjects/RemoteCompose/app/src/main/assets/$name.rc"
            })
            println("Composing: $name → ${outputFile.name}")
            val binary = builder()
            outputFile.writeBytes(binary)
            println("✓ Success! ${binary.size} bytes written to ${outputFile.absolutePath}")
        }

        "--dir" -> {
            if (args.size < 3) {
                System.err.println("Usage: --dir <input-dir> <output-dir>")
                System.exit(1)
            }
            batchConvert(args[1], args[2])
        }

        "--help", "-h" -> {
            printUsage()
        }

        else -> {
            // Single file mode: input.json [output.rc]
            val inputFile = File(args[0])
            if (!inputFile.exists()) {
                System.err.println("Error: Input file not found: ${args[0]}")
                System.exit(1)
            }

            val outputFile = if (args.size > 1) {
                File(args[1])
            } else {
                File(inputFile.nameWithoutExtension + ".rc")
            }

            convertSingleFile(inputFile, outputFile)
        }
    }
}

private fun convertSingleFile(input: File, output: File) {
    println("Converting: ${input.name} → ${output.name}")

    val config = json.decodeFromString<LayoutConfig>(input.readText())
    val binary = buildDocument(config)
    output.writeBytes(binary)

    println("✓ Success! ${binary.size} bytes written to ${output.absolutePath}")
}

private fun batchConvert(inputDir: String, outputDir: String) {
    val input = File(inputDir)
    val output = File(outputDir)
    output.mkdirs()

    val jsonFiles = input.listFiles { f -> f.extension == "json" }
    if (jsonFiles.isNullOrEmpty()) {
        System.err.println("No .json files found in $inputDir")
        System.exit(1)
    }

    println("Batch converting ${jsonFiles.size} files from $inputDir → $outputDir")
    println()

    var successCount = 0
    for (file in jsonFiles) {
        try {
            val config = json.decodeFromString<LayoutConfig>(file.readText())
            val binary = buildDocument(config)
            val outputFile = File(output, file.nameWithoutExtension + ".rc")
            outputFile.writeBytes(binary)
            println("  ✓ ${file.name} → ${outputFile.name} (${binary.size} bytes)")
            successCount++
        } catch (e: Exception) {
            System.err.println("  ✗ ${file.name}: ${e.message}")
        }
    }

    println()
    println("Done: $successCount/${jsonFiles.size} files converted successfully.")
}

private fun printUsage() {
    println("""
    ┌──────────────────────────────────────────────────────────────┐
    │  Remote Compose Server                                       │
    │  Converts JSON UI configs → binary .rc documents             │
    │  using AndroidX Remote Compose                               │
    ├──────────────────────────────────────────────────────────────┤
    │                                                              │
    │  Usage:                                                      │
    │                                                              │
    │    Single file:                                              │
    │      ./gradlew run --args="config.json output.rc"            │
    │                                                              │
    │    Batch convert:                                            │
    │      ./gradlew run --args="--dir input/ output/"             │
    │                                                              │
    │    HTTP server:                                              │
    │      ./gradlew run --args="--serve 8080"                     │
    │      ./gradlew run --args="--serve"      (default: 8080)     │
    │                                                              │
    │    No args (default):                                        │
    │      Starts HTTP server on port 8080                         │
    │                                                              │
    └──────────────────────────────────────────────────────────────┘
    """.trimIndent())
}
