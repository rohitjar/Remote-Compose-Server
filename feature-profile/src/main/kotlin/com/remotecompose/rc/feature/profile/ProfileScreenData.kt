package com.remotecompose.rc.feature.profile

data class ProfileScreenData(
    val title: String = "Profile",
    val userName: String = "Sheen Kumar",
    val userInitials: String = "RK",
    val phoneNumber: String = "+91-9999911111",
    val age: String = "23",
    val gender: String = "Male",
    val kycVerified: Boolean = true,
    val primaryUpiId: String = "9999999999@ybl",
    val savedAddressCount: Int = 2,
)