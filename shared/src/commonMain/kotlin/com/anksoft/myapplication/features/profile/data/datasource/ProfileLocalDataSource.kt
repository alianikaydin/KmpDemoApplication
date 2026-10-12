package com.anksoft.myapplication.features.profile.data.datasource

import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.EmptyResult
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.features.profile.domain.model.AccountProfile

/** The profile cache on disk. Suspends on a background dispatcher; never throws (except cancellation). */
interface ProfileLocalDataSource {
    /** The stored profile of [userId] only; another account's row is never returned (AC-12). */
    suspend fun read(userId: String): Result<AccountProfile?, DataError.Local>

    /** Stores [profile] and removes every other account's row in one transaction (AC-12). */
    suspend fun replaceWith(profile: AccountProfile): EmptyResult<DataError.Local>

    /** Keeps only [userId]'s row; null removes every row (AC-10, AC-11, cold start). */
    suspend fun retainOnly(userId: String?): EmptyResult<DataError.Local>
}
