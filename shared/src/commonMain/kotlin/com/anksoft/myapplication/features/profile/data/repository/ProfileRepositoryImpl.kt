package com.anksoft.myapplication.features.profile.data.repository

import com.anksoft.myapplication.core.concurrency.createReentrantLock
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.debug
import com.anksoft.myapplication.core.logging.warn
import com.anksoft.myapplication.core.session.SessionObserver
import com.anksoft.myapplication.core.session.SignOutReason
import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.features.profile.data.datasource.ProfileLocalDataSource
import com.anksoft.myapplication.features.profile.data.datasource.ProfileRemoteDataSource
import com.anksoft.myapplication.features.profile.data.mapper.toAccountProfile
import com.anksoft.myapplication.features.profile.domain.model.AccountProfile
import com.anksoft.myapplication.features.profile.domain.model.ProfileState
import com.anksoft.myapplication.features.profile.domain.profileStateOf
import com.anksoft.myapplication.features.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the profile of the signed-in account: the cache on disk, the copy in memory the screen
 * renders, and the refresh from `GET auth/me`. It is also the [SessionObserver], so by the time a
 * logout or an expiry returns, [state] already is [ProfileState.SignedOut] (AC-10, AC-11).
 *
 * Local first: the cached profile is shown at once and the network only refreshes it. A failed
 * refresh never hides a cached profile, it marks it stale (AC-5, AC-6). Where the database is
 * missing or broken the memory copy alone works (AC-17, AC-23).
 *
 * Locks. [stateLock] guards the fields below and is never held across a suspension, a database
 * call or a call into [SessionManager]. [dbMutex] orders every call into [local]; no network call
 * runs under it. The allowed orders are session lock -> [stateLock], [dbMutex] -> session lock and
 * [dbMutex] -> [stateLock], which cannot form a cycle.
 *
 * Every result carries the session generation it was started in and is dropped when the session
 * changed meanwhile, so a late answer of an ended session never reaches disk or screen (AC-13).
 */
