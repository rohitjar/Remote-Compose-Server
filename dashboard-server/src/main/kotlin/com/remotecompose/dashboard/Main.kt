package com.remotecompose.dashboard

import com.remotecompose.shared.LayoutConfig
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Dashboard Server — JSON → binary .rc conversion for the web editor.
 *
 * Modes:
 *   HTTP server:   ./gradlew :dashboard-server:run --args="--serve 8081"
 *   Single file:   ./gradlew :dashboard-server:run --args="config.json output.rc"
 *   Batch convert: ./gradlew :dashboard-server:run --args="--dir input/ output/"
 */

private val json = Json { ignoreUnknownKeys = true }

fun main(args: Array<String>) {
    when (args.getOrNull(0)) {
        null, "--serve" -> {
            val port = args.getOrNull(1)?.toIntOrNull() ?: 8081
            startHttpServer(port)
        }

        "--dir" -> {
            if (args.size < 3) {
                System.err.println("Usage: --dir <input-dir> <output-dir>")
                return
            }
            batchConvert(args[1], args[2])
        }

        "--help", "-h" -> printUsage()

        else -> {
            val inputFile = File(args[0])
            if (!inputFile.exists()) {
                System.err.println("Error: Input file not found: ${args[0]}")
                return
            }
            val outputFile = if (args.size > 1) File(args[1]) else File(inputFile.nameWithoutExtension + ".rc")
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
        return
    }

    println("Batch converting ${jsonFiles.size} files from $inputDir → $outputDir\n")
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
    println("\nDone: $successCount/${jsonFiles.size} files converted successfully.")
}

private fun printUsage() {
    println(
        """
        Dashboard Server — JSON UI configs → binary .rc documents

        Usage:
          Single file:    ./gradlew :dashboard-server:run --args="config.json output.rc"
          Batch convert:  ./gradlew :dashboard-server:run --args="--dir input/ output/"
          HTTP server:    ./gradlew :dashboard-server:run --args="--serve 8081"
        """.trimIndent()
    )
}
