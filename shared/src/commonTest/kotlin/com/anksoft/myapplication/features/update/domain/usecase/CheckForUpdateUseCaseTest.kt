package com.anksoft.myapplication.features.update.domain.usecase

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isEmpty
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogSeverity
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.core.logging.recordingLogger
import com.anksoft.myapplication.features.update.FakeVersionPolicyRepository
import com.anksoft.myapplication.features.update.data.SettingsDismissedUpdateStore
import com.anksoft.myapplication.features.update.domain.model.AppVersion
import com.anksoft.myapplication.features.update.domain.model.UpdatePlatform
import com.anksoft.myapplication.features.update.domain.model.UpdateRequirement
import com.anksoft.myapplication.features.update.domain.model.VersionPolicy
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class CheckForUpdateUseCaseTest {

    private val repository = FakeVersionPolicyRepository()
    private val dismissed = SettingsDismissedUpdateStore(MapSettings(), NoOpLogger)

    private fun v(raw: String): AppVersion = requireNotNull(AppVersion.parse(raw))

    private fun policy(min: String, latest: String) = Result.Success(VersionPolicy(v(min), v(latest)))

    private fun useCase(installed: String = "1.0", logger: AppLogger = NoOpLogger) =
        CheckForUpdateUseCase(repository, dismissed, installed, logger)

    // AC-14
    @Test
    fun everyRemoteErrorFailsOpenToNone() = runTest {
        DataError.Remote.entries.forEach { error ->
            repository.response = Result.Failure(error)

            assertThat(useCase()(UpdatePlatform.ANDROID), name = "result for $error")
                .isEqualTo(UpdateRequirement.None)
        }
    }

    // AC-1
    @Test
    fun installedVersionWithTwoComponentsIsRequiredWhenBelowTheMinimum() = runTest {
        repository.response = policy(min = "1.1.0", latest = "1.2.0")

        assertThat(useCase("1.0")(UpdatePlatform.ANDROID)).isEqualTo(UpdateRequirement.Required)
    }

    // AC-5
    @Test
    fun installedVersionEqualToTheMinimumIsNotBlockedEvenWithoutThePatchComponent() = runTest {
        repository.response = policy(min = "1.0.0", latest = "1.0.0")

        assertThat(useCase("1.0")(UpdatePlatform.ANDROID)).isEqualTo(UpdateRequirement.None)
    }

    // AC-7
    @Test
    fun installedVersionBetweenMinimumAndLatestGetsTheOptionalUpdate() = runTest {
        repository.response = policy(min = "1.0.0", latest = "1.2.0")

        assertThat(useCase("1.0")(UpdatePlatform.IOS)).isEqualTo(UpdateRequirement.Optional(v("1.2.0")))
    }

    // AC-8, AC-9
    @Test
    fun dismissedVersionIsReadFromTheStoreOnEveryCheck() = runTest {
        repository.response = policy(min = "1.0.0", latest = "1.4.0")
        dismissed.setDismissedLatestVersion(v("1.4.0"))

        assertThat(useCase("1.0")(UpdatePlatform.ANDROID)).isEqualTo(UpdateRequirement.None)

        repository.response = policy(min = "1.0.0", latest = "1.5.0")

        assertThat(useCase("1.0")(UpdatePlatform.ANDROID)).isEqualTo(UpdateRequirement.Optional(v("1.5.0")))
    }

    // AC-12
    @Test
    fun requiredWinsOverAnOptionalUpdateOfTheSamePolicy() = runTest {
        repository.response = policy(min = "1.1.0", latest = "1.2.0")

        assertThat(useCase("1.0")(UpdatePlatform.ANDROID)).isEqualTo(UpdateRequirement.Required)
    }

    // AC-15
    @Test
    fun nonNumericInstalledVersionSkipsTheRequestAndLogsAnError() = runTest {
        repository.response = policy(min = "9.0.0", latest = "9.0.0")
        val (logger, writer) = recordingLogger()

        val result = useCase("1.0-beta", logger)(UpdatePlatform.ANDROID)

        assertThat(result).isEqualTo(UpdateRequirement.None)
        assertThat(repository.requestedPlatforms).isEmpty()
        assertThat(writer.entries.map { it.severity to it.tag })
            .containsExactly(LogSeverity.ERROR to LogTags.UPDATE)
    }

    // AC-18
    @Test
    fun theRequestedPlatformIsPassedThrough() = runTest {
        repository.response = policy(min = "0.0.0", latest = "0.0.0")

        useCase()(UpdatePlatform.IOS)

        assertThat(repository.requestedPlatforms).containsExactly(UpdatePlatform.IOS)
    }
}
