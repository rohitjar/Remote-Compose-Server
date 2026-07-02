package com.remotecompose.rc.core

import androidx.compose.remote.creation.dsl.RcDp
import androidx.compose.remote.creation.dsl.RcSp

/**
 * Density-aware unit helpers — the single source of truth for dp/sp across all screens.
 *
 * Values are authored in logical dp/sp and baked to device pixels using the density from
 * the current request's [RenderContext]. [rcDocument] publishes that density as an ambient
 * value for the duration of binary creation (see [withDensity]) and writes the document with
 * DENSITY_BEHAVIOR_PIXELS, so the player renders 1:1 and the bake is never applied twice.
 *
 * Outside a document build the ambient density is [DEFAULT_DENSITY], so these are still safe
 * (and deterministic) to call in tests.
 */
private val ambientDensity = ThreadLocal.withInitial { DEFAULT_DENSITY }

/** The density in effect for the document currently being authored on this thread. */
fun currentDensity(): Float = ambientDensity.get()

/** Runs [block] with [density] as the ambient density, restoring the previous value after. */
internal fun <T> withDensity(density: Float, block: () -> T): T {
    val previous = ambientDensity.get()
    ambientDensity.set(density)
    try {
        return block()
    } finally {
        ambientDensity.set(previous)
    }
}

/** Logical dp → device px (baked with the current request density). For modifier dimensions. */
fun dp(value: Number): Float = value.toFloat() * currentDensity()

val Int.rdp: RcDp get() = RcDp(this * currentDensity())
val Float.rdp: RcDp get() = RcDp(this * currentDensity())

val Int.rsp: RcSp get() = RcSp(this * currentDensity())
val Float.rsp: RcSp get() = RcSp(this * currentDensity())
