package com.remotecompose.rc.feature.samples

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import com.remotecompose.rc.components.image.JarBlurImage
import com.remotecompose.rc.components.image.JarImage
import com.remotecompose.rc.core.RenderContext
import com.remotecompose.rc.core.dp
import com.remotecompose.rc.core.rcDocument
import com.remotecompose.rc.core.rsp

// ─── Demo screen ──────────────────────────────────────────────────────────────

fun JarImageScreen(ctx: RenderContext = RenderContext()): ByteArray = rcDocument(ctx) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(dp(24))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "JarImage — RC",
                color = 0xFFFFFFFF.toInt(),
                fontSize = 22.rsp,
                fontWeight = 700f,
            )

            Box(modifier = Modifier.fillMaxWidth().height(dp(16))) {}

            // Fit (default — mirrors ContentScale.Fit)
            JarImage(
                imageUrl = "https://example.com/sample.png",
                contentDescription = "Sample image",
                contentScale = RcContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(dp(180)),
            )

            Box(modifier = Modifier.fillMaxWidth().height(dp(12))) {}

            // Crop (mirrors ContentScale.Crop)
            JarImage(
                imageUrl = "https://example.com/sample.png",
                contentDescription = "Cropped image",
                contentScale = RcContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(dp(120)),
            )

            Box(modifier = Modifier.fillMaxWidth().height(dp(12))) {}

            // FillBounds (mirrors ContentScale.FillBounds)
            JarImage(
                imageUrl = "https://example.com/sample.png",
                contentDescription = "Fill bounds image",
                contentScale = RcContentScale.FillBounds,
                modifier = Modifier.size(dp(120), dp(80)),
            )

            Box(modifier = Modifier.fillMaxWidth().height(dp(12))) {}

            // BlurImage (no-op blur on RC — image rendered normally)
            JarBlurImage(
                imageUrl = "https://example.com/sample.png",
                blurRadiusDp = 20f,
                contentScale = RcContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(dp(120)),
            )
        }
    }
}
