package com.remotecompose.rc.feature.profile

import com.remotecompose.rc.core.RenderContext
import com.remotecompose.rc.core.Screen
import com.remotecompose.rc.core.ScreenData
import com.remotecompose.rc.core.ScreenRequest
import kotlinx.serialization.json.jsonPrimitive

/** Registers the Profile screen for ServiceLoader discovery. */
class ProfileScreenProvider : Screen {
    override val key = "profile"

    // v2: user data moved out of the binary into named USER: slots (served via /profile/data).
    override val layoutVersion = 2

    override fun render(ctx: RenderContext): ByteArray = ProfileScreen(ctx)

    // In production this would look up the authenticated user; the demo serves the defaults,
    // letting request args override a couple of fields to exercise the envelope end to end.
    // Args are untrusted client-relayed input — real screens must authorize them.
    override fun data(request: ScreenRequest): ScreenData {
        val defaults = ProfileScreenData()
        return defaults.copy(
            userName = request.args["userName"]?.jsonPrimitive?.content ?: defaults.userName,
            userInitials = request.args["userInitials"]?.jsonPrimitive?.content ?: defaults.userInitials,
        ).toScreenData()
    }
}
