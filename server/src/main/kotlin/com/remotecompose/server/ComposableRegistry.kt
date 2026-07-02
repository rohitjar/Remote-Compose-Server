package com.remotecompose.server

import com.remotecompose.rc.feature.profile.ProfileScreen
import com.remotecompose.rc.feature.samples.ButtonScreen
import com.remotecompose.rc.feature.samples.Dummy
import com.remotecompose.rc.feature.samples.GreetingScreen
import com.remotecompose.rc.feature.samples.JarButtonScreen
import com.remotecompose.rc.feature.samples.JarImageScreen

// Composition root: maps a --compose CLI key → the screen factory that produces its
// binary .rc document. Add feature screens here as new :feature-* modules are wired in.
val COMPOSABLE_REGISTRY: Map<String, () -> ByteArray> = mapOf(
    "greeting" to ::GreetingScreen,
    "jar_button" to ::JarButtonScreen,
    "jar_image" to ::JarImageScreen,
    "button" to ::ButtonScreen,
    "profile" to { ProfileScreen() },
    "dummy" to ::Dummy,
)
