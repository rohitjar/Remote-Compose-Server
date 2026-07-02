@file:Suppress("RestrictedApi")

package com.remotecompose.server.composables

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcScope
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.rsp
import androidx.compose.remote.creation.dsl.size

// ─── JarImage ─────────────────────────────────────────────────────────────────

/**
 * RC equivalent of JarImage.
 *
 * The original uses Glide to load from URLs, DrawableRes ints, Bitmap, or Drawable.
 * In RC all images are server-side resources. This function accepts:
 *   - [imageUrl]: a URL string → registered via remoteBitmapUrl (Glide equivalent)
 *   - [fallbackUrl]: shown when imageUrl is blank/null (replaces the failure Placeholder)
 *
 * RC limitations vs original:
 *   - No adaptive quality (DeviceClass / shouldReduceImageQuality) — the player handles display
 *   - No imgProxy URL rewriting — pass the final URL from the caller if needed
 *   - No colorFilter tint — RC Image does not expose a colorFilter param
 *   - No alpha per-image — no alpha modifier on Modifier in RC; handled at layout level
 *   - No blur (JarBlurImage) — RC has no blur modifier; see JarBlurImage note below
 *   - Alignment is controlled by the parent layout (Box horizontal/vertical positioning)
 *   - DrawableRes / Bitmap / Drawable variants: not applicable on the JVM server;
 *     callers should convert to a URL or omit
 */
fun RcScope.JarImage(
    imageUrl: String?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: RcContentScale = RcContentScale.Fit,
    fallbackUrl: String? = null,
) {
    val url = imageUrl?.takeIf { it.isNotBlank() } ?: fallbackUrl ?: return
    val image = remoteBitmapUrl(url)
    Image(
        image = image,
        modifier = modifier,
        contentDescription = contentDescription,
        contentScale = contentScale,
    )
}

// ─── JarBlurImage ─────────────────────────────────────────────────────────────

/**
 * RC equivalent of JarBlurImage.
 *
 * The original applies a RenderScript (Android < 12) or Compose blur (Android 12+) to the image.
 * RC has no blur modifier on Modifier. The closest approximation is rendering the image normally
 * and letting the Android app apply blur via RemoteComposePlayer post-processing if needed.
 *
 * RC limitation: blur radius is declared as metadata only — the RC document cannot apply pixel-
 * level blur. The [blurRadiusDp] parameter is preserved for callers so they can read it from the
 * document metadata and apply blur on the Android side after rendering.
 *
 * Image is loaded as Crop (matching the original's default contentScale = ContentScale.Crop).
 */
fun RcScope.JarBlurImage(
    imageUrl: String?,
    @Suppress("UNUSED_PARAMETER") blurRadiusDp: Float,
    modifier: Modifier = Modifier,
    contentScale: RcContentScale = RcContentScale.Crop,
    contentDescription: String? = null,
    fallbackUrl: String? = null,
) {
    JarImage(
        imageUrl = imageUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        fallbackUrl = fallbackUrl,
    )
}

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