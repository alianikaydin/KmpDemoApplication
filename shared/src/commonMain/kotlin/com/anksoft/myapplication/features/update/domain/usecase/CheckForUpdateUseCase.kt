package com.anksoft.myapplication.features.update.domain.usecase

import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.error
import com.anksoft.myapplication.features.update.domain.DismissedUpdateStore
import com.anksoft.myapplication.features.update.domain.decideUpdate
import com.anksoft.myapplication.features.update.domain.model.AppVersion
import com.anksoft.myapplication.features.update.domain.model.UpdatePlatform
import com.anksoft.myapplication.features.update.domain.model.UpdateRequirement
import com.anksoft.myapplication.features.update.domain.repository.VersionPolicyRepository

/**
 * Decides whether the installed app has to or may update. It needs neither a session nor consent:
 * this is a functional check, not optional data collection (AC-6, AC-22).
 */
class CheckForUpdateUseCase(
    private val repository: VersionPolicyRepository,
    private val dismissedUpdates: DismissedUpdateStore,
    private val installedVersionName: String,
    private val logger: AppLogger,
) {

    /** Never fails: every problem is logged and answered with None (fail-open, AC-14, AC-15). */
    suspend operator fun invoke(platform: UpdatePlatform): UpdateRequirement {
        val installed = AppVersion.parse(installedVersionName)
        if (installed == null) {
            // Caught at build time by scripts/ci/check-version-format.sh; never block the user for it.
            logger.error(LogTags.UPDATE) { "Installed version is not numeric; update check skipped" }
            return UpdateRequirement.None
        }
        return when (val result = repository.getVersionPolicy(platform)) {
            // The failure was already logged where it happened.
            is Result.Failure -> UpdateRequirement.None
            is Result.Success -> decideUpdate(installed, result.data, dismissedUpdates.dismissedLatestVersion())
        }
    }
}
