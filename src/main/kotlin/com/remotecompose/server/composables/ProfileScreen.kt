@file:Suppress("RestrictedApi")

package com.remotecompose.server.composables

import androidx.compose.remote.creation.Rc
import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcScope
import androidx.compose.remote.creation.dsl.RcSp
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.drawWithContent
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.onClick
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.ripple
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.toRecordingModifier
import androidx.compose.remote.creation.dsl.width
import androidx.compose.remote.creation.dsl.wrapContentHeight
import androidx.compose.remote.creation.dsl.wrapContentSize
import androidx.compose.remote.creation.modifiers.RoundedRectShape

/**
 * Pixel-perfect port of the JAR "Profile" Figma frame (node 3484:8240, 360×800 dp).
 *
 * SIZING MODEL — read this before touching dimensions:
 *  The document is generated with DENSITY_BEHAVIOR_DP (see rcDocument). Under that
 *  behavior the PLAYER multiplies every authored value by the device density once,
 *  at render time. So author EVERYTHING in raw dp / sp — never pre-multiply, or the
 *  device scales it a second time (the old ×2.625 / ×3 model did exactly that and
 *  made the whole screen render oversized).
 *   - Modifier dimensions (padding/size/width/height) take dp  → use [dp] (identity).
 *   - Font sizes take RcSp                                      → use Int.rsp.
 *  The frame is 360×800 dp (see ProfileScreen() widthDp/heightDp args).
 *
 * Tokens are the exact Figma variables (get_variable_defs on 3484:8240).
 */

// ─── Exact Figma color tokens (ARGB) ──────────────────────────────────────────
private const val COLOR_BG               = 0xFF201929.toInt() // ui/background/color-bg (page surface)
private const val COLOR_PURPLE_900       = 0xFF160829.toInt() // visual/purple/purple-900 (band base)
private const val COLOR_TEXT_PRIMARY     = 0xFFFFFFFF.toInt() // ui/text/color_text_primary
private const val COLOR_TEXT_SECONDARY   = 0xFFBEB4CC.toInt() // ui/text/color_text_secondary
private const val COLOR_DIVIDER          = 0x52837299.toInt() // rgba(131,114,153,0.32)
private const val COLOR_CARD_BG          = 0xFF43197A.toInt() // visual/purple/purple-700 (card fill)
private const val COLOR_CARD_BORDER      = 0xFFF1EAFA.toInt() // card stroke (#F1EAFA, 2dp top/sides)
private const val COLOR_BAND_BORDER      = 0xFF554766.toInt() // ui/background/surface/color_bg_label
private const val COLOR_AVATAR_BG        = 0xFFF1EAFA.toInt() // avatar circle (light) behind initials
private const val COLOR_AVATAR_INITIALS  = 0xFF7029CC.toInt() // visual/purple/purple-500
private const val COLOR_VERIFIED_BG      = 0xFF21A357.toInt() // green-600 (chip gradient, solid)

// ─── Icon URLs (swap for the real JAR CDN assets) ─────────────────────────────
// Served via remoteBitmapUrl + the consumer's Coil-backed CoilBitmapLoader.
// Requires Limits.ENABLE_IMAGE_URLS = true on the player (already set).
private const val ICON_ARROW_LEFT  = "https://cdn.myjar.app/rc/core/ic_back.webp"
private const val ICON_EDIT        = "https://cdn-icons-png.flaticon.com/512/1159/1159633.png"
private const val ICON_CHEVRON     = "https://cdn.myjar.app/rc/core/ic_arrow_right.webp"
private const val ICON_CHECK       = "https://cdn-icons-png.flaticon.com/512/845/845646.png"

// Declared image-slot size. The player's RemoteBitmapDecoder.checkBounds throws if a fetched
// bitmap is LARGER than the slot the document declared, and the DSL's remoteBitmapUrl() hard-codes
// the slot to 1×1 (which always fails). We declare a generous slot and the consumer's
// CoilBitmapLoader downscales every fetched image to fit within this cap — keep the two in sync
// (CoilBitmapLoader.DEFAULT_MAX_IMAGE_DIMENSION_PX = 512).
private const val IMAGE_SLOT_PX = 512

// ─── dp helper ────────────────────────────────────────────────────────────────
// Author in raw dp. With DENSITY_BEHAVIOR_DP the player scales to pixels once, on
// device, so this is an identity conversion — do NOT bake density in here.
private fun dp(value: Int): Float = value.toFloat()*2f

