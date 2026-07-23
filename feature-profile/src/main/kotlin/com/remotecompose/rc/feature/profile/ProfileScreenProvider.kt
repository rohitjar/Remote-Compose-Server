package com.remotecompose.rc.feature.profile

import com.remotecompose.rc.core.EndpointRef
import com.remotecompose.rc.core.RenderContext
import com.remotecompose.rc.core.Screen

/** Registers the Profile screen for ServiceLoader discovery. */
class ProfileScreenProvider : Screen {
    override val key = "profile"

    // v3: slots renamed to the flattened paths of the real Jar product APIs (see
    // dataEndpoints); the demo /profile/data payload is gone.
    override val layoutVersion = 3

    /**
     * The real product APIs that feed this screen's `USER:` slots. The consumer calls both in
     * parallel and flattens each response by path; both wrap their payload in the standard
     * `{success, data:{…}}` envelope, so every slot binds under the `data.` prefix (the two
     * field sets are disjoint, so sharing the prefix is collision-free). Slot names in
     * [ProfileScreen] must stay in sync with these responses' flattened paths.
     */
    override val dataEndpoints = listOf(
        EndpointRef("https://prod.myjar.app/v1/api/user/details"),
        EndpointRef("https://prod.myjar.app/v1/api/kyc/status?kycContext=PROFILE"),
    )

    override fun render(ctx: RenderContext): ByteArray = ProfileScreen(ctx)
}
