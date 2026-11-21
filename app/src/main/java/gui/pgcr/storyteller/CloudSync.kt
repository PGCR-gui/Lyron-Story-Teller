package gui.pgcr.storyteller

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.resume

/**
 * Espelha no Supabase o que o servidor precisa saber para mandar push:
 *  - story_favorites (quem favoritou qual história publicada)
 *  - author_follows  (quem segue qual autor)
 *  - device_tokens   (token FCM do aparelho, via função register_device_token)
 *
 * O aparelho continua sendo a fonte principal (favoritas/seguidos funcionam offline); aqui só se envia
 * o que mudou. Remoções feitas sem internet ficam numa fila e são reenviadas na próxima sincronização.
 * Tudo é "melhor esforço": se a tabela não existir ou a rede falhar, o app segue normal e o aviso
 * local (WorkManager) continua funcionando.
 */

private const val PREFS = "lyron_notify"
private const val KEY_PENDING_UNFAV = "pending_unfavorite"   // ids remotos de histórias
private const val KEY_PENDING_UNFOLLOW = "pending_unfollow"   // ids de autores
private const val KEY_FCM_TOKEN = "fcm_token"
private const val KEY_PUSH_ACTIVE = "push_active"

private val cloudScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

private fun pendingSet(context: Context, key: String): MutableSet<String> =
    prefs(context).getStringSet(key, emptySet())!!.toMutableSet()

private fun savePending(context: Context, key: String, set: Set<String>) {
    prefs(context).edit().putStringSet(key, set).apply()
}

@Serializable
private data class FavoriteRow(
    @SerialName("user_id") val userId: String,
    @SerialName("story_id") val storyId: String
)

@Serializable
private data class FollowRow(
    @SerialName("follower_id") val followerId: String,
    @SerialName("author_id") val authorId: String
)

/** O push está registrado para esta conta? (usado na tela de notificações) */
internal fun isPushActive(context: Context): Boolean = prefs(context).getBoolean(KEY_PUSH_ACTIVE, false)

// ---------------------------------------------------------------------------
// Pedidos feitos pela interface (não bloqueiam; falhas ficam na fila)
// ---------------------------------------------------------------------------

/** Chame depois de salvar as favoritas/seguidos no aparelho: envia o que mudou. */
internal fun requestCloudSync(context: Context) {
    val app = context.applicationContext
    cloudScope.launch { runCatching { syncCloudSubscriptions(app) } }
}

/** A pessoa desfavoritou uma história publicada: agenda a remoção no servidor. */
internal fun queueRemoteUnfavorite(context: Context, remoteStoryId: String?) {
    if (remoteStoryId.isNullOrBlank()) return
    savePending(context, KEY_PENDING_UNFAV, pendingSet(context, KEY_PENDING_UNFAV).apply { add(remoteStoryId) })
    requestCloudSync(context)
}

/** A pessoa deixou de seguir um autor: agenda a remoção no servidor. */
internal fun queueRemoteUnfollow(context: Context, authorId: String) {
    savePending(context, KEY_PENDING_UNFOLLOW, pendingSet(context, KEY_PENDING_UNFOLLOW).apply { add(authorId) })
    requestCloudSync(context)
}

/** Seguiu de novo / favoritou de novo: cancela uma remoção que estava na fila. */
internal fun cancelPendingRemovals(context: Context, remoteStoryId: String? = null, authorId: String? = null) {
    if (!remoteStoryId.isNullOrBlank()) {
        savePending(context, KEY_PENDING_UNFAV, pendingSet(context, KEY_PENDING_UNFAV).apply { remove(remoteStoryId) })
    }
    if (!authorId.isNullOrBlank()) {
        savePending(context, KEY_PENDING_UNFOLLOW, pendingSet(context, KEY_PENDING_UNFOLLOW).apply { remove(authorId) })
    }
}

// ---------------------------------------------------------------------------
// Sincronização
// ---------------------------------------------------------------------------