// ─── Sized URL image helper ───────────────────────────────────────────────────
// The high-level RcScope DSL only exposes remoteBitmapUrl(url) which declares a 1×1 slot and thus
// always trips RemoteBitmapDecoder.checkBounds. The sized writer.addBitmapUrl(url, w, h) is the
// only way to declare a real slot, but the writer is library-`internal`. We reach it via a single
// reflective field read (RcScopeImpl.writer); everything else (toRecordingModifier, writer.image,
// IMAGE_SCALE_*) is public. Isolated here so a library change only touches one function.
private val rcScopeWriterField by lazy {
    Class.forName("androidx.compose.remote.creation.dsl.RcScopeImpl")
        .getDeclaredField("writer")
        .apply { isAccessible = true }
}

private fun RcScope.writer(): RemoteComposeWriter =
    rcScopeWriterField.get(this) as RemoteComposeWriter

/** Draws a URL image declaring an [IMAGE_SLOT_PX]² slot so the decoder's bounds check passes. */
private fun RcScope.RemoteUrlImage(url: String, modifier: Modifier, scale: Int = RemoteComposeWriter.IMAGE_SCALE_FIT, size: Int = IMAGE_SLOT_PX) {
    val w = writer()
    val imageId = w.addBitmapUrl(url, size, size)
    w.image(modifier.toRecordingModifier(), imageId, scale, 1f)
}


public val Int.rsp: RcSp
    get() = RcSp(this.toFloat()*2f)

// ─── Data model (per-user fields) ─────────────────────────────────────────────
data class ProfileScreenData(
    val title: String = "Profile",
    val userName: String = "Monal Ambastha",
    val userInitials: String = "AS",
    val phoneNumber: String = "+91-9869409330",
    val age: String = "28",
    val gender: String = "Male",
    val kycVerified: Boolean = true,
    val primaryUpiId: String = "9999999999@ybl",
    val savedAddressCount: Int = 2,
)

// ─── Entry point ──────────────────────────────────────────────────────────────
fun ProfileScreen(data: ProfileScreenData = ProfileScreenData()): ByteArray =
    rcDocument(widthDp = 360, heightDp = 800) {
        Box(modifier = Modifier.fillMaxSize().background(COLOR_BG)) {
            Column(modifier = Modifier.fillMaxWidth()) {

                // Top bar: back arrow + title — same static color as band.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .background(COLOR_PURPLE_900)
                        .padding(dp(8), dp(12), dp(16), dp(0)),
                    vertical = RcVerticalPositioning.Center,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                        vertical = RcVerticalPositioning.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(dp(24), dp(24))
                                .hostActionValue(
                                    name = "analytics",
                                    payloadJson = """{"eventName":"profile_back_clicked","params":{"screen":"profile","source":"top_bar"}}""",
                                ),
                            horizontal = RcHorizontalPositioning.Center,
                            vertical = RcVerticalPositioning.Center,
                        ) {
                            Image(
                                image = remoteBitmapUrl("")
                            )
                            RemoteUrlImage(ICON_ARROW_LEFT, Modifier.wrapContentSize(), scale = Rc.ImageScale.FILL_BOUNDS, size = 96)
                        }
                        Spacer(modifier = Modifier.width(dp(6)))
                        Text(
                            text = data.title,
                            color = COLOR_TEXT_PRIMARY,
                            fontSize = 14.rsp,
                            fontWeight = 700f,
                        )
                    }
                }

                // Profile card (avatar + name + phone) — exact Figma "main details" frame.
                ProfileCard(
                    initials = data.userInitials,
                    name = data.userName,
                    phone = data.phoneNumber,
                )

                // "Profile Details" header + edit icon.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dp(8), dp(16), dp(16), dp(0)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(bottom = dp(16)),
                        vertical = RcVerticalPositioning.Center,
                    ) {
                        Text(
                            text = "Profile Details",
                            color = COLOR_TEXT_PRIMARY,
                            fontSize = 14.rsp,
                            fontWeight = 700f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        RemoteUrlImage(ICON_EDIT, Modifier.size(dp(20), dp(20)))
                    }
                }

                // Detail rows.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ProfileRowItem(label = "Age", value = data.age, actionPayload = "dl.myjar.app/agePopUp")
                        RowGap()
                        ProfileRowItem(label = "Gender", value = data.gender, actionPayload = "dl.myjar.app/genderPopUp")
                        RowGap()
                        KycRow(isVerified = data.kycVerified)
                        RowGap()
                        ProfileRowItem(
                            label = "Manage UPI ID",
                            value = data.primaryUpiId,
                            trailingChevron = true,
                            actionPayload = "dl.myjar.app/manageUpiId"
                        )
                        RowGap()
                        ProfileRowItem(
                            label = "Saved Addresses",
                            value = data.savedAddressCount.toString(),
                            trailingChevron = true,
                            actionPayload = "dl.myjar.app/savedAddress",
                        )
                        RowGap()
                        ProfileRowItem(
                            label = "Nominee Details",
                            trailingChevron = true,
                            actionPayload = "dl.myjar.app/nomineeDetails",
                        )
                    }
                }
            }
        }
    }

