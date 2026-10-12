package com.anksoft.myapplication.features.update.presentation

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogSeverity
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.core.logging.recordingLogger
import com.anksoft.myapplication.core.presentation.UiText
import com.anksoft.myapplication.features.update.FakeAppStore
import com.anksoft.myapplication.features.update.FakeVersionPolicyRepository
import com.anksoft.myapplication.features.update.data.SettingsDismissedUpdateStore
import com.anksoft.myapplication.features.update.domain.AppStore
import com.anksoft.myapplication.features.update.domain.model.AppVersion
import com.anksoft.myapplication.features.update.domain.model.UpdatePlatform
import com.anksoft.myapplication.features.update.domain.model.VersionPolicy
import com.anksoft.myapplication.features.update.domain.usecase.CheckForUpdateUseCase
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.update_store_unavailable
import kotlin.test.AfterTest
import kotlin.test.Test

class AppUpdateControllerTest {

    private val settings = MapSettings()
    private val dismissed = SettingsDismissedUpdateStore(settings, NoOpLogger)
    private val repository = FakeVersionPolicyRepository()
    private val store = FakeAppStore()
    private val scope = CoroutineScope(UnconfinedTestDispatcher())
    private val storeUnavailable = UiText.Resource(Res.string.update_store_unavailable)

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun v(raw: String): AppVersion = requireNotNull(AppVersion.parse(raw))

    private fun answer(min: String, latest: String) {
        repository.response = Result.Success(VersionPolicy(v(min), v(latest)))
    }

    private fun controller(
        appStore: AppStore? = store,
        installed: String = "1.0",
        dismissedStore: SettingsDismissedUpdateStore = dismissed,
        logger: AppLogger = NoOpLogger,
    ) = AppUpdateController(
        checkForUpdate = CheckForUpdateUseCase(repository, dismissedStore, installed, logger),
        dismissedUpdates = dismissedStore,
        appStore = appStore,
        scope = scope,
        logger = logger,
    )

    // AC-13
    @Test
    fun startingTwiceAsksTheRepositoryOnce() = runTest {
        answer(min = "0.0.0", latest = "0.0.0")
        val controller = controller()

        controller.start()
        controller.start()

        assertThat(repository.callCount).isEqualTo(1)
    }

    // AC-13
    @Test
    fun stateStaysNoneWhileTheAnswerIsPending() = runTest {
        repository.gate = CompletableDeferred()
        answer(min = "9.0.0", latest = "9.0.0")
        val controller = controller()

        controller.start()

        assertThat(controller.state.value).isEqualTo(AppUpdateState.None)
        repository.gate?.complete(Unit)
        assertThat(controller.state.value).isEqualTo(AppUpdateState.Required())
    }

    // AC-17
    @Test
    fun withoutAStoreNothingRunsAndNothingIsShown() = runTest {
        answer(min = "9.0.0", latest = "9.0.0")
        val controller = controller(appStore = null)

        controller.start()

        assertThat(repository.callCount).isEqualTo(0)
        assertThat(controller.state.value).isEqualTo(AppUpdateState.None)
    }

    // AC-18
    @Test
    fun theStoresPlatformIsRequested() = runTest {
        answer(min = "0.0.0", latest = "0.0.0")

        controller(appStore = FakeAppStore(platform = UpdatePlatform.IOS)).start()

        assertThat(repository.requestedPlatforms).isEqualTo(listOf(UpdatePlatform.IOS))
    }

    // AC-1
    @Test
    fun unsupportedVersionShowsTheRequiredState() = runTest {
        answer(min = "1.1.0", latest = "1.2.0")
        val controller = controller()

        controller.start()

        assertThat(controller.state.value).isEqualTo(AppUpdateState.Required())
    }

    // AC-7
    @Test
    fun olderThanLatestShowsTheOptionalState() = runTest {
        answer(min = "1.0.0", latest = "1.2.0")
        val controller = controller()

        controller.start()

        assertThat(controller.state.value).isEqualTo(AppUpdateState.Optional(v("1.2.0")))
    }

    // AC-14
    @Test
    fun failedCheckShowsNothing() = runTest {
        repository.response = Result.Failure(DataError.Remote.SERVER_ERROR)
        val controller = controller()

        controller.start()

        assertThat(controller.state.value).isEqualTo(AppUpdateState.None)
    }

    // AC-8
    @Test
    fun dismissingThePromptClosesItAndRemembersTheLatestVersion() = runTest {
        answer(min = "1.0.0", latest = "1.2.0")
        val controller = controller()
        controller.start()

        controller.onEvent(AppUpdateEvent.DismissPrompt)

        assertThat(controller.state.value).isEqualTo(AppUpdateState.None)
        assertThat(dismissed.dismissedLatestVersion()).isEqualTo(v("1.2.0"))
    }

    // AC-8
    @Test
    fun afterDismissingTheNextLaunchWithTheSamePolicyShowsNothing() = runTest {
        answer(min = "1.0.0", latest = "1.2.0")
        controller().apply {
            start()
            onEvent(AppUpdateEvent.DismissPrompt)
        }

        // A new process: new controller, same persisted settings.
        val relaunched = controller(dismissedStore = SettingsDismissedUpdateStore(settings, NoOpLogger))
        relaunched.start()

        assertThat(relaunched.state.value).isEqualTo(AppUpdateState.None)
    }

