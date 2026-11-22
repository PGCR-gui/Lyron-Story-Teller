package gui.pgcr.storyteller

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Recebe os pushes enviados pela Edge Function `notify-story-update`.
 * As mensagens são "data-only" (sem bloco notification): assim o app sempre monta o aviso,
 * no canal certo e com o mesmo id usado pela verificação local — se os dois chegarem, um só
 * aviso aparece (o segundo apenas atualiza o primeiro, sem tocar de novo).
 *
 * Campos esperados em message.data:
 *   kind = "favorite" | "author", title, body, story_id, owner_id
 */
class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        onFcmTokenRefreshed(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val kind = data["kind"] ?: return
        val title = data["title"]?.takeIf { it.isNotBlank() } ?: return
        val body = data["body"].orEmpty()
        val settings = loadAppSettings(applicationContext)
        when (kind) {
            "favorite" -> {
                if (!settings.notifyFavorites) return
                postNotification(applicationContext, CHANNEL_FAVORITES, "fav-${data["story_id"].orEmpty()}", title, body)
            }
            "author" -> {
                if (!settings.notifyFollowedAuthors) return
                postNotification(applicationContext, CHANNEL_AUTHORS, "author-${data["owner_id"].orEmpty()}", title, body)
            }
        }
    }
}
