package com.onesignal.internal

import android.content.Context
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.internal.logging.Logging
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Persisted gate deciding whether the SDK may initialize itself from the cached appId
 * (receivers, activities, jobs and workers calling `initWithContext(context)` without an appId).
 *
 * Read and written through raw [android.content.SharedPreferences] instead of
 * IPreferencesService so it works before the SDK is bootstrapped and is never lost to the
 * buffered async write loop. Defaults to NOT allowed when never set.
 *
 * Every read and write runs on one serial thread, so they apply in call order: an explicit init
 * queues `true` without blocking its caller, and a later [setAllowed] always lands after it.
 * Writes use commit() so a value is on disk once written.
 */
internal object AutoInitGate {
    const val PREFS_KEY = "onesignal_auto_init_allowed"

    // ponytail: one daemon thread for one boolean; FIFO order is the whole point.
    private val serial =
        Executors.newSingleThreadExecutor { r -> Thread(r, "OneSignal-AutoInitGate").apply { isDaemon = true } }

    /** Blocks until every previously queued write has landed. Call off the main thread. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun isAllowed(context: Context): Boolean =
        try {
            serial.submit(Callable { prefs(context).getBoolean(PREFS_KEY, false) }).get()
        } catch (e: Exception) {
            // e.g. credential-encrypted storage still locked (direct boot)
            false
        }

    /** Durable write, after every previously queued write. Blocks the caller on a small disk write. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun setAllowed(
        context: Context,
        allowed: Boolean,
    ) {
        try {
            serial.submit { write(context, allowed) }.get()
        } catch (e: Exception) {
            Logging.warn("OneSignal: could not persist $PREFS_KEY", e)
        }
    }

    /** Queues the write in call order without blocking the caller (used by initWithContext). */
    fun setAllowedAsync(
        context: Context,
        allowed: Boolean,
    ) {
        serial.execute { write(context, allowed) }
    }

    // Never throws: callers own the locked-storage handling.
    @Suppress("TooGenericExceptionCaught")
    private fun write(
        context: Context,
        allowed: Boolean,
    ) {
        try {
            if (!prefs(context).edit().putBoolean(PREFS_KEY, allowed).commit()) {
                Logging.warn("OneSignal: could not persist $PREFS_KEY")
            }
        } catch (e: Exception) {
            Logging.warn("OneSignal: could not persist $PREFS_KEY", e)
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
}
