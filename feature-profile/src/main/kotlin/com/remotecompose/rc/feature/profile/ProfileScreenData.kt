package com.remotecompose.rc.feature.profile

import com.remotecompose.rc.core.ScreenData

data class ProfileScreenData(
    val title: String = "Profile",
    val userName: String = "Shashank Chudakad",
    val userInitials: String = "SC",
    val phoneNumber: String = "+91-9999911111",
    val age: String = "23",
    val gender: String = "Transgender",
    val kycVerified: Boolean = true,
    val primaryUpiId: String = "chamar@ybl",
    val savedAddressCount: Int = 2,
)

/**
 * Maps the domain model onto the wire payload for the named `USER:` slots declared at the
 * root of [ProfileScreen]. Keys here and slot names there must stay in sync.
 */
fun ProfileScreenData.toScreenData() = ScreenData(
    strings = mapOf(
        "profile.name" to userName,
        "profile.initials" to userInitials,
        "profile.phone" to phoneNumber,
        "profile.age" to age,
        "profile.gender" to gender,
        "profile.upi" to primaryUpiId,
        "profile.addressCount" to savedAddressCount.toString(),
    ),
    ints = mapOf(
        "profile.kyc.verified" to if (kycVerified) 1 else 0,
        "profile.kyc.pending" to if (kycVerified) 0 else 1,
    ),
)
