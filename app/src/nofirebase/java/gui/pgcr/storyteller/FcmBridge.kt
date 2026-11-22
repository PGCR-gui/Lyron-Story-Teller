package gui.pgcr.storyteller

import android.content.Context

/** Versão SEM Firebase (build sem app/google-services.json): não há token, então não há push. */
internal suspend fun awaitFcmToken(context: Context): String? = null
