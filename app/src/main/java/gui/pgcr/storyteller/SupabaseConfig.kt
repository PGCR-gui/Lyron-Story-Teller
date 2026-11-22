package gui.pgcr.storyteller

import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val SUPABASE_URL = "https://pvgeeaapmrwdmyyiyxaz.supabase.co"
const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_9pFZVySKZ-KexZQShRhiXw_5Q3giamg"

/**
 * Link de retorno (deep link) para os e-mails do Supabase. Precisa estar cadastrado em
 * Authentication → URL Configuration → Redirect URLs, e bater com o <data> do AndroidManifest.
 */
const val AUTH_REDIRECT_SCHEME = "gui.pgcr.storyteller"
const val AUTH_REDIRECT_HOST = "login-callback"
const val AUTH_REDIRECT_URL = "$AUTH_REDIRECT_SCHEME://$AUTH_REDIRECT_HOST"

/** Mensagem de conta (e-mail confirmado, erro de link, etc.) exibida em um diálogo pelo app. */
val accountNotice = mutableStateOf<String?>(null)

val supabase: SupabaseClient by lazy {
    createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_PUBLISHABLE_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
    }
}

@Serializable
data class RemoteStory(
    val id: String? = null,
    @SerialName("owner_id") val ownerId: String? = null,
    val title: String = "",
    val author: String = "Autor",
    val description: String = "",
    val rating: String = "Livre",
    val status: String = "Em progresso",
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val classifications: List<String> = emptyList(),
    val editable: Boolean = true,
    @SerialName("is_public") val isPublic: Boolean = false,
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("cover_path") val coverPath: String? = null,
    @SerialName("chapter_count") val chapterCount: Int = 1,
    @SerialName("word_count") val wordCount: Int = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

suspend fun signInEmail(email: String, password: String) {
    supabase.auth.signInWith(Email) {
        this.email = email.trim()
        this.password = password
    }
}

/** Tamanho mínimo de senha exigido pelo Supabase Auth (padrão do projeto: 8). */
const val AUTH_MIN_PASSWORD = 8

/** Troca a mensagem técnica do Supabase (URL, cabeçalhos, códigos) por um texto curto em português. */
fun friendlyAuthError(e: Throwable, isSignUp: Boolean): String {
    val raw = e.message.orEmpty()
    val low = raw.lowercase()
    return when {
        "weak_password" in low -> "A senha precisa ter pelo menos $AUTH_MIN_PASSWORD caracteres."
        "user_already_exists" in low || "email_exists" in low || "already registered" in low ->
            "Esse e-mail já tem uma conta. Toque em “Entrar”."
        "invalid_credentials" in low || "invalid login credentials" in low -> "E-mail ou senha incorretos."
        "email_not_confirmed" in low -> "Confirme seu e-mail pelo link que enviamos antes de entrar."
        "validation_failed" in low || "invalid format" in low || "email_address_invalid" in low ->
            "Esse e-mail não parece válido."
        "over_email_send_rate_limit" in low || "over_request_rate_limit" in low || "rate limit" in low ->
            "Muitas tentativas seguidas. Espere alguns minutos e tente de novo."
        "database error" in low || "unexpected_failure" in low || "unexpected_failure" in raw ->
            "O servidor não conseguiu concluir agora (erro no banco de dados). Tente de novo mais tarde."
        "unable to resolve host" in low || "timeout" in low || "timed out" in low ||
            "failed to connect" in low || e is java.io.IOException ->
            "Sem conexão com o servidor. Confira a internet e tente de novo."
        raw.isBlank() -> if (isSignUp) "Falha ao criar a conta." else "Falha no login."
        // Último recurso: só a primeira linha, sem URL/cabeçalhos.
        else -> raw.lineSequence().first().take(160)
    }
}

/**
 * Cria a conta. username e nickname vão como metadados do usuário (raw_user_meta_data); o gatilho do banco
 * (veja push_setup/03_fix_username_profiles.sql) usa esses valores ao criar o perfil, mesmo antes de a
 * pessoa confirmar o e-mail.
 */
suspend fun signUpEmail(email: String, password: String, username: String = "", nickname: String = "") {
    supabase.auth.signUpWith(Email, redirectUrl = AUTH_REDIRECT_URL) {
        this.email = email.trim()
        this.password = password
        if (username.isNotBlank() || nickname.isNotBlank()) {
            data = buildJsonObject {
                if (username.isNotBlank()) put("username", username.trim().lowercase())
                if (nickname.isNotBlank()) put("nickname", nickname.trim())
            }
        }
    }
}

suspend fun signOut() {
    supabase.auth.signOut()
}

fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

fun currentUserEmail(): String? = supabase.auth.currentUserOrNull()?.email

fun sessionStatusFlow(): Flow<SessionStatus> = supabase.auth.sessionStatus

suspend fun fetchPublicStories(limit: Int = 40): List<RemoteStory> {
    return supabase.from("stories")
        .select {
            filter {
                eq("is_public", true)
            }
        }
        .decodeList<RemoteStory>()
        .take(limit)
}

suspend fun downloadStoryBytes(filePath: String): ByteArray {
    // file_path no banco: {userId}/{storyId}.lyr  → bucket story-files
    return supabase.storage.from("story-files").downloadAuthenticated(filePath)
}

suspend fun publishStoryMetadata(story: RemoteStory): RemoteStory {
    // upsert: publicar de novo a mesma história (mesmo id) atualiza a linha em vez de dar erro de duplicidade.
    return supabase.from("stories")
        .upsert(story) {
            select()
        }
        .decodeSingle<RemoteStory>()
}

/**
 * Remove a história do catálogo. A permissão é do SERVIDOR (RLS): se a conta não for a dona, nenhuma
 * linha é apagada e esta função lança erro — o app não consegue burlar isso, mesmo modificado.
 */
suspend fun unpublishStory(story: RemoteStory) {
    val id = story.id ?: throw IllegalArgumentException("História sem identificador.")
    val deleted = supabase.from("stories")
        .delete {
            select()
            filter { eq("id", id) }
        }
        .decodeList<RemoteStory>()
        .firstOrNull() ?: throw IllegalStateException("Só quem publicou a história pode despublicá-la.")
    deleted.filePath?.let { path -> runCatching { supabase.storage.from("story-files").delete(path) } }
    deleted.coverPath?.let { path -> runCatching { supabase.storage.from("covers").delete(path) } }
}

suspend fun uploadStoryFile(path: String, bytes: ByteArray) {
    supabase.storage.from("story-files").upload(path, bytes) {
        upsert = true
    }
}

suspend fun uploadCoverFile(path: String, bytes: ByteArray) {
    supabase.storage.from("covers").upload(path, bytes) {
        upsert = true
    }
}

// ---------------------------------------------------------------------------
// Retorno do link de e-mail (confirmação de conta) → volta ao app já logado
// ---------------------------------------------------------------------------

fun isAuthCallbackUri(uri: Uri): Boolean =
    uri.scheme == AUTH_REDIRECT_SCHEME && uri.host == AUTH_REDIRECT_HOST

/**
 * O Supabase (fluxo padrão "implicit") devolve os tokens no fragmento da URL:
 * gui.pgcr.storyteller://login-callback#access_token=...&refresh_token=...&type=signup
 * Em caso de link vencido/usado, vem #error=...&error_description=...
 */
suspend fun handleAuthCallback(uri: Uri): String {
    val params = HashMap<String, String>()
    fun readPairs(raw: String?) {
        raw.orEmpty().split("&").forEach { pair ->
            val index = pair.indexOf('=')
            if (index > 0) params[pair.substring(0, index)] = Uri.decode(pair.substring(index + 1).replace('+', ' '))
        }
    }
    readPairs(uri.encodedQuery)
    readPairs(uri.encodedFragment)

    params["error_description"]?.let { return "Não foi possível confirmar: $it. Peça um novo e-mail de confirmação." }
    params["error"]?.let { return "Não foi possível confirmar o e-mail ($it)." }

    val accessToken = params["access_token"]
    val refreshToken = params["refresh_token"]
    if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank()) {
        return "O link abriu o app, mas não trouxe dados de login. Tente entrar com e-mail e senha."
    }
    return try {
        supabase.auth.importAuthToken(accessToken, refreshToken, retrieveUser = true)
        "E-mail confirmado! Você já está conectado."
    } catch (e: Exception) {
        "Não foi possível concluir o login pelo link: ${e.message ?: "erro desconhecido"}"
    }
}

