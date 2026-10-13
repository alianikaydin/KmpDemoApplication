package com.anksoft.myapplication.features.update.domain.model

/** How strongly the installed version has to be updated. */
sealed interface UpdateRequirement {
    /** Nothing to show. */
    data object None : UpdateRequirement

    /** A dismissible suggestion to move to [latest]. */
    data class Optional(val latest: AppVersion) : UpdateRequirement

    /** The installed version is no longer supported. */
    data object Required : UpdateRequirement
}
