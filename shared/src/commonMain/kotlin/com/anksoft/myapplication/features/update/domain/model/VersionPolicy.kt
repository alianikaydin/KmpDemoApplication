package com.anksoft.myapplication.features.update.domain.model

/**
 * What the backend says about versions of this platform: anything below [minSupported] must
 * update, anything below [latest] may.
 */
data class VersionPolicy(val minSupported: AppVersion, val latest: AppVersion)
