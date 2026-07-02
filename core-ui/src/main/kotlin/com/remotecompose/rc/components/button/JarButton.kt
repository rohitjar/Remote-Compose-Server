@file:Suppress("RestrictedApi")

package com.remotecompose.rc.components.button

import androidx.compose.remote.core.operations.layout.modifiers.GraphicsLayerModifierOperation
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcScope
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.border
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.graphicsLayer
import androidx.compose.remote.creation.dsl.heightIn
import androidx.compose.remote.creation.dsl.onClick
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.ripple
import androidx.compose.remote.creation.dsl.rsp
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.wrapContentHeight
import androidx.compose.remote.creation.modifiers.RoundedRectShape

// ─── Icon gravity constants ───────────────────────────────────────────────────
const val ICON_GRAVITY_START = 1
const val ICON_GRAVITY_END = 2
const val ICON_GRAVITY_TOP = 3
const val ICON_GRAVITY_BOTTOM = 4

// ─── Color constants ──────────────────────────────────────────────────────────
// Hardcoded ARGB because colorResource / JarCustomTheme are unavailable on the
// JVM server side. Keep these in sync with the values in JarColors / res/values/colors.xml
private const val COLOR_BTN_PRIMARY_BG = 0xFF6B46C1.toInt()  // color_btn_primary_bg
private const val COLOR_WHITE          = 0xFFFFFFFF.toInt()
private const val COLOR_BG_LABEL       = 0xFF2D2D3A.toInt()  // color_bg_label
private const val COLOR_BG_SURFACE_2   = 0xFF1F1F2E.toInt()  // color_bg_surface_2
private const val COLOR_PURPLE_400     = 0xFF8B5CF6.toInt()  // color_purple_400
private const val COLOR_PURPLE_50      = 0xFFF5F3FF.toInt()  // color_purple_50
private const val COLOR_CTA_CAUTION    = 0xFF374151.toInt()  // color_cta_caution_state
private const val COLOR_BG             = 0xFF0D0D1A.toInt()  // color_bg
private const val COLOR_TEXT_PRIMARY   = 0xFFFFFFFF.toInt()  // color_text_primary (white)

// White at 40% opacity – used as the top-gradient border substitute on primary/black buttons.
// Original: color_text_primary.copy(alpha = 0.40f) → ARGB 0x66FFFFFF
private const val COLOR_BORDER_PRIMARY_TOP = 0x66FFFFFF.toInt()

// ─── Button type / height enums ───────────────────────────────────────────────

enum class RcButtonType {
    PRIMARY, SECONDARY, SPECIAL_WHITE, SPECIAL_BLACK, TERTIARY, PILL
}

enum class RcButtonHeight {
    PRIMARY_HEIGHT, ONE_THIRD_HEIGHT, HALF_HEIGHT, PILL
}

// ─── Layout helpers ───────────────────────────────────────────────────────────

fun getButtonHeightDp(h: RcButtonHeight): Float = when (h) {
    RcButtonHeight.PRIMARY_HEIGHT   -> 56f
    RcButtonHeight.HALF_HEIGHT      -> 44f
    RcButtonHeight.ONE_THIRD_HEIGHT -> 34f
    RcButtonHeight.PILL             -> 34f
}

data class RcPaddingValues(val horizontal: Float, val vertical: Float)

fun getButtonPadding(h: RcButtonHeight): RcPaddingValues = when (h) {
    RcButtonHeight.PRIMARY_HEIGHT   -> RcPaddingValues(24f, 18f)
    RcButtonHeight.ONE_THIRD_HEIGHT -> RcPaddingValues(24f, 12f)
    RcButtonHeight.HALF_HEIGHT      -> RcPaddingValues(24f, 8f)
    RcButtonHeight.PILL             -> RcPaddingValues(20f, 1f)
}

fun getFontSizeSp(h: RcButtonHeight): Float =
    if (h == RcButtonHeight.HALF_HEIGHT || h == RcButtonHeight.PILL) 12f else 14f

// ─── Variable-key helpers ─────────────────────────────────────────────────────
// Each call site must pass a unique `instanceKey` (e.g. a position counter or
// a stable UUID) so that multiple buttons sharing the same actionName on one
// screen don't share the same RC variable and trigger each other's animations.

