package com.remotecompose.rc.core

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RcProfiles
import androidx.compose.remote.core.operations.Header
import androidx.compose.remote.creation.JvmRcPlatformServices
import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.dsl.RcProfile
import androidx.compose.remote.creation.dsl.RcScope
import androidx.compose.remote.creation.dsl.createRcBuffer
import androidx.compose.remote.creation.profile.Profile


// ─── Authoring density model ───────────────────────────────────────────────────
// Verified against alpha13 bytecode (see REFERENCE.md §8):
//   • DOC_WIDTH / DOC_HEIGHT (header tags 5 / 6) are written verbatim, in PIXELS.
//   • DOC_DENSITY_AT_GENERATION (tag 7) is stored INDEPENDENTLY as a float; the
//     writer never multiplies size by density (RemoteComposeWriter.header passes
//     width/height/density straight through). Convention: density = densityDpi / 160f.
//   • Layout units RcDp / RcSp are plain value-class wrappers — no density baked in.
//     `.rdp` / `.rsp` and bare floats are all authored in dp / sp and scaled to
//     pixels ONCE, on the device, because DENSITY_BEHAVIOR_DP is set below.

const val DEFAULT_DENSITY = 3f   // xxhdpi reference; pass the device's real density (densityDpi/160f) for exact
private const val BASE_WIDTH_DP  = 1344
private const val BASE_HEIGHT_DP = 2992

private fun makeProfile() = RcProfile(
    Profile(
        CoreDocument.DOCUMENT_API_LEVEL,
        RcProfiles.PROFILE_EXPERIMENTAL,
        JvmRcPlatformServices()
    ) { _, profile, _ ->
        RemoteComposeWriter(profile)
    }
)

// ─── Entry point ──────────────────────────────────────────────────────────────
// Sizes are expressed in logical dp; the pixel header tags are derived from a
// single density value so width, height and density can never drift apart.
// To target a specific device exactly, pass its real `density` (densityDpi/160f)
// and screen dp size; with DENSITY_BEHAVIOR_DP the player rescales from this
// generation density to the device's actual density, keeping content physically
// correct across screens.
fun rcDocument(
    widthDp: Int = BASE_WIDTH_DP,
    heightDp: Int = BASE_HEIGHT_DP,
    density: Float = DEFAULT_DENSITY,
    content: RcScope.() -> Unit,
): ByteArray {
    val widthPx  = (widthDp  * density).toInt()
    val heightPx = (heightDp * density).toInt()
    return createRcBuffer(
        profile = makeProfile(),
        RemoteComposeWriter.HTag(Header.DOC_WIDTH, widthPx),
        RemoteComposeWriter.HTag(Header.DOC_HEIGHT, heightPx),
        RemoteComposeWriter.HTag(Header.DOC_DENSITY_AT_GENERATION, density),
        RemoteComposeWriter.HTag(Header.DOC_DENSITY_BEHAVIOR, CoreDocument.DENSITY_BEHAVIOR_DP),
        content= content
    )
}