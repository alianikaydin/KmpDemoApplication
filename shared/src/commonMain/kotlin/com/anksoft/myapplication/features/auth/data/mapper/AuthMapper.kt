package com.anksoft.myapplication.features.auth.data.mapper

import com.anksoft.myapplication.features.auth.data.dto.UserDto
import com.anksoft.myapplication.features.auth.domain.model.User

fun UserDto.toUser(): User = User(
    id = id,
    email = email,
    name = name
)
