package com.anksoft.myapplication.features.update.data.mapper

import com.anksoft.kmpdemo.contract.app.AppPlatforms
import com.anksoft.kmpdemo.contract.app.VersionPolicyDto
import com.anksoft.myapplication.features.update.domain.model.AppVersion
import com.anksoft.myapplication.features.update.domain.model.UpdatePlatform
import com.anksoft.myapplication.features.update.domain.model.VersionPolicy

/** Null when either value is not a version this app can read (AC-15). */
fun VersionPolicyDto.toVersionPolicyOrNull(): VersionPolicy? {
    val minSupported = AppVersion.parse(minSupportedVersion) ?: return null
    val latest = AppVersion.parse(latestVersion) ?: return null
    return VersionPolicy(minSupported = minSupported, latest = latest)
}

/** The value of the `platform` query parameter. */
fun UpdatePlatform.toContractValue(): String = when (this) {
    UpdatePlatform.ANDROID -> AppPlatforms.ANDROID
    UpdatePlatform.IOS -> AppPlatforms.IOS
}
