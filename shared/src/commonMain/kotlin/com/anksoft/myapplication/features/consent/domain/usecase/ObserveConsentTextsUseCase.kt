package com.anksoft.myapplication.features.consent.domain.usecase

import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.preferences.ContentLanguage
import com.anksoft.myapplication.features.consent.domain.model.ConsentTexts
import com.anksoft.myapplication.features.consent.domain.repository.ConsentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest

/** Where loading the consent text stands. */
sealed interface ConsentTextsLoad {
    data object Loading : ConsentTextsLoad
    data class Loaded(val texts: ConsentTexts) : ConsentTextsLoad
    data class Failed(val error: DataError) : ConsentTextsLoad
}

/**
 * The consent text in the language in use. It loads once, loads again whenever the language
 * changes (AC-24) and whenever [retry] emits (AC-4). A newer request cancels an older one still
 * running, so a late answer in the previous language never replaces the current one.
 */
class ObserveConsentTextsUseCase(
    private val repository: ConsentRepository,
    private val contentLanguage: ContentLanguage
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(retry: Flow<Unit>): Flow<ConsentTextsLoad> =
        combine(contentLanguage.tag, retry.onStart { emit(Unit) }) { tag, _ -> tag }
            .transformLatest { tag ->
                emit(ConsentTextsLoad.Loading)
                emit(
                    when (val result = repository.getTexts(tag)) {
                        is Result.Success -> ConsentTextsLoad.Loaded(result.data)
                        is Result.Failure -> ConsentTextsLoad.Failed(result.error)
                    }
                )
            }
}
