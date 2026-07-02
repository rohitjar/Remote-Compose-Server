package com.remotecompose.server

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.Header
import java.io.File

fun testInflate() {
    val file = File("/Users/rohitkumar/AndroidStudioProjects/RemoteCompose/app/src/main/assets/jar_primary_button.rc")
    val bytes = file.readBytes()
    println("File size: ${bytes.size}")
    println("First 40 bytes: ${bytes.take(40).joinToString(" ") { "%02X".format(it) }}")

    // Parse header manually
    val buffer = RemoteComposeBuffer.fromInputStream(bytes.inputStream())
    val wb = buffer.getBuffer()
    val apiLevel = Header.peekApiLevel(wb)
    println("peekApiLevel = $apiLevel (index after peek = ${wb.getIndex()})")

    wb.setIndex(0)
    try {
        val hdr = Header.readDirect(wb)
        println("Header profiles=${hdr.getProfiles()}, index after readDirect=${wb.getIndex()}")
    } catch (e: Exception) {
        println("readDirect failed: $e")
    }

    // Now try full inflate on a fresh buffer
    println("\nAttempting full inflate...")
    val buffer2 = RemoteComposeBuffer.fromInputStream(bytes.inputStream())
    val doc = CoreDocument()
    try {
        doc.initFromBuffer(buffer2)
        println("Success! width=${doc.width}, height=${doc.height}")
    } catch (e: Exception) {
        println("CRASH: $e")
        // Print just first 8 frames
        e.stackTrace.take(8).forEach { println("  at $it") }
    }
}
