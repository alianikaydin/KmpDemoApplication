package com.anksoft.myapplication

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform