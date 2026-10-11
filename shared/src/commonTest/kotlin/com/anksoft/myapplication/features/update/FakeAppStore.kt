package com.anksoft.myapplication.features.update

import com.anksoft.myapplication.features.update.domain.AppStore
import com.anksoft.myapplication.features.update.domain.model.UpdatePlatform

/** Pretends to open the store: [opens] is the answer, [openCalls] counts the attempts. */
class FakeAppStore(
    override val platform: UpdatePlatform = UpdatePlatform.ANDROID,
    var opens: Boolean = true,
) : AppStore {

    var openCalls = 0
        private set

    override suspend fun openAppPage(): Boolean {
        openCalls++
        return opens
    }
}
