package com.remotecompose.rc.feature.samples

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.rsp
import androidx.compose.remote.creation.dsl.size
import com.remotecompose.rc.components.image.JarBlurImage
import com.remotecompose.rc.components.image.JarImage
import com.remotecompose.rc.core.rcDocument

// ─── Demo screen ──────────────────────────────────────────────────────────────

fun JarImageScreen(): ByteArray = rcDocument {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "JarImage — RC",
                color = 0xFFFFFFFF.toInt(),
                fontSize = 22.rsp,
                fontWeight = 700f,
            )

            Box(modifier = Modifier.fillMaxWidth().height(16f)) {}

            // Fit (default — mirrors ContentScale.Fit)
            JarImage(
                imageUrl = "https://example.com/sample.png",
                contentDescription = "Sample image",
                contentScale = RcContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(180f),
            )

            Box(modifier = Modifier.fillMaxWidth().height(12f)) {}

            // Crop (mirrors ContentScale.Crop)
            JarImage(
                imageUrl = "https://example.com/sample.png",
                contentDescription = "Cropped image",
                contentScale = RcContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(120f),
            )

            Box(modifier = Modifier.fillMaxWidth().height(12f)) {}

            // FillBounds (mirrors ContentScale.FillBounds)
            JarImage(
                imageUrl = "https://example.com/sample.png",
                contentDescription = "Fill bounds image",
                contentScale = RcContentScale.FillBounds,
                modifier = Modifier.size(120f, 80f),
            )

            Box(modifier = Modifier.fillMaxWidth().height(12f)) {}

            // BlurImage (no-op blur on RC — image rendered normally)
            JarBlurImage(
                imageUrl = "https://example.com/sample.png",
                blurRadiusDp = 20f,
                contentScale = RcContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(120f),
            )
        }
    }
}
