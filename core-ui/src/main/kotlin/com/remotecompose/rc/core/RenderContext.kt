package com.remotecompose.rc.core

/**
 * Per-request rendering parameters supplied by the consumer (the device asking for a screen).
 *
 * The document is authored in logical dp/sp; [density] is written into the header
 * (DOC_DENSITY_AT_GENERATION) and used to derive the pixel dimensions, so the same
 * screen can be generated correctly for any device that reports its real metrics.
 *
 * Convention: density = densityDpi / 160f (e.g. xhdpi ≈ 2.0, xxhdpi ≈ 3.0).
 */
data class RenderContext(
    val density: Float = DEFAULT_DENSITY,
    val widthDp: Int = 360,
    val heightDp: Int = 800,
)