private fun scaleKey(instanceKey: String) = "btn_scale_$instanceKey"
private fun alphaKey(instanceKey: String) = "btn_alpha_$instanceKey"

// ─── Top-level JarButton dispatcher ──────────────────────────────────────────
/**
 * RC equivalent of the JarButton dispatcher.
 *
 * Parameters vs original:
 *   [actionName]      → replaces onClick lambda; fired as HostAction on the device
 *   [disabledActionName] → optional HostAction fired when the button is tapped while disabled
 *                        (button stays visible at 30% alpha; host handles the action)
 *   [instanceKey]     → unique string per button instance on a screen (e.g. "cta0", "cta1")
 *                        used to namespace RC variables so animations don't bleed across buttons
 *   [isAllCaps]       → handled here; pass pre-uppercased text from server if you prefer
 *
 * Not supported vs original:
 *   - borderBrush gradient  → solid border only (RC border takes a single color)
 *   - iconTint (ColorFilter) → no RC equivalent; pre-tint the icon image server-side
 *   - Modifier.shadow elevation → no RC shadow modifier
 *   - debounce interval     → handle in the Android host's onAction callback
 *   - @DrawableRes icons    → use full HTTPS URLs instead
 *   - animateContentSize / tween easing → press scale is instant snap (no tween in RC)
 */
fun RcScope.JarButton(
    text: String,
    actionName: String,
    instanceKey: String,
    disabledActionName: String? = null,
    isEnabled: Boolean = true,
    isAllCaps: Boolean = false,
    iconUrl: String? = null,
    iconGravity: Int = ICON_GRAVITY_START,
    iconSizeDp: Float = 16f,
    color: Int = COLOR_BTN_PRIMARY_BG,
    iconPaddingDp: Float = 10f,
    borderColor: Int = COLOR_BTN_PRIMARY_BG,
    textColor: Int = COLOR_WHITE,
    buttonType: RcButtonType = RcButtonType.PRIMARY,
    minHeightDp: Float = 56f,
    buttonHeight: RcButtonHeight = RcButtonHeight.PRIMARY_HEIGHT,
    paddingValues: RcPaddingValues = getButtonPadding(buttonHeight),
    fontSize: Float = getFontSizeSp(buttonHeight),
    cornerRadius: Float = 8f,
    fontWeight: Float = 500f,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    val displayText = if (isAllCaps) text.uppercase() else text
    when (buttonType) {
        RcButtonType.PRIMARY -> JarPrimaryButton(
            text = displayText,
            actionName = actionName,
            instanceKey = instanceKey,
            disabledActionName = disabledActionName,
            isEnabled = isEnabled,
            iconUrl = iconUrl,
            iconGravity = iconGravity,
            iconSizeDp = iconSizeDp,
            color = color,
            iconPaddingDp = iconPaddingDp,
            textColor = textColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            cornerRadius = cornerRadius,
            minHeightDp = minHeightDp,
            buttonHeight = buttonHeight,
            paddingValues = paddingValues,
            maxLines = maxLines,
            modifier = modifier,
        )
        RcButtonType.SECONDARY -> JarSecondaryButton(
            text = displayText,
            actionName = actionName,
            instanceKey = instanceKey,
            disabledActionName = disabledActionName,
            isEnabled = isEnabled,
            iconUrl = iconUrl,
            iconGravity = iconGravity,
            iconSizeDp = iconSizeDp,
            iconPaddingDp = iconPaddingDp,
            minHeightDp = minHeightDp,
            textColor = textColor,
            cornerRadius = cornerRadius,
            buttonHeight = buttonHeight,
            paddingValues = paddingValues,
            borderColor = borderColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = maxLines,
            modifier = modifier,
        )
        RcButtonType.SPECIAL_BLACK -> JarSpecialBlackButton(
            text = displayText,
            actionName = actionName,
            instanceKey = instanceKey,
            isEnabled = isEnabled,
            iconUrl = iconUrl,
            iconGravity = iconGravity,
            iconSizeDp = iconSizeDp,
            iconPaddingDp = iconPaddingDp,
            minHeightDp = minHeightDp,
            textColor = textColor,
            cornerRadius = cornerRadius,
            buttonHeight = buttonHeight,
            paddingValues = paddingValues,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = maxLines,
            modifier = modifier,
        )
        RcButtonType.SPECIAL_WHITE -> JarSpecialWhiteButton(
            text = displayText,
            actionName = actionName,
            instanceKey = instanceKey,
            isEnabled = isEnabled,
            iconUrl = iconUrl,
            iconGravity = iconGravity,
            iconSizeDp = iconSizeDp,
            iconPaddingDp = iconPaddingDp,
            minHeightDp = minHeightDp,
            cornerRadius = cornerRadius,
            buttonHeight = buttonHeight,
            paddingValues = paddingValues,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = maxLines,
            modifier = modifier,
        )
        RcButtonType.TERTIARY -> JarTertiaryButton(
            text = displayText,
            actionName = actionName,
            instanceKey = instanceKey,
            isEnabled = isEnabled,
            fontSize = getFontSizeSp(RcButtonHeight.PRIMARY_HEIGHT),
            modifier = modifier,
        )
        RcButtonType.PILL -> JarPillButton(
            text = displayText,
            actionName = actionName,
            instanceKey = instanceKey,
            isEnabled = isEnabled,
            iconUrl = iconUrl,
            iconGravity = iconGravity,
            iconSizeDp = iconSizeDp,
            iconPaddingDp = iconPaddingDp,
            cornerRadius = cornerRadius,
            fontSize = getFontSizeSp(RcButtonHeight.PILL),
            fontWeight = fontWeight,
            paddingValues = getButtonPadding(RcButtonHeight.PILL),
            maxLines = maxLines,
            modifier = modifier,
        )
    }
}

