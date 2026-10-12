package com.anksoft.myapplication.features.profile.domain.model

/**
 * The signed-in account's own data. A re-downloadable cache, never a source of truth: the backend
 * owns it, and it holds no token, password or consent decision (AC-14).
 */
data class AccountProfile(
    val userId: String,
    val email: String,
    val name: String?,
)
