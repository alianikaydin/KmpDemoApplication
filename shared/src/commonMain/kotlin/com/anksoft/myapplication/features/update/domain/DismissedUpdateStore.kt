package com.anksoft.myapplication.features.update.domain

import com.anksoft.myapplication.features.update.domain.model.AppVersion

/**
 * Remembers the latest version the user answered "later" to (AC-8). Reading and writing never
 * throw: a broken store only means the prompt may show again.
 */
interface DismissedUpdateStore {
    fun dismissedLatestVersion(): AppVersion?

    fun setDismissedLatestVersion(version: AppVersion)
}
