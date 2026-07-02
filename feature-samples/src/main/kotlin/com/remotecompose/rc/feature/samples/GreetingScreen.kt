package com.remotecompose.rc.feature.samples

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.verticalScroll
import com.remotecompose.rc.core.RenderContext
import com.remotecompose.rc.core.dp
import com.remotecompose.rc.core.rcDocument
import com.remotecompose.rc.core.rdp
import com.remotecompose.rc.core.rsp


fun GreetingScreen(ctx: RenderContext = RenderContext()): ByteArray = rcDocument(ctx) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(0xFFF8F6FC.toInt()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.rdp)
                .verticalScroll(),
        ) {
            Text(
                text = "Hello from RcScope!",
                color = 0xFF4A148C.toInt(),
                fontSize = 28.rsp,
                fontWeight = 700f,
            )

            Box(modifier = Modifier.fillMaxWidth().height(dp(16))) {}

            Text(
                text = "This screen was authored as a Kotlin composable function and compiled to a binary .rc document on the JVM server.",
                color = 0xFF5B5675.toInt(),
                fontSize = 14.rsp,
            )

            Box(modifier = Modifier.fillMaxWidth().height(dp(24))) {}

            // Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(0xFFFFFFFF.toInt())
                    .padding(dp(16)),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Why RcScope?",
                        color = 0xFF1A1A2E.toInt(),
                        fontSize = 18.rsp,
                        fontWeight = 600f,
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(dp(8))) {}
                    Text(
                        text = "Write composable-style layouts in pure Kotlin — no JSON required. The DSL maps directly to the binary RC format.",
                        color = 0xFF4B5563.toInt(),
                        fontSize = 13.rsp,
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(dp(16))) {}

            Text(
                text = "Server-driven UI • Binary format • Zero JSON parsing",
                color = 0xFF7C3AED.toInt(),
                fontSize = 13.rsp,
            )
        }
    }
}
