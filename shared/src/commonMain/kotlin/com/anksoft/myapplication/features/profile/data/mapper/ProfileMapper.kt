package com.anksoft.myapplication.features.profile.data.mapper

import com.anksoft.kmpdemo.contract.auth.UserDto
import com.anksoft.myapplication.features.profile.domain.model.AccountProfile

/** A blank name is no name: the profile then shows the e-mail alone (AC-1). */
fun UserDto.toAccountProfile(): AccountProfile = AccountProfile(
    userId = id,
    email = email,
    name = name?.takeIf { it.isNotBlank() },
)
