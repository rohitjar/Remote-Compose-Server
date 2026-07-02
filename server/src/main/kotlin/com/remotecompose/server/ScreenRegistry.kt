package com.remotecompose.server

import com.remotecompose.rc.core.Screen
import java.util.ServiceLoader

/**
 * Discovers all [Screen]s contributed by feature modules on the classpath, via ServiceLoader.
 *
 * There is no hand-maintained list here: each :feature-* module registers its own screens in
 * `META-INF/services/com.remotecompose.rc.core.Screen`. Dropping a new feature module onto the
 * runtime classpath exposes its screens automatically — the server never changes.
 */
object ScreenRegistry {
    val screens: Map<String, Screen> by lazy {
        ServiceLoader.load(Screen::class.java, ScreenRegistry::class.java.classLoader)
            .sortedBy { it.key }
            .associateBy(Screen::key)
            .also { require(it.isNotEmpty()) { "No screens found on the classpath (check :feature-* runtime deps)" } }
    }
}
