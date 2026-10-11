package com.anksoft.myapplication.features.consent.domain

import com.anksoft.myapplication.core.concurrency.createReentrantLock
import com.anksoft.myapplication.core.consent.ConsentManager
import com.anksoft.myapplication.core.consent.OptionalDataConsent
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.EmptyResult
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.domain.asEmptyResult
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.debug
import com.anksoft.myapplication.core.logging.info
import com.anksoft.myapplication.core.logging.warn
import com.anksoft.myapplication.core.session.SessionObserver
import com.anksoft.myapplication.core.session.SignOutReason
import com.anksoft.myapplication.features.consent.domain.model.AccountConsent
import com.anksoft.myapplication.features.consent.domain.model.ConsentChoice
import com.anksoft.myapplication.features.consent.domain.model.ConsentStatus
import com.anksoft.myapplication.features.consent.domain.model.needsPrompt
import com.anksoft.myapplication.features.consent.domain.model.toOptionalDataConsent
import com.anksoft.myapplication.features.consent.domain.model.toStatus
import com.anksoft.myapplication.features.consent.domain.repository.ConsentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Owns the account's consent state: the one [ConsentManager] other features query, plus the
 * detailed [status] the consent screens render.
 *
 * It listens to the session ([SessionObserver]), so by the time a logout or an expiry returns, the
 * facade already answers "no consent" (AC-18, AC-19). Collection is allowed only while the
 * backend's confirmed decision is "granted" (AC-25): turning it off takes effect before the
 * backend answers, turning it on only after (AC-15).
 *
 * Threading: callbacks arrive on any thread, results on [scope]'s threads. Shared state is
 * guarded by [stateLock] and never calls into [repository] while holding it, because the
 * repository takes the session lock and the session lock holder calls this class.
 *
 * Every network result carries the session generation it was started in and is dropped when the
 * session changed meanwhile, so an old account's answer never reaches the new one.
 */