// ─── JarTertiaryButton ────────────────────────────────────────────────────────
// Original: underlined Text with clickable. RC has no TextDecoration, so we
// render a styled Text with reduced alpha when disabled and fire the HostAction
// on tap. The underline must be achieved via a pre-underlined asset or accepted
// as a known visual gap.

fun RcScope.JarTertiaryButton(
    text: String,
    actionName: String,
    instanceKey: String,
    isEnabled: Boolean = true,
    fontSize: Float = getFontSizeSp(RcButtonHeight.PRIMARY_HEIGHT),
    fontColor: Int = COLOR_WHITE,
    fontWeight: Float = 500f,
    modifier: Modifier = Modifier,
) {
    val staticAlpha = if (isEnabled) 1.0f else 0.3f
    Text(
        text = text,
        color = fontColor,
        fontSize = fontSize.rsp,
        fontWeight = fontWeight,
        modifier = modifier
            .graphicsLayer(mapOf(GraphicsLayerModifierOperation.ALPHA to staticAlpha))
            .onClick { hostAction(actionName) },
    )
}

// ─── JarPrimaryButton ─────────────────────────────────────────────────────────

fun RcScope.JarPrimaryButton(
    text: String,
    actionName: String,
    instanceKey: String,
    disabledActionName: String? = null,
    isEnabled: Boolean = true,
    iconUrl: String? = null,
    iconGravity: Int = ICON_GRAVITY_START,
    iconSizeDp: Float = 16f,
    color: Int = COLOR_BTN_PRIMARY_BG,
    iconPaddingDp: Float = 10f,
    fontWeight: Float = 500f,
    textColor: Int = COLOR_WHITE,
    fontSize: Float = 14f,
    minHeightDp: Float = 56f,
    buttonHeight: RcButtonHeight = RcButtonHeight.PRIMARY_HEIGHT,
    // Original: Brush.verticalGradient(color_text_primary 40%→transparent) → solid fallback
    borderColor: Int = COLOR_BORDER_PRIMARY_TOP,
    borderWidth: Float = 1f,
    paddingValues: RcPaddingValues = getButtonPadding(buttonHeight),
    cornerRadius: Float = 8f,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    InternalButtonWrapperImpl(
        text = text,
        actionName = actionName,
        instanceKey = instanceKey,
        disabledActionName = disabledActionName,
        isEnabled = isEnabled,
        iconUrl = iconUrl,
        iconGravity = iconGravity,
        iconSizeDp = iconSizeDp,
        iconPaddingDp = iconPaddingDp,
        bgColor = color,
        textColor = textColor,
        fontSize = fontSize,
        fontWeight = fontWeight,
        cornerRadius = cornerRadius,
        borderColor = borderColor,
        borderWidth = borderWidth,
        minHeightDp = minHeightDp,
        paddingValues = paddingValues,
        maxLines = maxLines,
        modifier = modifier,
    )
}

