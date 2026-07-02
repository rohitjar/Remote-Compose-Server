@file:Suppress("RestrictedApi")

package com.remotecompose.rc.feature.profile

import androidx.compose.remote.creation.Rc
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcScope
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
import androidx.compose.remote.creation.dsl.width
import androidx.compose.remote.creation.dsl.wrapContentHeight
import androidx.compose.remote.creation.dsl.wrapContentSize
import androidx.compose.remote.creation.modifiers.RoundedRectShape
import com.remotecompose.rc.core.RemoteUrlImage
import com.remotecompose.rc.core.RenderContext
import com.remotecompose.rc.core.dp
import com.remotecompose.rc.core.rcDocument
import com.remotecompose.rc.core.rsp
import com.remotecompose.rc.modifier.hostActionValue
import com.remotecompose.rc.theme.Colors
import com.remotecompose.rc.theme.Icons

// Colors and icon URLs are shared design tokens — see :core-ui theme.Colors / theme.Icons.

// ─── Entry point ──────────────────────────────────────────────────────────────
fun ProfileScreen(
    ctx: RenderContext = RenderContext(),
    data: ProfileScreenData = ProfileScreenData(),
): ByteArray =
    rcDocument(ctx) {
        Box(modifier = Modifier.fillMaxSize().background(Colors.bgProfile)) {
            Column(modifier = Modifier.fillMaxWidth()) {

                // Top bar: back arrow + title — same static color as band.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .background(Colors.purple900)
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
                            RemoteUrlImage(Icons.back, Modifier.wrapContentSize(), scale = Rc.ImageScale.FILL_BOUNDS, size = 96)
                        }
                        Spacer(modifier = Modifier.width(dp(6)))
                        Text(
                            text = data.title,
                            color = Colors.textPrimary,
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
                            color = Colors.textPrimary,
                            fontSize = 14.rsp,
                            fontWeight = 700f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        RemoteUrlImage(Icons.edit, Modifier.size(dp(20), dp(20)))
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

private fun RcScope.ProfileCard(initials: String, name: String, phone: String) {
    val cardShape   = RoundedRectShape(dp(16), dp(16), dp(0), dp(0)) // top corners only
    val avatarShape = RoundedRectShape(dp(30), dp(30), dp(30), dp(30)) // 60dp circle
    val bandShape   = RoundedRectShape(dp(0), dp(0), dp(8), dp(8))    // bottom corners only (Figma: 8dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(bandShape)
            .background(Colors.purple900),
    ) {
        // Card inset: 16dp from band top, 20dp side margins.
        Box(modifier = Modifier.fillMaxWidth().padding(dp(10), dp(8), dp(10), dp(0))) {
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
                            color(Colors.cardBorderLight)
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
                            .background(Colors.cardBorderLight),
                        horizontal = RcHorizontalPositioning.Center,
                        vertical = RcVerticalPositioning.Center,
                    ) {
                        Text(
                            text = initials,
                            color = Colors.purple500,
                            fontSize = 24.rsp,
                            fontWeight = 700f,
                        )
                    }

                    Spacer(modifier = Modifier.width(dp(12)).wrapContentHeight()) // 12dp gap

                    Column(modifier = Modifier.wrapContentHeight()) {
                        Text(
                            text = name,
                            color = Colors.textPrimary,
                            fontSize = 16.rsp,
                            fontWeight = 700f,
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(dp(4))) {} // 4dp gap
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
                    color = Colors.textSecondary,
                    fontSize = 12.rsp,
                    fontWeight = 700f,
                )
            }
            if (hasValue) {
                Box(modifier = Modifier.width(dp(24)).wrapContentHeight()) {}
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
                RemoteUrlImage(Icons.chevronRight, Modifier.size(dp(24)), size = 96)
            }
        }
        Divider()
    }
}

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
                    color = Colors.textSecondary,
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
                        .background(Colors.green600)
                        .padding(dp(8), dp(4), dp(10), dp(4)),
                    vertical = RcVerticalPositioning.Center,
                ) {
                    Row(
                        modifier = Modifier.wrapContentHeight(),
                        vertical = RcVerticalPositioning.Center,
                    ) {
                        RemoteUrlImage(Icons.check, Modifier.size(dp(16), dp(16)))
                        Box(modifier = Modifier.width(dp(4)).wrapContentHeight()) {}
                        Text(
                            text = "Verified",
                            color = Colors.textPrimary,
                            fontSize = 14.rsp,
                            fontWeight = 500f,
                        )
                    }
                }
            } else {
                Text(
                    text = "Complete Now",
                    color = Colors.textPrimary,
                    fontSize = 14.rsp,
                    fontWeight = 500f,
                )
            }

            // flexible spacer pushes the chevron to the right edge (Figma)
            Spacer(modifier = Modifier.weight(0.5f))
            RemoteUrlImage(Icons.chevronRight, Modifier.size(dp(24)), size = 96)
        }
        Divider()
    }
}

private fun RcScope.Divider() {
    Box(modifier = Modifier.fillMaxWidth().padding(start = dp(8), end = dp(8)).height(dp(1)).background(Colors.divider)) {}
}