class AccountConsentManager(
    private val repository: ConsentRepository,
    private val scope: CoroutineScope,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    private val logger: AppLogger
) : ConsentManager, SessionObserver {

    private val stateLock = createReentrantLock()

    private val _status = MutableStateFlow<ConsentStatus>(ConsentStatus.SignedOut)
    private val _optionalDataConsent = MutableStateFlow(OptionalDataConsent.UNKNOWN)

    /** Detailed state for the consent screens. */
    val status: StateFlow<ConsentStatus> = _status.asStateFlow()

    override val optionalDataConsent: StateFlow<OptionalDataConsent> = _optionalDataConsent.asStateFlow()

    // Everything below is guarded by stateLock.
    private var generation = 0
    private var decisionCount = 0
    private var fetchJob: Job? = null
    private var promptClaimed = false
    private var lastSuccessfulFetch: ComparableTimeMark? = null

    // Writes are sent one at a time; a second change waits for the first.
    private val writeMutex = Mutex()

    init {
        if (repository.isSignedIn()) {
            // Cache first: the last confirmed decision applies while the fetch runs (cold start).
            val cached = repository.cachedConsent()
            setStatus(cached?.toStatus() ?: ConsentStatus.Loading)
            refresh()
        }
    }

    override fun onSignedIn() {
        // The cache holds a decision only when the register response carried one.
        val cached = repository.cachedConsent()
        val needsFetch = stateLock.withLock {
            generation++
            cancelFetch()
            promptClaimed = false
            if (cached != null) {
                setStatus(cached.toStatus())
                lastSuccessfulFetch = timeSource.markNow()
                false
            } else {
                lastSuccessfulFetch = null
                setStatus(ConsentStatus.Loading)
                true
            }
        }
        if (needsFetch) refresh()
    }

    override fun onSignedOut(reason: SignOutReason) {
        stateLock.withLock {
            generation++
            cancelFetch()
            promptClaimed = false
            lastSuccessfulFetch = null
            setStatus(ConsentStatus.SignedOut)
        }
    }

    /** Reads the account's decision again. No-op without a session or while a fetch is already running. */
    fun refresh() {
        if (!repository.isSignedIn()) return
        val started = stateLock.withLock {
            if (fetchJob != null) {
                null
            } else {
                val startGeneration = generation
                val startDecisionCount = decisionCount
                // Lazy, so the job is registered before it can finish.
                val job = scope.launch(start = CoroutineStart.LAZY) {
                    fetch(startGeneration, startDecisionCount)
                }
                fetchJob = job
                job.invokeOnCompletion {
                    stateLock.withLock { if (fetchJob === job) fetchJob = null }
                }
                job
            }
        }
        started?.start()
    }

    /** The app came to the foreground: look again when nothing is known or the last look is old (AC-12, AC-17). */
    fun onAppResumed() {
        val due = stateLock.withLock {
            val current = _status.value
            val last = lastSuccessfulFetch
            current == ConsentStatus.Unavailable ||
                (current != ConsentStatus.SignedOut && (last == null || last.elapsedNow() >= REFRESH_INTERVAL))
        }
        if (due) refresh()
    }

    /**
     * Records the user's decision. Turning collection off applies at once; everything else only
     * changes after the backend confirmed it. On failure the previous confirmed state stays (a
     * switch-off stays in effect, see [ConsentStatus.Granted.suppressed]) and the error is returned.
     *
     * The write runs in the application [scope], not in the caller's coroutine: a user who leaves
     * the screen right after switching off must not lose the write, or the next refresh would read
     * the old "granted" back and collection would resume unnoticed. The caller only waits for the
     * result; if it is cancelled, the write still finishes and its result is applied (a result
     * from an earlier session is still dropped, see the generation check).
     */
    suspend fun decide(choice: ConsentChoice): EmptyResult<DataError> =
        scope.async { write(choice) }.await()

    private suspend fun write(choice: ConsentChoice): EmptyResult<DataError> = writeMutex.withLock {
        val startGeneration = stateLock.withLock {
            // Fetches that started before this decision may carry the older answer.
            decisionCount++
            val current = _status.value
            if (!choice.granted && current is ConsentStatus.Granted && !current.suppressed) {
                setStatus(current.copy(suppressed = true))
            }
            generation
        }
        val result = repository.saveDecision(choice)
        stateLock.withLock {
            // Again after the answer: a fetch that started while the write was running may still
            // carry the value from before it and must not replace the new decision.
            decisionCount++
            if (startGeneration != generation) {
                logger.debug(LogTags.CONSENT) { "consent write result dropped: session changed" }
            } else {
                when (result) {
                    is Result.Success -> {
                        setStatus(result.data.toStatus())
                        lastSuccessfulFetch = timeSource.markNow()
                        logger.info(LogTags.CONSENT) { "consent saved" }
                    }

                    is Result.Failure -> logger.warn(LogTags.CONSENT) { "consent save failed error=${result.error}" }
                }
            }
        }
        result.asEmptyResult()
    }

    /**
     * True once per session when the one-time prompt is due (AC-7, AC-10). A user who leaves the
     * prompt without deciding is asked again at the next sign-in or app start, not sooner.
     */
    fun claimPrompt(): Boolean = stateLock.withLock {
        if (_status.value.needsPrompt && !promptClaimed) {
            promptClaimed = true
            true
        } else {
            false
        }
    }

    private suspend fun fetch(startGeneration: Int, startDecisionCount: Int) {
        val result = repository.fetchAccountConsent()
        stateLock.withLock {
            if (startGeneration != generation || startDecisionCount != decisionCount) {
                logger.debug(LogTags.CONSENT) { "consent fetch result dropped: state changed" }
            } else {
                applyFetch(result)
            }
        }
    }

    private fun applyFetch(result: Result<AccountConsent, DataError.Remote>) {
        when (result) {
            is Result.Success -> {
                setStatus(result.data.toStatus())
                lastSuccessfulFetch = timeSource.markNow()
                logger.debug(LogTags.CONSENT) { "consent fetch succeeded" }
            }

            is Result.Failure -> {
                // A known decision is never replaced by a failure; only "nothing known yet" becomes unavailable.
                if (_status.value == ConsentStatus.Loading) setStatus(ConsentStatus.Unavailable)
                logger.warn(LogTags.CONSENT) { "consent fetch failed error=${result.error}" }
            }
        }
    }

    private fun cancelFetch() {
        fetchJob?.cancel()
        fetchJob = null
    }

    /** Both flows change in this one place, under [stateLock], so they never disagree. */
    private fun setStatus(newStatus: ConsentStatus) {
        stateLock.withLock {
            _status.value = newStatus
            _optionalDataConsent.value = newStatus.toOptionalDataConsent()
        }
    }

    private companion object {
        val REFRESH_INTERVAL = 60.seconds
    }
}