// ---------------------------------------------------------------------------
// Perfil online: nome de usuário único, apelido, nome completo (privado) e foto
// ---------------------------------------------------------------------------

/** Linha pública de public.profiles (qualquer pessoa pode ler). */
@Serializable
data class RemoteProfile(
    val id: String,
    val username: String,
    val nickname: String = "",
    @SerialName("avatar_path") val avatarPath: String? = null,
    val bio: String = "",
    @SerialName("public_bio") val publicBio: Boolean = true,
    @SerialName("public_written") val publicWritten: Boolean = true,
    @SerialName("public_favorites") val publicFavorites: Boolean = false
)

/** Linha de public.profile_private: só a própria pessoa lê e grava. */
@Serializable
data class RemotePrivateProfile(
    val id: String,
    @SerialName("full_name") val fullName: String
)

/** Devolve o perfil da conta (e o nome completo) ou null se ainda não existe perfil online. */
suspend fun fetchMyProfile(uid: String): Pair<RemoteProfile, String>? {
    val profile = supabase.from("profiles")
        .select { filter { eq("id", uid) } }
        .decodeList<RemoteProfile>()
        .firstOrNull() ?: return null
    val fullName = supabase.from("profile_private")
        .select { filter { eq("id", uid) } }
        .decodeList<RemotePrivateProfile>()
        .firstOrNull()?.fullName.orEmpty()
    return profile to fullName
}

