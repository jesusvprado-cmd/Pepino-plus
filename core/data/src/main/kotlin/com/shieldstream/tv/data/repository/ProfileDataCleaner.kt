package com.shieldstream.tv.data.repository

import android.content.Context
import com.shieldstream.tv.data.security.TokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

internal const val TRAKT_SYNC_PREFS = "trakt_sync"

@Singleton
class ProfileDataCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokens: TokenStore,
) {
    fun delete(profileId: String) {
        for (name in listOf(
            WatchProgressRepositoryImpl.prefsNameFor(profileId),
            MyListRepositoryImpl.prefsNameFor(profileId),
            EpisodeTrackingRepositoryImpl.prefsNameFor(profileId),
        )) {
            // Mínimo Android 8: siempre se puede borrar el archivo entero.
            context.deleteSharedPreferences(name)
        }
        clearTrakt(profileId)
    }

    fun clearTrakt(profileId: String) {
        tokens.setSecret(TraktSession.storeKey(profileId), null)
        context.getSharedPreferences(TRAKT_SYNC_PREFS, Context.MODE_PRIVATE).edit()
            .remove("all_$profileId")
            .remove("last_$profileId")
            .remove("remote_$profileId")
            .remove("wl_$profileId")
            .apply()
    }
}
