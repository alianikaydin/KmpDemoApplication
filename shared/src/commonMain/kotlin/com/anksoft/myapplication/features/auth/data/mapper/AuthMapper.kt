package com.anksoft.myapplication.features.auth.data.mapper

import com.anksoft.kmpdemo.contract.auth.UserDto
import com.anksoft.myapplication.features.auth.domain.model.User
import com.anksoft.myapplication.features.profile.domain.model.AccountProfile

fun UserDto.toUser(): User = User(
    id = id,
    email = email,
    name = name
)

/** A blank name is no name: the profile then shows the e-mail alone (AC-1). */
fun User.toAccountProfile(): AccountProfile = AccountProfile(
    userId = id,
    email = email,
    name = name?.takeIf { it.isNotBlank() }
)
