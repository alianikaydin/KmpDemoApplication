package com.anksoft.myapplication.features.update

import com.anksoft.myapplication.features.update.domain.AppStore
import com.anksoft.myapplication.features.update.domain.model.UpdatePlatform
import kotlinx.coroutines.CompletableDeferred

/**
 * Pretends to open the store: [opens] is the answer (given after [gate] opens, when set),
 * [openCalls] counts the attempts.
 */
class FakeAppStore(
    override val platform: UpdatePlatform = UpdatePlatform.ANDROID,
    var opens: Boolean = true,
    var gate: CompletableDeferred<Unit>? = null,
) : AppStore {

    var openCalls = 0
        private set

    override suspend fun openAppPage(): Boolean {
        openCalls++
        gate?.await()
        return opens
    }
}
