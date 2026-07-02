@file:Suppress("RestrictedApi")

package com.remotecompose.rc.core

import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcScope
import androidx.compose.remote.creation.dsl.toRecordingModifier

// Declared image-slot size. The player's RemoteBitmapDecoder.checkBounds throws if a fetched
// bitmap is LARGER than the slot the document declared, and the DSL's remoteBitmapUrl() hard-codes
// the slot to 1×1 (which always fails). We declare a generous slot and the consumer's
// CoilBitmapLoader downscales every fetched image to fit within this cap — keep the two in sync
// (CoilBitmapLoader.DEFAULT_MAX_IMAGE_DIMENSION_PX = 512).
const val IMAGE_SLOT_PX = 512

/**
 * Draws a URL image declaring an [IMAGE_SLOT_PX]² slot so the decoder's bounds check passes.
 *
 * The high-level RcScope DSL only exposes remoteBitmapUrl(url) which declares a 1×1 slot and thus
 * always trips RemoteBitmapDecoder.checkBounds. The sized writer.addBitmapUrl(url, w, h) is the
 * only way to declare a real slot, but the writer is library-`internal` — reached via [writer].
 */
fun RcScope.RemoteUrlImage(url: String, modifier: Modifier, scale: Int = RemoteComposeWriter.IMAGE_SCALE_FIT, width: Int = IMAGE_SLOT_PX, height: Int = IMAGE_SLOT_PX) {
    val w = writer()
    val imageId = w.addBitmapUrl(url, width, height)
    w.image(modifier.toRecordingModifier(), imageId, scale, 1f)
}
