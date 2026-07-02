package com.remotecompose.rc.feature.profile

data class ProfileScreenData(
    val title: String = "Profile",
    val userName: String = "Monal Ambastha",
    val userInitials: String = "AS",
    val phoneNumber: String = "+91-9869409330",
    val age: String = "28",
    val gender: String = "Male",
    val kycVerified: Boolean = true,
    val primaryUpiId: String = "9999999999@ybl",
    val savedAddressCount: Int = 2,
)