// 16dp gap between rows.
private fun RcScope.RowGap() {
    Box(modifier = Modifier.fillMaxWidth().height(dp(16))) {}
}

// ─── Profile card — exact Figma "main details" frame (3484:8253) ──────────────
// Band: 108dp tall, bottom corners 8dp, bottom border #554766 (1dp), purple-900 base.
// Card: 320dp wide (16dp top from band, 20dp side margins), TOP corners 16dp only, 2dp #F1EAFA
//       stroke, #43197A fill, 16dp inner padding. Avatar 60dp circle + 12dp gap + name/phone.
private fun RcScope.ProfileCard(initials: String, name: String, phone: String) {
    val cardShape   = RoundedRectShape(dp(16), dp(16), dp(0), dp(0)) // top corners only
    val avatarShape = RoundedRectShape(dp(30), dp(30), dp(30), dp(30)) // 60dp circle
    val bandShape   = RoundedRectShape(dp(0), dp(0), dp(8), dp(8))    // bottom corners only (Figma: 8dp)

    // Band: square top (flush with the top bar), rounded bottom (8dp), purple-900 base.
    // STATIC solid fill (only the inner card has a gradient).
    //
    // NOTE: Figma also shows a bottom-only 1dp #554766 hairline on this band. Three different
    // approaches to add it were each reverted after producing a visibly broken render:
    //   1. border() modifier — always outlines all 4 sides with one uniform corner radius,
    //      which wraps the square top in an unwanted rounded edge.
    //   2. A second drawWithContent on this Box (to hand-draw a bottom-only stroke) — corrupted
    //      the nested card's own drawWithContent (gradient + border silently stopped rendering).
    //   3. A declarative leaf Box below the card, clipped with the same bandShape — produced an
    //      unrelated stray border artifact along the screen edge, cause not yet understood.
    // Left off rather than risk a fourth regression; needs a proper look at the player/runtime
    // source before trying again.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(bandShape)
            .background(COLOR_PURPLE_900),
    ) {
        // Card inset: 16dp from band top, 20dp side margins.
        Box(modifier = Modifier.fillMaxWidth().padding(dp(10), dp(8), dp(10), dp(0))) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .drawWithContent {
                        paint { color(COLOR_CARD_BG) }
                        drawRect(0f.rf, 0f.rf, this.width, this.height)
                        paint {
                            linearGradient(
                                startX = 0f, startY = 0f,
                                endX = dp(320), endY = dp(76),
                                colors = intArrayOf(0x3DFFFFFF, 0x00FFFFFF),
                            )
                        }
                        drawRect(0f.rf, 0f.rf, this.width, this.height)
                        drawComponentContent()

                        // Card stroke (#F1EAFA, 2dp): top/left/right only, rounded top
                        // corners — Figma omits a bottom edge. Drawn last, on top of the
                        // fill and content above, since it'd otherwise be painted over.
                        val strokeW = dp(2)
                        val inset = strokeW / 2f
                        val radius = dp(16)
                        val w = this.width
                        val h = this.height
                        paint {
                            color(COLOR_CARD_BORDER)
                            style(RcPaintStyle.Stroke)
                            strokeWidth(strokeW)
                        }
                        drawArc(
                            inset.rf, inset.rf,
                            (inset + radius * 2f).rf, (inset + radius * 2f).rf,
                            180f.rf, 90f.rf,
                        )
                        drawArc(
                            w - inset - radius * 2f, inset.rf,
                            w - inset, (inset + radius * 2f).rf,
                            270f.rf, 90f.rf,
                        )
                        drawLine((inset + radius).rf, inset.rf, w - inset - radius, inset.rf)
                        drawLine(inset.rf, (inset + radius).rf, inset.rf, h)
                        drawLine(w - inset, (inset + radius).rf, w - inset, h)
                    }
                    .padding(dp(8), dp(8), dp(8), dp(8))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    vertical = RcVerticalPositioning.Center,
                ) {
                    // Avatar: 60dp circle, "AS" initials centered.
                    Box(
                        modifier = Modifier
                            .size(dp(60), dp(60))
                            .clip(avatarShape)
                            .background(COLOR_AVATAR_BG),
                        horizontal = RcHorizontalPositioning.Center,
                        vertical = RcVerticalPositioning.Center,
                    ) {
                        Text(
                            text = initials,
                            color = COLOR_AVATAR_INITIALS,
                            fontSize = 24.rsp,
                            fontWeight = 700f,
                        )
                    }

                    Spacer(modifier = Modifier.width(dp(12)).wrapContentHeight()) // 12dp gap

                    Column(modifier = Modifier.wrapContentHeight()) {
                        Text(
                            text = name,
                            color = COLOR_TEXT_PRIMARY,
                            fontSize = 16.rsp,
                            fontWeight = 700f,
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(dp(4))) {} // 4dp gap
                        Text(
                            text = phone,
                            color = COLOR_TEXT_PRIMARY,
                            fontSize = 14.rsp,
                            fontWeight = 500f,
                        )
                    }
                }
            }
        }
    }
}