/** true se nenhuma OUTRA conta usa esse nome de usuário (já em minúsculas). */
suspend fun isUsernameAvailable(username: String, selfId: String?): Boolean {
    val rows = supabase.from("profiles")
        .select { filter { eq("username", username) } }
        .decodeList<RemoteProfile>()
    return rows.none { it.id != selfId }
}

suspend fun saveRemoteProfile(profile: RemoteProfile, fullName: String) {
    supabase.from("profiles").upsert(profile)
    supabase.from("profile_private").upsert(RemotePrivateProfile(id = profile.id, fullName = fullName))
}

suspend fun uploadAvatarFile(path: String, bytes: ByteArray) {
    supabase.storage.from("avatars").upload(path, bytes)
}

suspend fun downloadAvatarBytes(path: String): ByteArray {
    return supabase.storage.from("avatars").downloadPublic(path)
}

suspend fun deleteAvatarFile(path: String) {
    supabase.storage.from("avatars").delete(path)
}

suspend fun fetchProfileByUsername(username: String): RemoteProfile? {
    return supabase.from("profiles")
        .select { filter { eq("username", username.lowercase()) } }
        .decodeList<RemoteProfile>()
        .firstOrNull()
}

suspend fun fetchProfileById(id: String): RemoteProfile? {
    return supabase.from("profiles")
        .select { filter { eq("id", id) } }
        .decodeList<RemoteProfile>()
        .firstOrNull()
}

suspend fun fetchStoriesByOwner(ownerId: String, limit: Int = 40): List<RemoteStory> {
    return supabase.from("stories")
        .select {
            filter {
                eq("owner_id", ownerId)
                eq("is_public", true)
            }
        }
        .decodeList<RemoteStory>()
        .take(limit)
}

suspend fun downloadCoverBytes(path: String): ByteArray {
    return supabase.storage.from("covers").downloadPublic(path)
}

/** URL pública do bucket covers (se o bucket for público). */
fun publicCoverUrl(path: String?): String? {
    if (path.isNullOrBlank()) return null
    return "$SUPABASE_URL/storage/v1/object/public/covers/$path"
}

