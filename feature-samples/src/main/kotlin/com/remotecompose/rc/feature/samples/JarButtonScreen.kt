package com.remotecompose.rc.feature.samples

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.rsp
import androidx.compose.remote.creation.dsl.size
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
import com.remotecompose.rc.core.rcDocument

private const val COLOR_WHITE = 0xFFFFFFFF.toInt()

// ─── Demo screen ─────────────────────────────────────────────────────────────

fun JarButtonScreen(): ByteArray = rcDocument {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(0xFF1A1A2E.toInt()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24f, 24f, 24f, 24f),
        ) {
            Text(
                text = "JarButton variants — RC",
                color = COLOR_WHITE,
                fontSize = 22.rsp,
                fontWeight = 700f,
            )

            Spacer(modifier = Modifier.size(16f))

            // PRIMARY – instanceKey must be unique per button on this screen
            JarPrimaryButton(
                text = "Get Started",
                actionName = "jar:cta:primary",
                instanceKey = "demo_primary",
            )

            Spacer(modifier = Modifier.size(12f))

            // SECONDARY
            JarSecondaryButton(
                text = "Learn More",
                actionName = "jar:cta:secondary",
                instanceKey = "demo_secondary",
            )

            Spacer(modifier = Modifier.size(12f))

            // SPECIAL_BLACK
            JarSpecialBlackButton(
                text = "Special Black",
                actionName = "jar:cta:special_black",
                instanceKey = "demo_special_black",
            )

            Spacer(modifier = Modifier.size(12f))

            // SPECIAL_WHITE
            JarSpecialWhiteButton(
                text = "Special White",
                actionName = "jar:cta:special_white",
                instanceKey = "demo_special_white",
            )

            Spacer(modifier = Modifier.size(12f))

            // TERTIARY
            JarTertiaryButton(
                text = "Tertiary (link)",
                actionName = "jar:cta:tertiary",
                instanceKey = "demo_tertiary",
            )

            Spacer(modifier = Modifier.size(12f))

            // PILL
            JarPillButton(
                text = "Buy",
                actionName = "jar:cta:pill",
                instanceKey = "demo_pill",
            )

            Spacer(modifier = Modifier.size(12f))

            // DISABLED primary – stays visible at 30% alpha
            JarPrimaryButton(
                text = "Disabled",
                actionName = "jar:cta:disabled",
                instanceKey = "demo_disabled",
                isEnabled = false,
                // Optional: fire a toast/snackbar HostAction even when disabled
                disabledActionName = "jar:cta:disabled_tap",
            )

            Spacer(modifier = Modifier.size(12f))

            // Primary with icon at end
            JarPrimaryButton(
                text = "Continue",
                actionName = "jar:cta:icon_end",
                instanceKey = "demo_icon_end",
                //iconUrl = "https://cdn.example.com/icons/arrow_right.png",
                // iconTint not supported – use a pre-tinted PNG
                iconGravity = ICON_GRAVITY_END,
            )

            Spacer(modifier = Modifier.size(12f))

            // RenderImagePillButton – caller controls width via modifier
            RenderImagePillButton(
                text = "Filter",
                bgColor = 0xFF2D2D3A.toInt(),
                textColor = COLOR_WHITE,
                actionName = "jar:filter:open",
                maxLines = 1,
                // No fillMaxWidth here – pill sizes to content
            )

            Spacer(modifier = Modifier.size(12f))

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