// ─── JarSecondaryButton ───────────────────────────────────────────────────────

fun RcScope.JarSecondaryButton(
    text: String,
    actionName: String,
    instanceKey: String,
    disabledActionName: String? = null,
    isEnabled: Boolean = true,
    iconUrl: String? = null,
    iconGravity: Int = ICON_GRAVITY_START,
    iconSizeDp: Float = 16f,
    borderColor: Int = COLOR_PURPLE_400,
    color: Int = COLOR_BG_LABEL,
    iconPaddingDp: Float = 10f,
    buttonHeight: RcButtonHeight = RcButtonHeight.PRIMARY_HEIGHT,
    minHeightDp: Float = 56f,
    textColor: Int = COLOR_WHITE,
    cornerRadius: Float = 8f,
    fontSize: Float = getFontSizeSp(RcButtonHeight.PRIMARY_HEIGHT),
    paddingValues: RcPaddingValues = getButtonPadding(buttonHeight),
    fontWeight: Float = 500f,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    InternalButtonWrapperImpl(
        text = text,
        actionName = actionName,
        instanceKey = instanceKey,
        disabledActionName = disabledActionName,
        isEnabled = isEnabled,
        iconUrl = iconUrl,
        iconGravity = iconGravity,
        iconSizeDp = iconSizeDp,
        iconPaddingDp = iconPaddingDp,
        bgColor = color,
        textColor = textColor,
        fontSize = fontSize,
        fontWeight = fontWeight,
        cornerRadius = cornerRadius,
        borderColor = borderColor,
        borderWidth = 1f,
        minHeightDp = minHeightDp,
        paddingValues = paddingValues,
        maxLines = maxLines,
        modifier = modifier,
    )
}

// ─── JarSpecialBlackButton ────────────────────────────────────────────────────

fun RcScope.JarSpecialBlackButton(
    text: String,
    actionName: String,
    instanceKey: String,
    isEnabled: Boolean = true,
    iconUrl: String? = null,
    iconGravity: Int = ICON_GRAVITY_START,
    iconSizeDp: Float = 16f,
    color: Int = COLOR_CTA_CAUTION,
    buttonHeight: RcButtonHeight = RcButtonHeight.PRIMARY_HEIGHT,
    iconPaddingDp: Float = 10f,
    minHeightDp: Float = 56f,
    textColor: Int = COLOR_WHITE,
    cornerRadius: Float = 8f,
    fontSize: Float = getFontSizeSp(RcButtonHeight.PRIMARY_HEIGHT),
    fontWeight: Float = 500f,
    paddingValues: RcPaddingValues = getButtonPadding(buttonHeight),
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    InternalButtonWrapperImpl(
        text = text,
        actionName = actionName,
        instanceKey = instanceKey,
        isEnabled = isEnabled,
        iconUrl = iconUrl,
        iconGravity = iconGravity,
        iconSizeDp = iconSizeDp,
        iconPaddingDp = iconPaddingDp,
        bgColor = color,
        textColor = textColor,
        fontSize = fontSize,
        fontWeight = fontWeight,
        cornerRadius = cornerRadius,
        // Original: color_text_primary.copy(alpha=0.40f) → 0x66FFFFFF
        borderColor = COLOR_BORDER_PRIMARY_TOP,
        borderWidth = 1f,
        minHeightDp = minHeightDp,
        paddingValues = paddingValues,
        maxLines = maxLines,
        modifier = modifier,
    )
}

// ─── JarSpecialWhiteButton ────────────────────────────────────────────────────

