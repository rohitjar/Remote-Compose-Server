package com.remotecompose.server

import androidx.compose.remote.core.operations.Header
import androidx.compose.remote.core.operations.layout.managers.BoxLayout
import androidx.compose.remote.core.operations.layout.managers.ColumnLayout
import androidx.compose.remote.core.operations.layout.managers.CoreText
import androidx.compose.remote.core.operations.layout.managers.RowLayout
import androidx.compose.remote.creation.JvmRcPlatformServices
import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.actions.HostAction
import androidx.compose.remote.creation.modifiers.RecordingModifier
import androidx.compose.remote.creation.modifiers.RoundedRectShape
import com.remotecompose.server.composables.DEFAULT_DENSITY
import com.remotecompose.shared.ElementConfig
import com.remotecompose.shared.LayoutConfig
import com.remotecompose.shared.parseColorLong
import java.rmi.Remote

/**
 * Core conversion engine: takes a JSON LayoutConfig and produces a binary .rc document
 * using AndroidX RemoteComposeWriter.
 */

// ─── Density / Unit Helpers ──────────────────────────────────────────
// Default Android mdpi density = 2.625 (xxhdpi)
private fun density(): Float = 2.625f
private fun dp(value: Int): Float = value * density()
private fun sp(value: Int): Float = value * density()

private fun parseArgb(hex: String): Int = parseColorLong(hex).toInt()

// ─── Platform Services ───────────────────────────────────────────────
private val platform = JvmRcPlatformServices()

/**
 * Builds a binary Remote Compose document from the given layout configuration.
 * Returns the raw ByteArray (.rc format) ready for RemoteComposePlayer.
 */
fun buildDocument(config: LayoutConfig): ByteArray {
    val width = (400 * density()).toInt()
    val height = (800 * density()).toInt()
    val writer = RemoteComposeWriter(
        platform,
        RemoteComposeWriter.HTag(Header.DOC_WIDTH, width),
        RemoteComposeWriter.HTag(Header.DOC_HEIGHT, height),
        RemoteComposeWriter.HTag(Header.DOC_DENSITY_AT_GENERATION, density()),
        RemoteComposeWriter.HTag(Header.DOC_DENSITY_BEHAVIOR, 2)
    )

    val bgColor = parseArgb(config.backgroundColor)
    val padding = config.padding ?: 24

    writer.root {
        val bgModifier = RecordingModifier()
            .fillMaxSize()
            .background(bgColor)

        writer.startBox(bgModifier, BoxLayout.START, BoxLayout.TOP)

        val colModifier = RecordingModifier()
            .fillMaxWidth()
            .padding(dp(padding), dp(padding), dp(padding), dp(padding))
        if (config.scrollable) {
            colModifier.verticalScroll()
        }

        writer.startColumn(colModifier, ColumnLayout.TOP, ColumnLayout.START)

        // availableWidth = -1 signals "use parent width" to child renderers
        for (element in config.elements) {
            renderElement(writer, element)
        }
        writer.endColumn()
        writer.endBox()
    }

    return writer.encodeToByteArray()
}





/**
 * Renders a single element into the RemoteComposeWriter.
 * Called recursively for nested children.
 */
private fun renderElement(writer: RemoteComposeWriter, el: ElementConfig) {
    when (el.type) {
        "text" -> renderText(writer, el)
        "button" -> renderButton(writer, el)
        "spacer" -> renderSpacer(writer, el)
        "divider" -> renderDivider(writer, el)
        "card" -> renderCard(writer, el)
        "row" -> renderRow(writer, el)
        "icon" -> renderIcon(writer, el)
        "image" -> renderImage(writer, el)
    }
}

// ─── Text ────────────────────────────────────────────────────────────
private fun renderText(writer: RemoteComposeWriter, el: ElementConfig) {
    val color = parseArgb(el.color ?: "#1A1A2E")
    val fontSize = el.fontSize ?: 16

    val modifier = RecordingModifier()

    if (!el.actionName.isNullOrBlank()) {
        modifier.onClick(HostAction(el.actionName))
    }

    val textId = writer.addText(el.text ?: "")
    writer.startTextComponent(
        modifier,
        textId,
        color,
        sp(fontSize),
        RemoteComposeWriter.FONT_TYPE_DEFAULT,
        400f,
        null,
        CoreText.TEXT_ALIGN_START,
        CoreText.OVERFLOW_CLIP,
        0
    )
    writer.endTextComponent()
}

// ─── Button ──────────────────────────────────────────────────────────
private fun renderButton(writer: RemoteComposeWriter, el: ElementConfig) {
    val bgColor = parseArgb(el.color ?: "#7C3AED")
    val textColor = parseArgb(el.textColor ?: "#FFFFFF")
    val fontSize = el.fontSize ?: 14
    val cornerRadius = el.cornerRadius ?: 12
    val paddingH = el.paddingH ?: 24
    val paddingV = el.paddingV ?: 12

    val modifier = RecordingModifier()
        .background(bgColor)
        .clip(RoundedRectShape(dp(cornerRadius), dp(cornerRadius), dp(cornerRadius), dp(cornerRadius)))
        .padding(dp(paddingH), dp(paddingV), dp(paddingH), dp(paddingV))

    if (!el.actionName.isNullOrBlank()) {
        modifier.onClick(HostAction(el.actionName))
    }

    writer.startBox(modifier, BoxLayout.CENTER, BoxLayout.CENTER)

    val textId = writer.addText(el.text ?: "Button")
    writer.startTextComponent(
        RecordingModifier(),
        textId,
        textColor,
        sp(fontSize),
        RemoteComposeWriter.FONT_TYPE_DEFAULT,
        400f,
        null,
        CoreText.TEXT_ALIGN_CENTER,
        CoreText.OVERFLOW_CLIP,
        0
    )
    writer.endTextComponent()

    writer.endBox()
}

