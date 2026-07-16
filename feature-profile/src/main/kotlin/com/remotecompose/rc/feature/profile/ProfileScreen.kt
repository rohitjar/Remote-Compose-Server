@file:Suppress("RestrictedApi")

package com.remotecompose.rc.feature.profile

import androidx.compose.remote.creation.Rc
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcInteger
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcScope
import androidx.compose.remote.creation.dsl.RcText
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.drawWithContent
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.verticalScroll
import androidx.compose.remote.creation.dsl.visibility
import androidx.compose.remote.creation.dsl.width
import androidx.compose.remote.creation.dsl.wrapContentHeight
import androidx.compose.remote.creation.dsl.wrapContentSize
import androidx.compose.remote.creation.modifiers.RoundedRectShape
import com.remotecompose.rc.core.RemoteUrlImage
import com.remotecompose.rc.core.RenderContext
import com.remotecompose.rc.core.rcDocument
import com.remotecompose.rc.core.rdp
import com.remotecompose.rc.core.rsp
import com.remotecompose.rc.modifier.onClick
import com.remotecompose.rc.modifier.padding
import com.remotecompose.rc.theme.Colors
import com.remotecompose.rc.theme.Icons

fun ProfileScreen(
    ctx: RenderContext = RenderContext(),
): ByteArray =
    rcDocument(ctx) {
        // Named USER: slots — every per-user value lives here so the binary itself is
        // user-agnostic (one cached skeleton for all users; the consumer fills these via
        // StateUpdater.setUserLocal*). MUST stay at document root: nested declarations
        // never register in the player's variable table and overrides silently no-op.
        // Bare slot names (after USER:) must match the keys in ProfileScreenData.toScreenData().
        val name        = remoteNamedText("USER:profile.name", "")
        val initials    = remoteNamedText("USER:profile.initials", "")
        val phone       = remoteNamedText("USER:profile.phone", "")
        val age         = remoteNamedText("USER:profile.age", "")
        val gender      = remoteNamedText("USER:profile.gender", "")
        val upi         = remoteNamedText("USER:profile.upi", "")
        val addresses   = remoteNamedText("USER:profile.addressCount", "")
        // KYC renders both variants; these toggles show exactly one (1=VISIBLE, 0=GONE).
        val kycVerified = remoteNamedInteger("USER:profile.kyc.verified", 0)
        val kycPending  = remoteNamedInteger("USER:profile.kyc.pending", 1)

        val bitmap = remoteNamedBitmapUrl("", "")

        // Safe-area strips match the profile card band (purple900); the content
        // column paints its own bgProfile surface so rows keep their color.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Colors.purple900)
                .padding(top = ctx.safeAreaTop.rdp, bottom = ctx.safeAreaBottom.rdp),
        ) {
            Column(modifier = Modifier.fillMaxSize().background(Colors.bgProfile)) {

                // Top bar: back arrow + title
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .background(Colors.purple900)
                        .padding(16.rdp, 12.rdp, 16.rdp, 12.rdp),
                    vertical = RcVerticalPositioning.Center,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                        vertical = RcVerticalPositioning.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.rdp)
                                .onClick {
                                    hostAction("onBack")
                                    analyticsApi.postEvent(
                                        event = "profile_back_clicked",
                                        values = mapOf(
                                            "screen" to "profile",
                                            "source" to "top_bar",
                                        ),
                                    )
                                },
                            horizontal = RcHorizontalPositioning.Center,
                            vertical = RcVerticalPositioning.Center,
                        ) {
                            Image(
                                image = remoteBitmapUrl("")
                            )
                            RemoteUrlImage(Icons.back, Modifier.wrapContentSize(), scale = Rc.ImageScale.FILL_BOUNDS, width = 96, height = 96)
                        }
                        Spacer(modifier = Modifier.width(6.rdp))
                        Text(
                            text = "Profile",
                            color = Colors.textPrimary,
                            fontSize = 14.rsp,
                            fontWeight = 700f,
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll()
                ) {
                    // Profile card (avatar + name + phone) — exact Figma "main details" frame.
                    ProfileCard(
                        initials = initials,
                        name = name,
                        phone = phone,
                    )

                    // "Profile Details" header + edit icon.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.rdp, 16.rdp, 16.rdp, 0.rdp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().wrapContentHeight()
                                .padding(bottom = 16.rdp),
                            vertical = RcVerticalPositioning.Center,
                        ) {
                            Text(
                                text = "Profile Details",
                                color = Colors.textPrimary,
                                fontSize = 14.rsp,
                                fontWeight = 700f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            RemoteUrlImage(Icons.edit, Modifier.size(20.rdp, 20.rdp))
                        }
                    }

                    // Detail rows.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.rdp, end = 16.rdp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ProfileRowItem(label = "Name", value = name)
                            RowGap()
                            ProfileRowItem(label = "Mobile ", value = phone)
                            RowGap()
                            ProfileRowItem(
                                label = "Age",
                                value = age,
                                actionPayload = "dl.myjar.app/dynamicUI/greeting"
                            )
                            RowGap()
                            ProfileRowItem(
                                label = "Gender",
                                value = gender,
                                actionPayload = "dl.myjar.app/dynamicUI/image_list"
                            )
                            RowGap()
                            KycRow(verifiedVis = kycVerified, pendingVis = kycPending)
                            RowGap()
                            ProfileRowItem(
                                label = "Manage UPI ID",
                                value = upi,
                                trailingChevron = true,
                                actionPayload = "dl.myjar.app/dynamicUI/button"
                            )
                            RowGap()
                            ProfileRowItem(
                                label = "Saved Addresses",
                                value = addresses,
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
    }

