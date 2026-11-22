package gui.pgcr.storyteller

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Versão COM Firebase (usada quando existe app/google-services.json). */
internal suspend fun awaitFcmToken(context: Context): String? {
    if (FirebaseApp.getApps(context).isEmpty()) return null
    return suspendCancellableCoroutine { cont ->
        runCatching {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (cont.isActive) cont.resume(if (task.isSuccessful) task.result else null)
            }
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }
}