// ─── Unified profile row (label + optional value + optional chevron) ──────────
private fun RcScope.ProfileRowItem(
    label: String,
    value: String = "",
    trailingChevron: Boolean = false,
    actionName: String? = "deeplink",
    actionPayload: String? = "dl.myjar.app/defaultDeeplink",
) {
    val hasValue = value.isNotEmpty()
    Column(modifier = Modifier.fillMaxWidth()) {
        var rowModifier = Modifier
            .fillMaxWidth()
            .padding(dp(8), dp(4), dp(16), dp(10))
        if (actionName != null) {
            rowModifier = if (actionPayload != null) {
                rowModifier.hostActionValue(actionName, actionPayload).ripple()
            } else {
                rowModifier.onClick { hostAction(actionName) }.ripple()
            }
        }
        Row(
            modifier = rowModifier,
            vertical = RcVerticalPositioning.Center,
        ) {
            Box(modifier = Modifier.width(dp(140)).wrapContentHeight()) {
                Text(
                    text = label,
                    color = COLOR_TEXT_SECONDARY,
                    fontSize = 12.rsp,
                    fontWeight = 700f,
                )
            }
            if (hasValue) {
                Box(modifier = Modifier.width(dp(24)).wrapContentHeight()) {}
                Text(
                    text = value,
                    color = COLOR_TEXT_PRIMARY,
                    fontSize = 14.rsp,
                    fontWeight = 500f,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            if (trailingChevron) {
                RemoteUrlImage(ICON_CHEVRON, Modifier.size(dp(24)), size = 96)
            }
        }
        Divider()
    }
}

// ─── KYC row: label + green "Verified" chip + chevron ─────────────────────────
private fun RcScope.KycRow(isVerified: Boolean) {
    val chipShape = RoundedRectShape(dp(8), dp(8), dp(8), dp(8))

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .hostActionValue("deeplink", "dl.myjar.app/kyc")
                .fillMaxWidth()
                // Match ProfileRowItem's right inset (dp(16)) so the chevron aligns
                // with the chevrons on the other rows instead of running to the edge.
                .padding(dp(8), dp(4), dp(16), dp(10))
                .ripple(),
            vertical = RcVerticalPositioning.Center,
        ) {
            Box(modifier = Modifier.width(dp(140)).wrapContentHeight()) {
                Text(
                    text = "KYC Verification",
                    color = COLOR_TEXT_SECONDARY,
                    fontSize = 12.rsp,
                    fontWeight = 700f,
                    maxLines = 1,
                )
            }
            Box(modifier = Modifier.width(dp(24)).wrapContentHeight()) {}

            if (isVerified) {
                Box(
                    modifier = Modifier
                        .wrapContentHeight()
                        .clip(chipShape)
                        .background(COLOR_VERIFIED_BG)
                        .padding(dp(8), dp(4), dp(10), dp(4)),
                    vertical = RcVerticalPositioning.Center,
                ) {
                    Row(
                        modifier = Modifier.wrapContentHeight(),
                        vertical = RcVerticalPositioning.Center,
                    ) {
                        RemoteUrlImage(ICON_CHECK, Modifier.size(dp(16), dp(16)))
                        Box(modifier = Modifier.width(dp(4)).wrapContentHeight()) {}
                        Text(
                            text = "Verified",
                            color = COLOR_TEXT_PRIMARY,
                            fontSize = 14.rsp,
                            fontWeight = 500f,
                        )
                    }
                }
            } else {
                Text(
                    text = "Complete Now",
                    color = COLOR_TEXT_PRIMARY,
                    fontSize = 14.rsp,
                    fontWeight = 500f,
                )
            }

            // flexible spacer pushes the chevron to the right edge (Figma)
            Spacer(modifier = Modifier.weight(0.5f))
            RemoteUrlImage(ICON_CHEVRON, Modifier.size(dp(24)), size = 96)
        }
        Divider()
    }
}

// ─── 1dp divider line ─────────────────────────────────────────────────────────
private fun RcScope.Divider() {
    Box(modifier = Modifier.fillMaxWidth().padding(start = dp(8), end = dp(8)).height(dp(1)).background(COLOR_DIVIDER)) {}
}