// 16dp gap between rows.
private fun RcScope.RowGap() {
    Box(modifier = Modifier.fillMaxWidth().height(16.rdp)) {}
}

private fun RcScope.ProfileCard(initials: RcText, name: RcText, phone: RcText) {
    val cardShape   = RoundedRectShape(16.rdp.value, 16.rdp.value, 0.rdp.value, 0.rdp.value) // top corners only
    val avatarShape = RoundedRectShape(30.rdp.value, 30.rdp.value, 30.rdp.value, 30.rdp.value) // 60dp circle
    val bandShape   = RoundedRectShape(0.rdp.value, 0.rdp.value, 8.rdp.value, 8.rdp.value)    // bottom corners only (Figma: 8dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(bandShape)
            .drawWithContent {
                paint { color(Colors.purple900) }
                drawRect(0f.rf, 0f.rf, this.width, this.height)
                drawComponentContent()

                // Band hairline (#554766, 1dp): Figma strokes the bottom edge only,
                // inside-aligned, following the 8dp bottom corner radius.
                val strokeW = 1.rdp
                val inset = strokeW.value / 2f
                val radius = 8.rdp.value
                val w = this.width
                val h = this.height
                paint {
                    // Flat two-stop gradient instead of color(): the card's fill
                    // gradient (drawn as part of the content above) leaks its shader
                    // into this shared paint and color() alone doesn't clear it.
                    color(Colors.white)
                    linearGradient(
                        0f, 0f, 1f, 0f,
                        colors = intArrayOf(Colors.bandBorder, Colors.bandBorder),
                    )
                    style(RcPaintStyle.Stroke)
                    strokeWidth(strokeW.value)
                }
                drawArc(
                    inset.rf, h - inset - radius * 2f,
                    (inset + radius * 2f).rf, h - inset,
                    90f.rf, 90f.rf,
                )
                drawArc(
                    w - inset - radius * 2f, h - inset - radius * 2f,
                    w - inset, h - inset,
                    0f.rf, 90f.rf,
                )
                drawLine((inset + radius).rf, h - inset, w - inset - radius, h - inset)
            },
    ) {
        // Card inset: 16dp from band top, 20dp side margins.
        Box(modifier = Modifier.fillMaxWidth().padding(20.rdp, 16.rdp, 20.rdp, 0.rdp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .drawWithContent {
                        paint { color(Colors.purple700) }
                        drawRect(0f.rf, 0f.rf, this.width, this.height)
                        paint {
                            linearGradient(
                                startX = 0f, startY = 0f,
                                endX = 320.rdp.value, endY = 76.rdp.value,
                                colors = intArrayOf(0x3DFFFFFF, 0x00FFFFFF),
                            )
                        }
                        drawRect(0f.rf, 0f.rf, this.width, this.height)
                        drawComponentContent()

                        // Card stroke (2dp): top/left/right only, rounded top corners —
                        // Figma omits a bottom edge. Two passes matching the two Figma
                        // strokes: a solid #8D54D6 @40% base, then a #F1EAFA radial
                        // highlight fading out from the top-left corner. The base pass
                        // uses a flat two-stop gradient because color() alone doesn't
                        // clear the fill's leaked shader from the shared paint.
                        val strokeW = 2.rdp
                        val inset = strokeW.value / 2f
                        val radius = 16.rdp.value
                        val w = this.width
                        val h = this.height
                        val strokeOutline = {
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
                        paint {
                            color(Colors.white) // full alpha so the shader isn't dimmed
                            linearGradient(
                                0f, 0f, 1f, 0f,
                                colors = intArrayOf(Colors.cardBorderBase, Colors.cardBorderBase),
                            )
                            style(RcPaintStyle.Stroke)
                            strokeWidth(strokeW.value)
                        }
                        strokeOutline()
                        paint {
                            color(Colors.white)
                            radialGradient(
                                centerX = 0f, centerY = 0f,
                                radius = 320.rdp.value,
                                colors = intArrayOf(Colors.cardBorderGlow, Colors.cardBorderGlowEnd),
                                positions = floatArrayOf(0f, 0.7f),
                            )
                            style(RcPaintStyle.Stroke)
                            strokeWidth(strokeW.value)
                        }
                        strokeOutline()
                    }
                    .padding(16.rdp, 16.rdp, 16.rdp, 16.rdp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    vertical = RcVerticalPositioning.Center,
                ) {
                    // Avatar: 60dp circle, "AS" initials centered.
                    Box(
                        modifier = Modifier
                            .size(60.rdp, 60.rdp)
                            .clip(avatarShape)
                            .background(Colors.cardBorderLight),
                        horizontal = RcHorizontalPositioning.Center,
                        vertical = RcVerticalPositioning.Center,
                    ) {
//                        RemoteUrlImage(
//                            url = "https://cdn.myjar.app/profilePics/64d211e6c82e5256c8c698276a290c94cd6c6d0339b75c42.jpg",
//                            width = 230,
//                            height = 512,
//                            modifier = Modifier.onClick { hostAction("editImage") },
//                            scale = RemoteComposeWriter.IMAGE_SCALE_FILL_BOUNDS
//                        )
                        Text(
                            text = initials,
                            color = Colors.purple500,
                            fontSize = 24.rsp,
                            fontWeight = 700f,
                        )
                    }

                    Spacer(modifier = Modifier.width(12.rdp).wrapContentHeight()) // 12dp gap

                    Column(modifier = Modifier.wrapContentHeight()) {
                        Text(
                            text = name,
                            color = Colors.textPrimary,
                            fontSize = 16.rsp,
                            fontWeight = 700f,
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(4.rdp)) {} // 4dp gap
                        Text(
                            text = phone,
                            color = Colors.textPrimary,
                            fontSize = 14.rsp,
                            fontWeight = 500f,
                        )
                    }
                }
            }
        }
    }
}

