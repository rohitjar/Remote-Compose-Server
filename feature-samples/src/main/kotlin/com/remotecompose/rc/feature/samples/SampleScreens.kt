package com.remotecompose.rc.feature.samples

import com.remotecompose.rc.core.RenderContext
import com.remotecompose.rc.core.Screen

// ServiceLoader providers for the demo / showcase screens. Each is listed in
// META-INF/services/com.remotecompose.rc.core.Screen.

class GreetingScreenProvider : Screen {
    override val key = "greeting"
    override fun render(ctx: RenderContext): ByteArray = GreetingScreen(ctx)
}

class JarButtonScreenProvider : Screen {
    override val key = "jar_button"
    override fun render(ctx: RenderContext): ByteArray = JarButtonScreen(ctx)
}

class JarImageScreenProvider : Screen {
    override val key = "jar_image"
    override fun render(ctx: RenderContext): ByteArray = JarImageScreen(ctx)
}

class ImageListScreenProvider : Screen {
    override val key = "image_list"
    override fun render(ctx: RenderContext): ByteArray = ImageListScreen(ctx)
}

class ButtonScreenProvider : Screen {
    override val key = "button"
    override fun render(ctx: RenderContext): ByteArray = ButtonScreen(ctx)
}

class DummyScreenProvider : Screen {
    override val key = "dummy"
    override fun render(ctx: RenderContext): ByteArray = Dummy(ctx)
}