    // AC-9
    @Test
    fun aNewerLatestAfterDismissingAsksAgain() = runTest {
        answer(min = "1.0.0", latest = "1.2.0")
        controller().apply {
            start()
            onEvent(AppUpdateEvent.DismissPrompt)
        }
        answer(min = "1.0.0", latest = "1.3.0")

        val relaunched = controller(dismissedStore = SettingsDismissedUpdateStore(settings, NoOpLogger))
        relaunched.start()

        assertThat(relaunched.state.value).isEqualTo(AppUpdateState.Optional(v("1.3.0")))
    }

    // AC-2
    @Test
    fun dismissIsIgnoredOnTheRequiredScreen() = runTest {
        answer(min = "1.1.0", latest = "1.2.0")
        val controller = controller()
        controller.start()

        controller.onEvent(AppUpdateEvent.DismissPrompt)

        assertThat(controller.state.value).isEqualTo(AppUpdateState.Required())
        assertThat(dismissed.dismissedLatestVersion()).isNull()
    }

    // AC-3
    @Test
    fun openStoreAsksTheStoreToOpen() = runTest {
        answer(min = "1.1.0", latest = "1.2.0")
        val controller = controller()
        controller.start()

        controller.onEvent(AppUpdateEvent.OpenStore)

        assertThat(store.openCalls).isEqualTo(1)
    }

    // AC-10
    @Test
    fun openingTheStoreFromThePromptClosesItWithoutRememberingLater() = runTest {
        answer(min = "1.0.0", latest = "1.2.0")
        val controller = controller()
        controller.start()

        controller.onEvent(AppUpdateEvent.OpenStore)

        assertThat(controller.state.value).isEqualTo(AppUpdateState.None)
        assertThat(dismissed.dismissedLatestVersion()).isNull()
    }

    // AC-4, AC-10
    @Test
    fun storeThatCannotOpenKeepsThePromptOpenAndShowsTheError() = runTest {
        answer(min = "1.0.0", latest = "1.2.0")
        store.opens = false
        val (logger, writer) = recordingLogger()
        val controller = controller(logger = logger)
        controller.start()

        controller.onEvent(AppUpdateEvent.OpenStore)

        assertThat(controller.state.value)
            .isEqualTo(AppUpdateState.Optional(v("1.2.0"), isOpeningStore = false, storeError = storeUnavailable))
        assertThat(dismissed.dismissedLatestVersion()).isNull()
        assertThat(writer.entries.map { it.severity to it.tag }).contains(LogSeverity.WARN to LogTags.UPDATE)
    }

    // AC-4
    @Test
    fun storeThatCannotOpenKeepsTheRequiredScreenAndShowsTheError() = runTest {
        answer(min = "1.1.0", latest = "1.2.0")
        store.opens = false
        val controller = controller()
        controller.start()

        controller.onEvent(AppUpdateEvent.OpenStore)

        assertThat(controller.state.value)
            .isEqualTo(AppUpdateState.Required(isOpeningStore = false, storeError = storeUnavailable))
    }

    // AC-1, AC-3
    @Test
    fun requiredScreenStaysAfterTheStoreOpened() = runTest {
        answer(min = "1.1.0", latest = "1.2.0")
        val controller = controller()
        controller.start()

        controller.onEvent(AppUpdateEvent.OpenStore)

        assertThat(controller.state.value).isEqualTo(AppUpdateState.Required())
    }

    // AC-4
    @Test
    fun aRetryAfterAFailedOpenClearsTheErrorWhenTheStoreOpens() = runTest {
        answer(min = "1.1.0", latest = "1.2.0")
        store.opens = false
        val controller = controller()
        controller.start()
        controller.onEvent(AppUpdateEvent.OpenStore)

        store.opens = true
        controller.onEvent(AppUpdateEvent.OpenStore)

        assertThat(controller.state.value).isEqualTo(AppUpdateState.Required())
        assertThat(store.openCalls).isEqualTo(2)
    }

    // AC-4
    @Test
    fun secondTapWhileTheStoreIsOpeningDoesNotOpenItTwice() = runTest {
        answer(min = "1.1.0", latest = "1.2.0")
        store.gate = CompletableDeferred()
        val controller = controller()
        controller.start()

        controller.onEvent(AppUpdateEvent.OpenStore)
        assertThat((controller.state.value as AppUpdateState.Required).isOpeningStore).isTrue()
        controller.onEvent(AppUpdateEvent.OpenStore)
        store.gate?.complete(Unit)

        assertThat(store.openCalls).isEqualTo(1)
        assertThat((controller.state.value as AppUpdateState.Required).isOpeningStore).isFalse()
    }

    // AC-1
    @Test
    fun openStoreWithNothingToShowDoesNothing() = runTest {
        answer(min = "0.0.0", latest = "0.0.0")
        val controller = controller()
        controller.start()

        controller.onEvent(AppUpdateEvent.OpenStore)

        assertThat(store.openCalls).isEqualTo(0)
        assertThat(controller.state.value).isEqualTo(AppUpdateState.None)
    }
}
