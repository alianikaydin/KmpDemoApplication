package com.anksoft.myapplication.features.update.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.anksoft.myapplication.features.update.domain.AppStore
import com.anksoft.myapplication.features.update.domain.model.UpdatePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PLAY_STORE_PACKAGE = "com.android.vending"

/**
 * Opens this app's Google Play page, as Google describes in "Linking to Google Play". The link is
 * built from the running app's package name, so every flavor points at its own listing (AC-20).
 */
internal class PlayStoreAppStore(private val context: Context) : AppStore {

    override val platform: UpdatePlatform = UpdatePlatform.ANDROID

    override suspend fun openAppPage(): Boolean = withContext(Dispatchers.Main) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(StoreUrls.playStore(context.packageName)))
                .setPackage(PLAY_STORE_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            // No Play Store on this device (emulator image without it).
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