class ProfileRepositoryImpl(
    private val remote: ProfileRemoteDataSource,
    private val local: ProfileLocalDataSource,
    private val sessionManager: SessionManager,
    private val scope: CoroutineScope,
    private val logger: AppLogger,
) : ProfileRepository, SessionObserver {

    private val stateLock = createReentrantLock()
    private val dbMutex = Mutex()

    private val _state = MutableStateFlow<ProfileState>(ProfileState.SignedOut)
    override val state: StateFlow<ProfileState> = _state.asStateFlow()

    // Everything below is guarded by stateLock.
    private var generation = 0
    private var signedIn = false
    private var cached: AccountProfile? = null
    private var cacheLoadedForGeneration: Int? = null
    private var refreshJob: Job? = null
    private var lastRefreshFailed = false

    init {
        val userId = currentUserId()
        val startGeneration = stateLock.withLock {
            signedIn = userId != null
            publish()
            generation
        }
        // Drops rows of sessions that ended while the app was not running (a deletion that failed
        // earlier is completed here) and puts the current account's row into memory.
        scope.launch {
            dbMutex.withLock {
                local.retainOnly(currentUserId())
                if (userId != null) loadCache(startGeneration, userId)
            }
        }
    }

    override fun onSignedIn() {
        stateLock.withLock {
            generation++
            cancelRefresh()
            signedIn = true
            cached = null
            lastRefreshFailed = false
            // A new session has nothing to read from disk; its profile comes with saveSignedInProfile.
            cacheLoadedForGeneration = generation
            publish()
        }
    }

    override fun onSignedOut(reason: SignOutReason) {
        stateLock.withLock {
            generation++
            cancelRefresh()
            signedIn = false
            cached = null
            lastRefreshFailed = false
            publish()
        }
        // No disk I/O here: observers run under the session lock, often on the main thread.
        scope.launch { dbMutex.withLock { local.retainOnly(currentUserId()) } }
    }

    override fun refresh() {
        // The generation is read before the session, so a sign-in in between makes this call a no-op
        // instead of attributing the old account's id to the new session.
        val startGeneration = stateLock.withLock { generation }
        val userId = currentUserId() ?: return
        val job = stateLock.withLock {
            if (startGeneration != generation || refreshJob != null) {
                null
            } else {
                // Lazy, so the job is registered before it can finish.
                val newJob = scope.launch(start = CoroutineStart.LAZY) { runRefresh(startGeneration, userId) }
                refreshJob = newJob
                newJob.invokeOnCompletion {
                    stateLock.withLock {
                        if (refreshJob === newJob) {
                            refreshJob = null
                            publish()
                        }
                    }
                }
                publish()
                newJob
            }
        }
        job?.start()
    }

    override suspend fun saveSignedInProfile(profile: AccountProfile) {
        val startGeneration = stateLock.withLock { generation }
        store(startGeneration, profile)
    }

    private suspend fun runRefresh(startGeneration: Int, userId: String) {
        dbMutex.withLock { loadCache(startGeneration, userId) }
        when (val result = remote.me()) {
            is Result.Success -> {
                if (result.data.id == userId) {
                    store(startGeneration, result.data.toAccountProfile())
                } else {
                    logger.warn(LogTags.PROFILE) { "profile response dropped: account mismatch" }
                    markRefreshFailed(startGeneration)
                }
            }

            is Result.Failure -> markRefreshFailed(startGeneration)
        }
    }

    /** Reads the stored profile into memory once per session. Called with [dbMutex] held. */
    private suspend fun loadCache(startGeneration: Int, userId: String) {
        val needed = stateLock.withLock {
            startGeneration == generation && cacheLoadedForGeneration != startGeneration
        }
        if (!needed) return
        // A failed read is logged by the data source and treated as "nothing stored".
        val stored = when (val read = local.read(userId)) {
            is Result.Success -> read.data?.takeIf { it.userId == userId }
            is Result.Failure -> null
        }
        stateLock.withLock {
            if (startGeneration == generation) {
                cacheLoadedForGeneration = startGeneration
                if (cached == null) cached = stored
                publish()
            }
        }
    }

    /**
     * Writes [profile] to disk and shows it, unless the session changed since [startGeneration]. The
     * check is repeated under [dbMutex] right before the write, and a logout that comes later queues
     * its own deletion behind this write, so the row cannot outlive the session. A failed write still
     * updates the memory copy (AC-17).
     */
    private suspend fun store(startGeneration: Int, profile: AccountProfile) {
        dbMutex.withLock {
            if (isCurrent(startGeneration, profile.userId)) local.replaceWith(profile)
        }
        val applied = stateLock.withLock {
            if (startGeneration == generation && signedIn) {
                cached = profile
                lastRefreshFailed = false
                cacheLoadedForGeneration = startGeneration
                publish()
                true
            } else {
                false
            }
        }
        if (!applied) logger.debug(LogTags.PROFILE) { "profile result dropped: session changed" }
    }

    private fun markRefreshFailed(startGeneration: Int) {
        val applied = stateLock.withLock {
            if (startGeneration == generation) {
                lastRefreshFailed = true
                publish()
                true
            } else {
                false
            }
        }
        if (!applied) logger.debug(LogTags.PROFILE) { "profile result dropped: session changed" }
    }

    /**
     * True while the session that started the work is still the stored one. The session lock and
     * [stateLock] are taken one after the other, never nested.
     */
    private fun isCurrent(startGeneration: Int, userId: String): Boolean =
        currentUserId() == userId && stateLock.withLock { startGeneration == generation }

    private fun currentUserId(): String? = sessionManager.withSessionLock {
        if (sessionManager.getToken() != null) sessionManager.getUserId()?.takeIf { it.isNotBlank() } else null
    }

    private fun cancelRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    /** Called with [stateLock] held. */
    private fun publish() {
        _state.value = profileStateOf(
            signedIn = signedIn,
            cached = cached,
            isRefreshing = refreshJob != null,
            lastRefreshFailed = lastRefreshFailed,
        )
    }
}
