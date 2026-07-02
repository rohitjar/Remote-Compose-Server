package com.remotecompose.rc.theme

/**
 * CDN asset URLs for shared icons. Served via remoteBitmapUrl + the consumer's Coil-backed
 * CoilBitmapLoader (requires Limits.ENABLE_IMAGE_URLS = true on the player).
 */
object Icons {
    private const val CDN = "https://cdn.myjar.app/rc/core"

    const val back         = "$CDN/ic_back.webp"
    const val chevronRight = "$CDN/ic_arrow_right.webp"

    // TODO: replace flaticon placeholders with real CDN assets.
    const val edit  = "https://cdn-icons-png.flaticon.com/512/1159/1159633.png"
    const val check = "https://cdn-icons-png.flaticon.com/512/845/845646.png"
}
