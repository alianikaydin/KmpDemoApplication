package com.anksoft.myapplication.features.update.domain

import com.anksoft.myapplication.features.update.domain.model.UpdatePlatform

/** This app's page in the platform store. Not bound on the web, where no update check runs (AC-17). */
interface AppStore {
    val platform: UpdatePlatform

    /** Opens this app's store page; false when it could not be opened (AC-4). Never throws. */
    suspend fun openAppPage(): Boolean
}
