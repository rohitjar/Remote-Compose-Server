package com.remotecompose.rc.feature.profile

import com.remotecompose.rc.core.RenderContext
import com.remotecompose.rc.core.Screen

/** Registers the Profile screen for ServiceLoader discovery. */
class ProfileScreenProvider : Screen {
    override val key = "profile"
    override fun render(ctx: RenderContext): ByteArray = ProfileScreen(ctx)
}
