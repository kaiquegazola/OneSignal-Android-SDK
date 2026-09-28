package com.onesignal.internal

import android.content.Context
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.internal.logging.Logging

/**
 * Persisted gate deciding whether the SDK may initialize itself from the cached appId
 * (receivers, activities, jobs and workers calling `initWithContext(context)` without an appId).
 *
 * Read and written through raw [android.content.SharedPreferences] instead of
 * IPreferencesService so it works before the SDK is bootstrapped and is never lost to the
 * buffered async write loop. Defaults to NOT allowed when never set.
 */
internal object AutoInitGate {
    const val PREFS_KEY = "onesignal_auto_init_allowed"

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun isAllowed(context: Context): Boolean =
        try {
            prefs(context).getBoolean(PREFS_KEY, false)
        } catch (e: Exception) {
            // e.g. credential-encrypted storage still locked (direct boot)
            false
        }

    @Suppress("TooGenericExceptionCaught")
    fun setAllowed(
        context: Context,
        allowed: Boolean,
    ) {
        // Never throw: this runs right before internalInit, which owns the locked-storage handling.
        try {
            prefs(context).edit().putBoolean(PREFS_KEY, allowed).apply()
        } catch (e: Exception) {
            Logging.warn("OneSignal: could not persist $PREFS_KEY", e)
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
}
