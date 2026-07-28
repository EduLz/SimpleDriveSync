package com.example.drivesync

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Setup : NavKey

@Serializable
data object Sync : NavKey
