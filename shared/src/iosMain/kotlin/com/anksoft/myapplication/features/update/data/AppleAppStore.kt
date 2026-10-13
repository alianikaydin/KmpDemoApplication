package com.anksoft.myapplication.features.update.data

import com.anksoft.myapplication.features.update.domain.AppStore
import com.anksoft.myapplication.features.update.domain.model.UpdatePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import kotlin.coroutines.resume

/** Info.plist key filled from `KMP_APP_STORE_ID` in the xcconfig files. */
private const val APP_STORE_ID_KEY = "KMPAppStoreID"

/**
 * Opens this app's App Store page. The numeric App Store ID comes from the build configuration
 * (AC-20); without a usable one nothing is opened and the answer is false (AC-4).
 */
internal class AppleAppStore : AppStore {

    override val platform: UpdatePlatform = UpdatePlatform.IOS

    override suspend fun openAppPage(): Boolean {
        val appStoreId = NSBundle.mainBundle.objectForInfoDictionaryKey(APP_STORE_ID_KEY) as? String
        val url = StoreUrls.appStore(appStoreId)?.let { NSURL.URLWithString(it) } ?: return false
        return withContext(Dispatchers.Main) {
            // The completion handler reports whether the URL was really opened.
            suspendCancellableCoroutine { continuation ->
                UIApplication.sharedApplication.openURL(
                    url,
                    options = emptyMap<Any?, Any>(),
                    completionHandler = { opened -> continuation.resume(opened) }
                )
            }
        }
    }
}
