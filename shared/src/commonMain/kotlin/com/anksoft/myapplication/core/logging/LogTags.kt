package com.anksoft.myapplication.core.logging

/** Fixed log tags. Tags are never derived from class names (minified JS would mangle them). */
object LogTags {
    const val APP = "App"
    const val NETWORK = "Network"
    const val HTTP = "Http"
    const val AUTH = "Auth"
}