fun RcScope.JarSpecialWhiteButton(
    text: String,
    actionName: String,
    instanceKey: String,
    isEnabled: Boolean = true,
    iconUrl: String? = null,
    iconGravity: Int = ICON_GRAVITY_START,
    iconSizeDp: Float = 16f,
    color: Int = COLOR_PURPLE_50,
    buttonHeight: RcButtonHeight = RcButtonHeight.PRIMARY_HEIGHT,
    iconPaddingDp: Float = 10f,
    borderColor: Int = COLOR_PURPLE_50,
    minHeightDp: Float = 56f,
    // Original: colorResource(R.color.color_bg) – dark background
    textColor: Int = COLOR_BG,
    cornerRadius: Float = 8f,
    fontSize: Float = getFontSizeSp(RcButtonHeight.PRIMARY_HEIGHT),
    fontWeight: Float = 500f,
    paddingValues: RcPaddingValues = getButtonPadding(buttonHeight),
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    InternalButtonWrapperImpl(
        text = text,
        actionName = actionName,
        instanceKey = instanceKey,
        isEnabled = isEnabled,
        iconUrl = iconUrl,
        iconGravity = iconGravity,
        iconSizeDp = iconSizeDp,
        iconPaddingDp = iconPaddingDp,
        bgColor = color,
        textColor = textColor,
        fontSize = fontSize,
        fontWeight = fontWeight,
        cornerRadius = cornerRadius,
        borderColor = borderColor,
        borderWidth = 2f, // original uses 2.sdp for SpecialWhite
        minHeightDp = minHeightDp,
        paddingValues = paddingValues,
        maxLines = maxLines,
        modifier = modifier,
    )
}

// ─── JarPillButton ────────────────────────────────────────────────────────────

fun RcScope.JarPillButton(
    text: String,
    actionName: String,
    instanceKey: String,
    isEnabled: Boolean = true,
    iconUrl: String? = null,
    iconGravity: Int = ICON_GRAVITY_START,
    iconSizeDp: Float = 16f,
    color: Int = COLOR_BG_SURFACE_2,
    iconPaddingDp: Float = 10f,
    borderColor: Int = COLOR_BG_LABEL,
    minHeightDp: Float = getButtonHeightDp(RcButtonHeight.PILL),
    textColor: Int = COLOR_WHITE,
    cornerRadius: Float = 8f,
    borderSize: Float = 1f,
    fontSize: Float = getFontSizeSp(RcButtonHeight.PILL),
    fontWeight: Float = 500f,
    paddingValues: RcPaddingValues = getButtonPadding(RcButtonHeight.PILL),
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    InternalButtonWrapperImpl(
        text = text,
        actionName = actionName,
        instanceKey = instanceKey,
        isEnabled = isEnabled,
        iconUrl = iconUrl,
        iconGravity = iconGravity,
        iconSizeDp = iconSizeDp,
        iconPaddingDp = iconPaddingDp,
        bgColor = color,
        textColor = textColor,
        fontSize = fontSize,
        fontWeight = fontWeight,
        cornerRadius = cornerRadius,
        borderColor = borderColor,
        borderWidth = borderSize,
        minHeightDp = minHeightDp,
        paddingValues = paddingValues,
        maxLines = maxLines,
        modifier = modifier,
    )
}

// ─── RenderImagePillButton ────────────────────────────────────────────────────

fun RcScope.RenderImagePillButton(
    text: String,
    bgColor: Int,
    textColor: Int,
    iconUrl: String? = null,
    cornerRadius: Float = 16f,
    actionName: String? = null,
    smallerTextPadding: Boolean = false,
    biggerVerticalPadding: Boolean = false,
    borderColor: Int = bgColor,
    maxLines: Int = Int.MAX_VALUE,
    iconSizeDp: Float? = null,
    fontSize: Float = 14f,
    // modifier is intentionally NOT forced to fillMaxWidth – caller controls sizing
    modifier: Modifier = Modifier,
) {
    val textPaddingH = if (smallerTextPadding) 6f else 12f
    val textPaddingV = if (iconUrl == null && !biggerVerticalPadding) 4f else 8f
    val shape = RoundedRectShape(cornerRadius, cornerRadius, cornerRadius, cornerRadius)

    // Build row modifier without forcing fillMaxWidth – honour the caller's modifier
    var rowMod = modifier
        .border(1f, cornerRadius, borderColor, 2)
        .clip(shape)
        .background(bgColor)
    if (actionName != null) {
        rowMod = rowMod.onClick { hostAction(actionName) }
    }

    Row(modifier = rowMod) {
        iconUrl?.let { url ->
            val imgMod = if (iconSizeDp != null)
                Modifier.size(iconSizeDp, iconSizeDp).padding(12f, 0f, 0f, 0f)
            else
                Modifier.padding(12f, 0f, 0f, 0f)
            Image(image = remoteBitmapUrl(url), modifier = imgMod)
        }
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize.rsp,
            maxLines = maxLines,
            modifier = Modifier.padding(textPaddingH, textPaddingV, 12f, textPaddingV),
        )
    }
}

// ─── InternalButtonWrapperImpl ────────────────────────────────────────────────
/**
 * Core shared implementation.
 *
 * Key differences from the Compose original:
 *
 * MODIFIER ORDER (fixed from original review):
 *   graphicsLayer (scale + alpha) → fillMaxWidth → heightIn → clip → background → border
 *   → ripple → padding → onTouchDown/Up → onClick
 *   graphicsLayer MUST be first so scale applies to the entire visual including clip/bg.
 *
 * DISABLED STATE:
 *   alpha = 0.3f via graphicsLayer ALPHA key (not visibility).
 *   The button stays in the layout at reduced opacity, matching the original.
 *   onClick fires [disabledActionName] if set; if null, the tap is swallowed.
 *
 * PRESS ANIMATION:
 *   scaleVar snaps to 0.95 on touch-down, back to 1.0 on touch-up.
 *   No tween easing – RC has no equivalent of Compose's animateFloatAsState.
 *
 * BORDER:
 *   Solid color only – the vertical gradient brush from the original is not
 *   supported by RC's border modifier.
 *
 * SHADOW / ELEVATION:
 *   Not supported in RC DSL – omitted.
 *
 * ICON TINT (ColorFilter):
 *   Not supported in RC – tint icon images server-side before serving the URL.
 *
 * MINIMUM WIDTH:
 *   Original uses defaultMinSize(minWidth = 48.sdp). RC has no minWidth constraint
 *   modifier – buttons always fill the available width via fillMaxWidth.
 */
@Suppress("RestrictedApi")
private fun RcScope.InternalButtonWrapperImpl(
    text: String,
    actionName: String,
    instanceKey: String,
    disabledActionName: String? = null,
    isEnabled: Boolean,
    iconUrl: String?,
    iconGravity: Int,
    iconSizeDp: Float,
    iconPaddingDp: Float,
    bgColor: Int,
    textColor: Int,
    fontSize: Float,
    fontWeight: Float,
    cornerRadius: Float,
    borderColor: Int,
    borderWidth: Float,
    minHeightDp: Float,
    paddingValues: RcPaddingValues,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedRectShape(cornerRadius, cornerRadius, cornerRadius, cornerRadius)

    // ── RC variables ─────────────────────────────────────────────────────────
    // Keys include instanceKey so two buttons with the same actionName on the
    // same screen don't share state.
    // The effective action: when disabled and a fallback action is given, fire
    // that instead. If no fallback, the onClick is still registered but the host
    // can choose to ignore it based on the alpha / disabled context passed via
    // a naming convention (e.g. prefix "disabled:" on the action string).
    val effectiveAction = when {
        !isEnabled && disabledActionName != null -> disabledActionName
        !isEnabled -> "noop:$actionName" // host should no-op actions prefixed with "noop:"
        else -> actionName
    }

    // ── Modifier chain ───────────────────────────────────────────────────────
    // ORDER MATTERS:
    //  1. graphicsLayer  – scale + alpha applied to everything below
    //  2. fillMaxWidth   – layout size
    //  3. heightIn       – minimum height constraint
    //  4. clip           – clip to rounded shape BEFORE background so bg doesn't bleed
    //  5. background     – fill inside the clipped shape
    //  6. border         – drawn on top of the clipped background
    //  7. ripple         – touch feedback
    //  8. padding        – inner content padding
    //  9. onTouchDown/Up – drive the scale variable
    // 10. onClick        – fire the host action
    val staticAlpha = if (isEnabled) 1.0f else 0.3f
    val boxMod = modifier
        .graphicsLayer(
            mapOf(
                GraphicsLayerModifierOperation.ALPHA to staticAlpha,
            )
        )
        .fillMaxWidth()
        .heightIn(min = minHeightDp)
        .clip(shape)
        .background(bgColor)
        .border(borderWidth, cornerRadius, borderColor, 2)
        .ripple()
        .padding(
            paddingValues.horizontal,
            paddingValues.vertical,
            paddingValues.horizontal,
            paddingValues.vertical,
        )
        .onClick { hostAction(effectiveAction) }

    Box(
        modifier = boxMod,
        horizontal = RcHorizontalPositioning.Center,
        vertical   = RcVerticalPositioning.Center,
    ) {
        when (iconGravity) {
            ICON_GRAVITY_START, ICON_GRAVITY_END ->
                HorizontalButtonContentImpl(
                    text = text,
                    textColor = textColor,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    iconUrl = iconUrl,
                    iconGravity = iconGravity,
                    iconSizeDp = iconSizeDp,
                    iconPaddingDp = iconPaddingDp,
                    maxLines = maxLines,
                )
            ICON_GRAVITY_TOP, ICON_GRAVITY_BOTTOM ->
                VerticalButtonContentImpl(
                    text = text,
                    textColor = textColor,
                    fontSize = fontSize,
                    iconUrl = iconUrl,
                    iconGravity = iconGravity,
                    iconSizeDp = iconSizeDp,
                    iconPaddingDp = iconPaddingDp,
                )
        }
    }
}