internal suspend fun syncCloudSubscriptions(context: Context) {
    val uid = currentUserId() ?: return

    // 1) Remoções pendentes
    val pendingFav = pendingSet(context, KEY_PENDING_UNFAV)
    if (pendingFav.isNotEmpty()) {
        val done = mutableSetOf<String>()
        pendingFav.forEach { storyId ->
            runCatching {
                supabase.from("story_favorites").delete {
                    filter {
                        eq("user_id", uid)
                        eq("story_id", storyId)
                    }
                }
            }.onSuccess { done += storyId }
        }
        savePending(context, KEY_PENDING_UNFAV, pendingFav - done)
    }
    val pendingFollow = pendingSet(context, KEY_PENDING_UNFOLLOW)
    if (pendingFollow.isNotEmpty()) {
        val done = mutableSetOf<String>()
        pendingFollow.forEach { authorId ->
            runCatching {
                supabase.from("author_follows").delete {
                    filter {
                        eq("follower_id", uid)
                        eq("author_id", authorId)
                    }
                }
            }.onSuccess { done += authorId }
        }
        savePending(context, KEY_PENDING_UNFOLLOW, pendingFollow - done)
    }
    val stillPendingFav = pendingSet(context, KEY_PENDING_UNFAV)
    val stillPendingFollow = pendingSet(context, KEY_PENDING_UNFOLLOW)

    // 2) Favoritas do aparelho -> servidor (só histórias que existem online)
    val favoriteIds = loadFavoriteIds(context)
    val favoriteRemoteIds = loadUserStories(context)
        .filter { it.id in favoriteIds }
        .mapNotNull { it.remoteId?.takeIf { id -> id.isNotBlank() } }
        .filter { it !in stillPendingFav }
        .toSet()
    if (favoriteRemoteIds.isNotEmpty()) {
        supabase.from("story_favorites").upsert(favoriteRemoteIds.map { FavoriteRow(uid, it) })
    }

    // 3) Autores seguidos: aparelho -> servidor e servidor -> aparelho (restaura ao trocar de aparelho)
    val localFollows = loadFollowedAuthors(context)
    val toPush = localFollows.map { it.ownerId }.filter { it !in stillPendingFollow && it != uid }
    if (toPush.isNotEmpty()) {
        supabase.from("author_follows").upsert(toPush.map { FollowRow(uid, it) })
    }
    val serverFollows = supabase.from("author_follows")
        .select { filter { eq("follower_id", uid) } }
        .decodeList<FollowRow>()
    val localIds = localFollows.map { it.ownerId }.toSet()
    serverFollows
        .map { it.authorId }
        .filter { it !in localIds && it !in stillPendingFollow }
        .forEach { authorId ->
            val profile = runCatching { fetchProfileById(authorId) }.getOrNull()
            val name = profile?.nickname?.takeIf { it.isNotBlank() }
                ?: profile?.username?.takeIf { it.isNotBlank() }
                ?: "Autor"
            storeFollowLocally(context, authorId, name)
        }
}

// ---------------------------------------------------------------------------
// Token do aparelho (FCM)
// ---------------------------------------------------------------------------

/** null se o Firebase não está configurado (sem google-services.json) ou se o token não veio. */
private suspend fun awaitFcmToken(context: Context): String? {
    if (FirebaseApp.getApps(context).isEmpty()) return null
    return suspendCancellableCoroutine { cont ->
        runCatching {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (cont.isActive) cont.resume(if (task.isSuccessful) task.result else null)
            }
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }
}

/** Registra (ou atualiza) o token deste aparelho para a conta logada, com as preferências de aviso. */
internal fun syncPushRegistration(context: Context) {
    val app = context.applicationContext
    cloudScope.launch { runCatching { registerPushTokenNow(app) } }
}

private suspend fun registerPushTokenNow(context: Context) {
    if (currentUserId() == null) return
    val token = awaitFcmToken(context) ?: return
    val settings = loadAppSettings(context)
    supabase.postgrest.rpc(
        function = "register_device_token",
        parameters = buildJsonObject {
            put("p_token", token)
            put("p_notify_favorites", settings.notifyFavorites)
            put("p_notify_authors", settings.notifyFollowedAuthors)
        }
    )
    prefs(context).edit().putString(KEY_FCM_TOKEN, token).putBoolean(KEY_PUSH_ACTIVE, true).apply()
}

/** Chamado pelo FirebaseMessagingService quando o Firebase troca o token. */
internal fun onFcmTokenRefreshed(context: Context, token: String) {
    prefs(context).edit().putString(KEY_FCM_TOKEN, token).apply()
    syncPushRegistration(context)
}

/** Antes de sair da conta: tira o token deste aparelho do servidor (senão o aparelho continuaria recebendo). */
internal suspend fun unregisterPushToken(context: Context) {
    val token = prefs(context).getString(KEY_FCM_TOKEN, null)
    prefs(context).edit().putBoolean(KEY_PUSH_ACTIVE, false).apply()
    if (token.isNullOrBlank() || currentUserId() == null) return
    supabase.postgrest.rpc(
        function = "unregister_device_token",
        parameters = buildJsonObject { put("p_token", token) }
    )
}
