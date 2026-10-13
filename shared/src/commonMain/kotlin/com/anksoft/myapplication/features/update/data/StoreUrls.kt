package com.anksoft.myapplication.features.update.data

/** Store page links of this app. They come from the client's own configuration, never the backend (AC-20). */
internal object StoreUrls {

    /** Google's documented "Linking to Google Play" web form; the app opens it with the Play package. */
    fun playStore(packageName: String): String =
        "https://play.google.com/store/apps/details?id=$packageName"

    /** Null unless [appStoreId] is a non-empty string of digits, so a placeholder never becomes a link. */
    fun appStore(appStoreId: String?): String? {
        val id = appStoreId?.trim().orEmpty()
        if (id.isEmpty() || !id.all { it in '0'..'9' }) return null
        return "https://apps.apple.com/app/id$id"
    }
}