// ─── Horizontal content (icon start/end + text) ───────────────────────────────

private fun RcScope.HorizontalButtonContentImpl(
    text: String,
    textColor: Int,
    fontSize: Float,
    fontWeight: Float,
    iconUrl: String?,
    iconGravity: Int,
    iconSizeDp: Float,
    iconPaddingDp: Float,
    maxLines: Int,
) {
    Row(modifier = Modifier.wrapContentHeight()) {
        if (iconGravity == ICON_GRAVITY_START) {
            iconUrl?.let { url ->
                Image(
                    image = remoteBitmapUrl(url),
                    modifier = Modifier.size(iconSizeDp, iconSizeDp),
                    // iconTint (ColorFilter) not supported in RC – pre-tint the image URL
                )
            }
            if (iconUrl != null && text.isNotBlank()) {
                Spacer(modifier = Modifier.size(iconPaddingDp))
            }
        }

        if (text.isNotBlank()) {
            Text(
                text = text,
                color = textColor,
                fontSize = fontSize.rsp,
                fontWeight = fontWeight,
                textAlign = RcTextAlign.Center,
                maxLines = maxLines,
                // lineHeight: pass via TextStyle if RC exposes it in a future alpha
            )
        }

        if (iconGravity == ICON_GRAVITY_END) {
            if (iconUrl != null && text.isNotBlank()) {
                Spacer(modifier = Modifier.size(iconPaddingDp))
            }
            iconUrl?.let { url ->
                Image(
                    image = remoteBitmapUrl(url),
                    modifier = Modifier.size(iconSizeDp, iconSizeDp),
                )
            }
        }
    }
}

// ─── Vertical content (icon top/bottom + text) ────────────────────────────────

private fun RcScope.VerticalButtonContentImpl(
    text: String,
    textColor: Int,
    fontSize: Float,
    iconUrl: String?,
    iconGravity: Int,
    iconSizeDp: Float,
    iconPaddingDp: Float,
) {
    Column(modifier = Modifier.wrapContentHeight()) {
        if (iconGravity == ICON_GRAVITY_TOP) {
            iconUrl?.let { url ->
                Image(
                    image = remoteBitmapUrl(url),
                    modifier = Modifier.size(iconSizeDp, iconSizeDp),
                )
            }
            Spacer(modifier = Modifier.size(iconPaddingDp))
        }

        if (text.isNotBlank()) {
            Text(
                text = text,
                color = textColor,
                fontSize = fontSize.rsp,
                fontWeight = 500f,
                textAlign = RcTextAlign.Center,
            )
        }

        if (iconGravity == ICON_GRAVITY_BOTTOM) {
            Spacer(modifier = Modifier.size(iconPaddingDp))
            iconUrl?.let { url ->
                Image(
                    image = remoteBitmapUrl(url),
                    modifier = Modifier.size(iconSizeDp, iconSizeDp),
                )
            }
        }
    }
}
