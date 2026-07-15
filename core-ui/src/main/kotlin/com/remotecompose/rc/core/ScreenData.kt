package com.remotecompose.rc.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Per-user data payload for a screen's named `USER:` slots, grouped by slot type so the
 * consumer can apply it generically — it iterates each map and calls the matching
 * `StateUpdater.setUserLocal*` without knowing any field names. Keys are the bare slot
 * names (no `USER:` prefix — both sides add it via their respective APIs).
 *
 * This is the contract that keeps the binary user-agnostic: one cached skeleton per
 * [Screen.layoutVersion], with this JSON carrying everything user-specific.
 */
@Serializable
data class ScreenData(
    val strings: Map<String, String> = emptyMap(),
    /** Ints double as visibility toggles: 1 = VISIBLE, 0 = GONE, 2 = INVISIBLE. */
    val ints: Map<String, Int> = emptyMap(),
    val floats: Map<String, Float> = emptyMap(),
    /** ARGB hex, e.g. "#FF6B00" or "#CCFF6B00" — parsed with android.graphics.Color. */
    val colors: Map<String, String> = emptyMap(),
    /** URLs the consumer loads itself, then pushes via setUserLocalBitmap. */
    val bitmapUrls: Map<String, String> = emptyMap(),
) {
    fun toJson(): String = Json.encodeToString(serializer(), this)
}
