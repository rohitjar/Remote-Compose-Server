package com.remotecompose.rc.core

import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.dsl.RcScope

// The high-level RcScope DSL hides the underlying RemoteComposeWriter (library-`internal`).
// A few primitives (e.g. sized URL images) need it, so we reach it via a single reflective
// field read. Isolated here so a library change only touches one place.
private val rcScopeWriterField by lazy {
    Class.forName("androidx.compose.remote.creation.dsl.RcScopeImpl")
        .getDeclaredField("writer")
        .apply { isAccessible = true }
}

internal fun RcScope.writer(): RemoteComposeWriter =
    rcScopeWriterField.get(this) as RemoteComposeWriter
