package com.remotecompose.shared

import kotlinx.serialization.Serializable

/**
 * Top-level layout configuration for a single screen.
 * Matches the JSON structure produced by the web editor.
 */
@Serializable
data class LayoutConfig(
    val backgroundColor: String = "#FFFFFF",
    val scrollable: Boolean = true,
    val padding: Int? = 24,
    val elements: List<ElementConfig> = emptyList(),
    // Optional metadata for batch processing
    val name: String? = null,
    val icon: String? = null
)

/**
 * Individual UI element configuration.
 * Supports all element types: text, button, spacer, divider, card, row, icon, image.
 */
@Serializable
data class ElementConfig(
    val type: String,
    val id: String,

    // Text / Button
    val text: String? = null,
    val color: String? = null,
    val textColor: String? = null,
    val fontSize: Int? = null,

    // Button specific
    val cornerRadius: Int? = null,
    val paddingH: Int? = null,
    val paddingV: Int? = null,

    // Spacer
    val height: Int? = null,

    // Divider
    val thickness: Int? = null,

    // Card
    val borderColor: String? = null,
    val borderWidth: Int? = null,
    val align: String? = null,

    // Row
    val gap: Int? = null,

    // Icon
    val icon: String? = null,
    val size: Int? = null,

    // Image
    val url: String? = null,
    val width: Int? = null,
    val fit: String? = null,

    // Action
    val actionName: String? = null,

    // Nested children (for card, row)
    val children: List<ElementConfig>? = null
)

/**
 * Parses a hex color string (#RRGGBB or #AARRGGBB) to a Long ARGB value.
 */
fun parseColorLong(hex: String): Long {
    val clean = hex.removePrefix("#")
    return when (clean.length) {
        6 -> (0xFF000000L or clean.toLong(16))
        8 -> clean.toLong(16)
        else -> 0xFF000000L // fallback: black
    }
}
