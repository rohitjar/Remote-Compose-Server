package com.remotecompose.rc.feature.samples

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import com.remotecompose.rc.core.dp
import com.remotecompose.rc.core.rsp
import com.remotecompose.rc.theme.Colors
import com.remotecompose.rc.components.button.ICON_GRAVITY_END
import com.remotecompose.rc.components.button.JarButton
import com.remotecompose.rc.components.button.JarPillButton
import com.remotecompose.rc.components.button.JarPrimaryButton
import com.remotecompose.rc.components.button.JarSecondaryButton
import com.remotecompose.rc.components.button.JarSpecialBlackButton
import com.remotecompose.rc.components.button.JarSpecialWhiteButton
import com.remotecompose.rc.components.button.JarTertiaryButton
import com.remotecompose.rc.components.button.RcButtonType
import com.remotecompose.rc.components.button.RenderImagePillButton
import com.remotecompose.rc.core.RenderContext
import com.remotecompose.rc.core.rcDocument

// ─── Demo screen ─────────────────────────────────────────────────────────────

fun JarButtonScreen(ctx: RenderContext = RenderContext()): ByteArray = rcDocument(ctx) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(0xFF1A1A2E.toInt()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dp(24), dp(24), dp(24), dp(24)),
        ) {
            Text(
                text = "JarButton variants — RC",
                color = Colors.white,
                fontSize = 22.rsp,   // core rsp (density-baked)
                fontWeight = 700f,
            )

            Spacer(modifier = Modifier.size(dp(16)))

            // PRIMARY – instanceKey must be unique per button on this screen
            JarPrimaryButton(
                text = "Get Started",
                actionName = "jar:cta:primary",
                instanceKey = "demo_primary",
            )

            Spacer(modifier = Modifier.size(dp(12)))

            // SECONDARY
            JarSecondaryButton(
                text = "Learn More",
                actionName = "jar:cta:secondary",
                instanceKey = "demo_secondary",
            )

            Spacer(modifier = Modifier.size(dp(12)))

            // SPECIAL_BLACK
            JarSpecialBlackButton(
                text = "Special Black",
                actionName = "jar:cta:special_black",
                instanceKey = "demo_special_black",
            )

            Spacer(modifier = Modifier.size(dp(12)))

            // SPECIAL_WHITE
            JarSpecialWhiteButton(
                text = "Special White",
                actionName = "jar:cta:special_white",
                instanceKey = "demo_special_white",
            )

            Spacer(modifier = Modifier.size(dp(12)))

            // TERTIARY
            JarTertiaryButton(
                text = "Tertiary (link)",
                actionName = "jar:cta:tertiary",
                instanceKey = "demo_tertiary",
            )

            Spacer(modifier = Modifier.size(dp(12)))

            // PILL
            JarPillButton(
                text = "Buy",
                actionName = "jar:cta:pill",
                instanceKey = "demo_pill",
            )

            Spacer(modifier = Modifier.size(dp(12)))

            // DISABLED primary – stays visible at 30% alpha
            JarPrimaryButton(
                text = "Disabled",
                actionName = "jar:cta:disabled",
                instanceKey = "demo_disabled",
                isEnabled = false,
                // Optional: fire a toast/snackbar HostAction even when disabled
                disabledActionName = "jar:cta:disabled_tap",
            )

            Spacer(modifier = Modifier.size(dp(12)))

            // Primary with icon at end
            JarPrimaryButton(
                text = "Continue",
                actionName = "jar:cta:icon_end",
                instanceKey = "demo_icon_end",
                //iconUrl = "https://cdn.example.com/icons/arrow_right.png",
                // iconTint not supported – use a pre-tinted PNG
                iconGravity = ICON_GRAVITY_END,
            )

            Spacer(modifier = Modifier.size(dp(12)))

            // RenderImagePillButton – caller controls width via modifier
            RenderImagePillButton(
                text = "Filter",
                bgColor = Colors.bgLabel,
                textColor = Colors.white,
                actionName = "jar:filter:open",
                maxLines = 1,
                // No fillMaxWidth here – pill sizes to content
            )

            Spacer(modifier = Modifier.size(dp(12)))

            // Dispatcher convenience wrapper
            JarButton(
                text = "Via Dispatcher",
                actionName = "jar:cta:dispatcher",
                instanceKey = "demo_dispatcher",
                buttonType = RcButtonType.PRIMARY,
            )
        }
    }
}