private fun RcScope.ProfileRowItem(
    label: String,
    value: RcText? = null,
    trailingChevron: Boolean = false,
    actionName: String = "deeplink",
    actionPayload: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        var rowModifier = Modifier
            .fillMaxWidth()
            .padding(8.rdp, 4.rdp, 0.rdp, 10.rdp)
        if (actionPayload != null) {
            rowModifier = rowModifier.onClick {
                hostAction(actionName, actionPayload)
                analyticsApi.postEvent(
                    event = "profile_row_clicked",
                    values = mapOf(
                        "screen" to "profile",
                        "label" to label,
                        "deeplink" to actionPayload,
                    ),
                )
            }
        }
        Row(
            modifier = rowModifier,
            vertical = RcVerticalPositioning.Center,
        ) {
            Box(modifier = Modifier.width(140.rdp).wrapContentHeight()) {
                Text(
                    text = label,
                    color = Colors.textSecondary,
                    fontSize = 12.rsp,
                    fontWeight = 700f,
                )
            }
            if (value != null) {
                Box(modifier = Modifier.width(24.rdp).wrapContentHeight()) {}
                Text(
                    text = value,
                    color = Colors.textPrimary,
                    fontSize = 14.rsp,
                    fontWeight = 500f,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            if (trailingChevron) {
                RemoteUrlImage(Icons.chevronRight, Modifier.size(24.rdp), width = 96, height = 96)
            }
        }
        Divider()
    }
}

// Bakes BOTH the "Verified" chip and the "Complete Now" text; the consumer shows exactly
// one by setting the profile.kyc.verified / profile.kyc.pending ints (1=VISIBLE, 0=GONE).
private fun RcScope.KycRow(verifiedVis: RcInteger, pendingVis: RcInteger) {
    val chipShape = RoundedRectShape(8.rdp.value, 8.rdp.value, 8.rdp.value, 8.rdp.value)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .onClick {
                    hostAction("deeplink", "dl.myjar.app/image_list")
                    analyticsApi.postEvent(
                        event = "profile_row_clicked",
                        values = mapOf(
                            "screen" to "profile",
                            "label" to "KYC Verification",
                            "deeplink" to "dl.myjar.app/image_list",
                        ),
                    )
                }
                .fillMaxWidth()
                .padding(8.rdp, 4.rdp, 0.rdp, 10.rdp),
            vertical = RcVerticalPositioning.Center,
        ) {
            Box(modifier = Modifier.width(140.rdp).wrapContentHeight()) {
                Text(
                    text = "KYC Verification",
                    color = Colors.textSecondary,
                    fontSize = 12.rsp,
                    fontWeight = 700f,
                    maxLines = 1,
                )
            }
            Box(modifier = Modifier.width(24.rdp).wrapContentHeight()) {}

            Box(
                modifier = Modifier
                    .visibility(verifiedVis)
                    .wrapContentHeight()
                    .clip(chipShape)
                    .background(Colors.green600)
                    .padding(8.rdp, 4.rdp, 10.rdp, 4.rdp),
                vertical = RcVerticalPositioning.Center,
            ) {
                Row(
                    modifier = Modifier.wrapContentHeight(),
                    vertical = RcVerticalPositioning.Center,
                ) {
                    RemoteUrlImage(Icons.check, Modifier.size(16.rdp, 16.rdp))
                    Box(modifier = Modifier.width(4.rdp).wrapContentHeight()) {}
                    Text(
                        text = "Verified",
                        color = Colors.textPrimary,
                        fontSize = 14.rsp,
                        fontWeight = 500f,
                    )
                }
            }
            Text(
                text = "Complete Now",
                color = Colors.textPrimary,
                fontSize = 14.rsp,
                fontWeight = 500f,
                modifier = Modifier.visibility(pendingVis),
            )

            // flexible spacer pushes the chevron to the right edge (Figma)
            Spacer(modifier = Modifier.weight(0.5f))
            RemoteUrlImage(Icons.chevronRight, Modifier.size(24.rdp), width = 96, height = 96)
        }
        Divider()
    }
}

private fun RcScope.Divider() {
    Box(modifier = Modifier.fillMaxWidth().padding(start = 8.rdp, end = 8.rdp).height(1.rdp).background(Colors.divider)) {}
}
