@file:Suppress("RestrictedApi")

package com.remotecompose.rc.feature.samples

import androidx.compose.remote.creation.Rc
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcScope
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.width
import androidx.compose.remote.creation.modifiers.RoundedRectShape
import com.remotecompose.rc.core.RemoteUrlImage
import com.remotecompose.rc.core.RenderContext
import com.remotecompose.rc.core.rcDocument
import com.remotecompose.rc.core.rdp
import com.remotecompose.rc.modifier.padding
import com.remotecompose.rc.theme.Colors

// Small square placeholder thumbnails (~1 KB each). The declared slot must match
// this pixel size — the player budgets bitmap memory from slot dims, not file size.
private const val IMAGE_PX = 120

private const val COLUMNS = 3
private const val CELL_HEIGHT = 104 // dp
private const val CELL_GAP = 8 // dp

private val imageUrls: List<String> = listOf(
    "43197A", "7029CC", "8B5CF6", "6B46C1", "554766",
    "43197A", "7029CC", "8B5CF6", "6B46C1", "554766",
    "43197A", "7029CC", "8B5CF6", "6B46C1", "554766",
).mapIndexed { index, hex ->
    "https://placehold.co/${IMAGE_PX}x$IMAGE_PX/$hex/FFFFFF/png?text=${index + 1}"
}

/** All images on one screen in a fixed 3-column grid — no scrolling. */
fun ImageListScreen(ctx: RenderContext = RenderContext()): ByteArray = rcDocument(ctx) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Colors.bgApp)
            .padding(top = ctx.safeAreaTop.rdp, bottom = ctx.safeAreaBottom.rdp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.rdp, end = 16.rdp, top = 16.rdp),
        ) {
            imageUrls.chunked(COLUMNS).forEach { rowUrls ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowUrls.forEachIndexed { i, url ->
                        if (i > 0) Spacer(modifier = Modifier.width(CELL_GAP.rdp))
                        GridImage(url, modifier = Modifier.weight(1f))
                    }
                    // Pad a short last row so its cells keep the same width.
                    repeat(COLUMNS - rowUrls.size) {
                        Spacer(modifier = Modifier.width(CELL_GAP.rdp))
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.fillMaxWidth().height(CELL_GAP.rdp))
            }
        }
    }
}

private fun RcScope.GridImage(url: String, modifier: Modifier) {
    val shape = RoundedRectShape(12.rdp.value, 12.rdp.value, 12.rdp.value, 12.rdp.value)
    Box(modifier = modifier.height(CELL_HEIGHT.rdp).clip(shape)) {
        RemoteUrlImage(
            url = url,
            modifier = Modifier.fillMaxWidth().height(CELL_HEIGHT.rdp),
            scale = Rc.ImageScale.CROP,
            width = IMAGE_PX,
            height = IMAGE_PX,
        )
    }
}