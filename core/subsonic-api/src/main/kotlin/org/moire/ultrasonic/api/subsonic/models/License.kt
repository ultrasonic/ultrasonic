package org.moire.ultrasonic.api.subsonic.models

import java.util.Calendar

data class License(
    val valid: Boolean = false,
    val email: String = "",
    val trialExpires: Calendar = Calendar.getInstance(),
    val licenseExpires: Calendar = Calendar.getInstance()
)