// ─── Spacer ──────────────────────────────────────────────────────────
private fun renderSpacer(writer: RemoteComposeWriter, el: ElementConfig) {
    val height = el.height ?: 16
    val modifier = RecordingModifier().height(dp(height))
    writer.startBox(modifier, BoxLayout.START, BoxLayout.TOP)
    writer.endBox()
}

// ─── Divider ─────────────────────────────────────────────────────────
private fun renderDivider(writer: RemoteComposeWriter, el: ElementConfig) {
    val color = parseArgb(el.color ?: "#E0E0E0")
    val thickness = el.thickness ?: 1
    val modifier = RecordingModifier()
        .fillMaxWidth()
        .height(dp(thickness))
        .background(color)
    writer.startBox(modifier, BoxLayout.START, BoxLayout.TOP)
    writer.endBox()
}

// ─── Card ────────────────────────────────────────────────────────────
private fun renderCard(writer: RemoteComposeWriter, el: ElementConfig) {
    val bgColor = parseArgb(el.color ?: "#FFFFFF")
    val cornerRadius = el.cornerRadius ?: 16
    val paddingH = el.paddingH ?: 16
    val paddingV = el.paddingV ?: 14
    val borderColor = if (!el.borderColor.isNullOrBlank() && el.borderColor != "transparent") {
        parseArgb(el.borderColor)
    } else null
    val borderWidth = el.borderWidth ?: 0

    val modifier = RecordingModifier()
        .fillMaxWidth()
        .background(bgColor)
        .clip(RoundedRectShape(dp(cornerRadius), dp(cornerRadius), dp(cornerRadius), dp(cornerRadius)))
        .padding(dp(paddingH), dp(paddingV), dp(paddingH), dp(paddingV))

    if (borderColor != null && borderWidth > 0) {
        modifier.border(dp(borderWidth), dp(cornerRadius), borderColor, 2)
    }

    if (!el.actionName.isNullOrBlank()) {
        modifier.onClick(HostAction(el.actionName))
    }

    val alignment = when (el.align) {
        "center" -> ColumnLayout.CENTER
        "end" -> ColumnLayout.END
        else -> ColumnLayout.START
    }

    writer.startColumn(modifier, ColumnLayout.TOP, alignment)
    el.children?.forEach { child ->
        renderElement(writer, child)
    }
    writer.endColumn()
}

// ─── Row ─────────────────────────────────────────────────────────────
private fun renderRow(writer: RemoteComposeWriter, el: ElementConfig) {
    val gap = el.gap ?: 8
    val alignment = when (el.align) {
        "center" -> RowLayout.CENTER
        "end" -> RowLayout.BOTTOM
        else -> RowLayout.TOP
    }

    val modifier = RecordingModifier().fillMaxWidth()

    writer.startRow(modifier, RowLayout.SPACE_BETWEEN, alignment)
    val childCount = el.children?.size ?: 0
    el.children?.forEachIndexed { index, child ->
        renderElement(writer, child)
        if (index < childCount - 1 && gap > 0) {
            val gapModifier = RecordingModifier().width(dp(gap))
            writer.startBox(gapModifier, BoxLayout.START, BoxLayout.TOP)
            writer.endBox()
        }
    }
    writer.endRow()
}

// ─── Icon ────────────────────────────────────────────────────────────
private fun renderIcon(writer: RemoteComposeWriter, el: ElementConfig) {
    val color = parseArgb(el.color ?: "#7C3AED")
    val size = el.size ?: 24

    val textId = writer.addText(el.icon ?: "star")
    writer.startTextComponent(
        RecordingModifier(),
        textId,
        color,
        sp(size),
        RemoteComposeWriter.FONT_TYPE_DEFAULT,
        400f,
        null,
        CoreText.TEXT_ALIGN_CENTER,
        CoreText.OVERFLOW_CLIP,
        0
    )
    writer.endTextComponent()
}

// ─── Image ───────────────────────────────────────────────────────────
private fun renderImage(writer: RemoteComposeWriter, el: ElementConfig) {
    val height = el.height ?: 150
    val cornerRadius = el.cornerRadius ?: 12

    val modifier = RecordingModifier()
        .fillMaxWidth()
        .height(dp(height))
        .clip(RoundedRectShape(dp(cornerRadius), dp(cornerRadius), dp(cornerRadius), dp(cornerRadius)))

    if (!el.url.isNullOrBlank()) {
        val imageId = writer.addBitmapUrl(el.url)
        writer.image(modifier, imageId, RemoteComposeWriter.IMAGE_SCALE_CROP, 1f)
    } else {
        // Placeholder box when no URL is provided
        val placeholderModifier = modifier.background(parseArgb("#F0F0F0"))
        writer.startBox(placeholderModifier, BoxLayout.CENTER, BoxLayout.CENTER)
        val textId = writer.addText("📷")
        writer.startTextComponent(
            RecordingModifier(),
            textId,
            parseArgb("#999999"),
            sp(24),
            RemoteComposeWriter.FONT_TYPE_DEFAULT,
            400f,
            null,
            CoreText.TEXT_ALIGN_CENTER,
            CoreText.OVERFLOW_CLIP,
            0
        )
        writer.endTextComponent()
        writer.endBox()
    }
}
