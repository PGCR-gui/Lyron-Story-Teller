package gui.pgcr.storyteller

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val NOTIFY_PREFS = "lyron_notify"
private const val KEY_FOLLOWED = "followed_authors"       // ownerId -> nome
private const val KEY_AUTHOR_BASELINE = "author_baseline"  // ownerId -> { storyId -> nº de capítulos }
private const val KEY_FAV_NOTIFIED = "fav_notified"        // storyId remoto -> último nº de capítulos já avisado
private const val KEY_MY_USER_ID = "my_user_id"
private const val KEY_PERMISSION_ASKED = "permission_asked"

internal const val CHANNEL_FAVORITES = "new_chapters_favorites"
internal const val CHANNEL_AUTHORS = "followed_authors"

private const val WORK_PERIODIC = "lyron_new_content_periodic"
private const val WORK_NOW = "lyron_new_content_now"

private fun notifyPrefs(context: Context) = context.getSharedPreferences(NOTIFY_PREFS, Context.MODE_PRIVATE)

// ---------------------------------------------------------------------------
// Autores seguidos (guardados no aparelho, como as favoritas)
// ---------------------------------------------------------------------------

internal data class FollowedAuthor(val ownerId: String, val name: String)

private fun readObject(context: Context, key: String): JSONObject =
    runCatching { JSONObject(notifyPrefs(context).getString(key, null) ?: "{}") }.getOrDefault(JSONObject())

private fun writeObject(context: Context, key: String, json: JSONObject) {
    notifyPrefs(context).edit().putString(key, json.toString()).apply()
}

internal fun loadFollowedAuthors(context: Context): List<FollowedAuthor> {
    val json = readObject(context, KEY_FOLLOWED)
    return json.keys().asSequence()
        .map { FollowedAuthor(it, json.optString(it, "Autor")) }
        .sortedBy { it.name.lowercase() }
        .toList()
}

internal fun isFollowingAuthor(context: Context, ownerId: String?): Boolean =
    !ownerId.isNullOrBlank() && readObject(context, KEY_FOLLOWED).has(ownerId)

/**
 * Passa a seguir o autor. O "marco zero" (histórias e capítulos que ele já tem) é gravado na primeira
 * verificação, sem notificar; só o que aparecer depois avisa.
 */
internal fun followAuthor(context: Context, ownerId: String, name: String) {
    storeFollowLocally(context, ownerId, name)
    cancelPendingRemovals(context, authorId = ownerId)
    scheduleNewContentChecks(context)
    checkNewContentNow(context)
    requestCloudSync(context) // grava em author_follows (para o push do servidor)
}

/** Só grava no aparelho (usado também ao restaurar os seguidos vindos do servidor). */
internal fun storeFollowLocally(context: Context, ownerId: String, name: String) {
    writeObject(context, KEY_FOLLOWED, readObject(context, KEY_FOLLOWED).put(ownerId, name.ifBlank { "Autor" }))
}

internal fun unfollowAuthor(context: Context, ownerId: String) {
    writeObject(context, KEY_FOLLOWED, readObject(context, KEY_FOLLOWED).apply { remove(ownerId) })
    writeObject(context, KEY_AUTHOR_BASELINE, readObject(context, KEY_AUTHOR_BASELINE).apply { remove(ownerId) })
    queueRemoteUnfollow(context, ownerId)
}

/** Guarda quem está logado, para não avisar a pessoa sobre as próprias publicações. */
internal fun saveMyUserIdForNotifications(context: Context, userId: String?) {
    notifyPrefs(context).edit().apply {
        if (userId.isNullOrBlank()) remove(KEY_MY_USER_ID) else putString(KEY_MY_USER_ID, userId)
    }.apply()
}

// ---------------------------------------------------------------------------
// Canais e envio
// ---------------------------------------------------------------------------

internal fun createNotificationChannels(context: Context) {
    val manager = NotificationManagerCompat.from(context)
    manager.createNotificationChannel(
        NotificationChannelCompat.Builder(CHANNEL_FAVORITES, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName("Capítulos novos (favoritas)")
            .setDescription("Avisa quando uma história favoritada recebe capítulos novos.")
            .build()
    )
    manager.createNotificationChannel(
        NotificationChannelCompat.Builder(CHANNEL_AUTHORS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName("Autores que você segue")
            .setDescription("Avisa quando um autor seguido publica uma história ou um capítulo novo.")
            .build()
    )
}

internal fun notificationsAllowed(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

@SuppressLint("MissingPermission")
internal fun postNotification(context: Context, channel: String, key: String, title: String, text: String): Boolean {
    if (!notificationsAllowed(context)) return false
    createNotificationChannels(context)
    val open = PendingIntent.getActivity(
        context,
        key.hashCode(),
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notification = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat_notify)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(open)
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()
    return runCatching { NotificationManagerCompat.from(context).notify(key.hashCode(), notification) }.isSuccess
}

// ---------------------------------------------------------------------------
// Verificação de novidades
// ---------------------------------------------------------------------------

private fun chaptersText(count: Int) = if (count == 1) "1 capítulo novo" else "$count capítulos novos"

/**
 * Compara o catálogo online com o que o aparelho já conhece:
 *  - favoritas baixadas: avisa quando o online tem mais capítulos do que a cópia local (e do que já foi avisado);
 *  - autores seguidos: avisa história nova ou capítulo novo em história que já existia no "marco zero".
 * Devolve quantas notificações foram enviadas.
 */
internal suspend fun checkForNewContent(context: Context): Int {
    val settings = loadAppSettings(context)
    val followed = if (settings.notifyFollowedAuthors) loadFollowedAuthors(context) else emptyList()
    val favoriteIds = if (settings.notifyFavorites) loadFavoriteIds(context) else emptySet()
    if (followed.isEmpty() && favoriteIds.isEmpty()) return 0

    val remote = fetchPublicStories(limit = Int.MAX_VALUE)
    saveCatalogCache(context, remote)
    val remoteById = remote.filter { it.id != null }.associateBy { it.id!! }
    val myId = notifyPrefs(context).getString(KEY_MY_USER_ID, null)
    var sent = 0

    // --- favoritas ---------------------------------------------------------
    if (favoriteIds.isNotEmpty()) {
        val notified = readObject(context, KEY_FAV_NOTIFIED)
        loadUserStories(context)
            .filter { it.id in favoriteIds && !it.remoteId.isNullOrBlank() }
            .forEach { local ->
                val r = remoteById[local.remoteId] ?: return@forEach
                if (myId != null && r.ownerId == myId) return@forEach
                val id = r.id ?: return@forEach
                val lastNotified = notified.optInt(id, 0)
                val known = maxOf(local.chapters.size, lastNotified)
                if (r.chapterCount > known) {
                    val diff = r.chapterCount - known
                    val ok = postNotification(
                        context, CHANNEL_FAVORITES, "fav-$id",
                        "Novo capítulo em “${r.title}”",
                        "${chaptersText(diff)} de ${r.author}. Abra o app para atualizar sua cópia."
                    )
                    if (ok) sent++
                }
                // Mesmo sem permissão, avança o marco para não acumular avisos velhos.
                notified.put(id, maxOf(r.chapterCount, 0))
            }
        writeObject(context, KEY_FAV_NOTIFIED, notified)
    }

    // --- autores seguidos ----------------------------------------------------
    if (followed.isNotEmpty()) {
        val baselines = readObject(context, KEY_AUTHOR_BASELINE)
        followed.forEach { author ->
            if (myId != null && author.ownerId == myId) return@forEach
            val theirs = remote.filter { it.ownerId == author.ownerId && it.id != null }
            val current = JSONObject().also { o -> theirs.forEach { o.put(it.id!!, it.chapterCount) } }
            val baseline = baselines.optJSONObject(author.ownerId)
            if (baseline != null) {
                val events = mutableListOf<String>()
                theirs.forEach { s ->
                    val id = s.id!!
                    if (!baseline.has(id)) {
                        events += "Nova história: “${s.title}”"
                    } else if (s.chapterCount > baseline.optInt(id, 0)) {
                        events += "“${s.title}”: ${chaptersText(s.chapterCount - baseline.optInt(id, 0))}"
                    }
                }
                if (events.isNotEmpty()) {
                    val title = if (events.size == 1 && events[0].startsWith("Nova história"))
                        "${author.name} publicou uma história"
                    else "${author.name} tem novidades"
                    val ok = postNotification(context, CHANNEL_AUTHORS, "author-${author.ownerId}", title, events.joinToString("\n"))
                    if (ok) sent++
                }
            }
            // Sem baseline ainda = primeira verificação depois de seguir: só registra o marco zero.
            baselines.put(author.ownerId, current)
        }
        writeObject(context, KEY_AUTHOR_BASELINE, baselines)
    }
    return sent
}

internal class NewContentWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        checkForNewContent(applicationContext)
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Sem rede/servidor fora do ar: tenta de novo algumas vezes; depois espera o próximo ciclo.
        if (runAttemptCount < 3) Result.retry() else Result.success()
    }
}

private val networkConstraint: Constraints
    get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

/** Agenda a verificação a cada hora (o Android pode agrupar/atrasar para poupar bateria). */
internal fun scheduleNewContentChecks(context: Context) {
    createNotificationChannels(context)
    val request = PeriodicWorkRequestBuilder<NewContentWorker>(1, TimeUnit.HOURS)
        .setConstraints(networkConstraint)
        .build()
    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
}

/** Faz uma verificação agora (ao seguir um autor, ao abrir o app, ou pelo botão das configurações). */
internal fun checkNewContentNow(context: Context) {
    val request = OneTimeWorkRequestBuilder<NewContentWorker>()
        .setConstraints(networkConstraint)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, request)
}

// ---------------------------------------------------------------------------
// Permissão de notificação (Android 13+)
// ---------------------------------------------------------------------------

/**
 * Devolve uma função que pede a permissão POST_NOTIFICATIONS quando ela ainda falta.
 * Com force = false pergunta só uma vez (ao favoritar/seguir); com force = true sempre tenta
 * (botão das configurações).
 */
@Composable
internal fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit = {}): (Boolean) -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        currentOnResult(granted)
    }
    return remember(context, launcher) {
        val request: (Boolean) -> Unit = { force ->
            if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed(context)) {
                val prefs = notifyPrefs(context)
                if (force || !prefs.getBoolean(KEY_PERMISSION_ASKED, false)) {
                    prefs.edit().putBoolean(KEY_PERMISSION_ASKED, true).apply()
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        request
    }
}

// ---------------------------------------------------------------------------
// Tela: Configurações → Notificações
// ---------------------------------------------------------------------------

@Composable
internal fun SettingsNotificationsScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit
) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(notificationsAllowed(context)) }
    var followed by remember { mutableStateOf(loadFollowedAuthors(context)) }
    var checkMessage by remember { mutableStateOf<String?>(null) }
    val requestPermission = rememberNotificationPermissionRequest { allowed = notificationsAllowed(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Notificações", color = PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)

        Card(
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (allowed) "Permissão concedida ✓" else "Permissão de notificação desligada",
                    color = if (allowed) SuccessColor else WarnColor,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (allowed) "O app pode avisar sobre capítulos novos."
                    else "Sem ela, o app não consegue avisar quando sair capítulo novo.",
                    color = SecondaryText,
                    fontSize = 12.sp
                )
                if (!allowed) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= 33) requestPermission(true)
                                else allowed = notificationsAllowed(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)
                        ) { Text("Permitir") }
                        OutlinedButton(onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        }) { Text("Ajustes do sistema", color = Accent) }
                    }
                }
            }
        }

        Text(
            if (isPushActive(context)) "Aviso instantâneo (push): ativo ✓"
            else "Aviso instantâneo (push): inativo. Entre na conta para ativar; enquanto isso o app confere de hora em hora.",
            color = if (isPushActive(context)) SuccessColor else SecondaryText,
            fontSize = 12.sp
        )
        NotificationSwitchCard(
            title = "Capítulos novos de favoritas",
            description = "Avisa quando uma história favoritada (e baixada) recebe capítulos novos.",
            checked = settings.notifyFavorites,
            onCheckedChange = { onSettingsChange(settings.copy(notifyFavorites = it)) }
        )
        NotificationSwitchCard(
            title = "Autores que eu sigo",
            description = "Avisa quando um autor seguido publica uma história ou um capítulo novo.",
            checked = settings.notifyFollowedAuthors,
            onCheckedChange = { onSettingsChange(settings.copy(notifyFollowedAuthors = it)) }
        )

        Text("Autores que você segue", color = PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (followed.isEmpty()) {
            Text(
                "Você ainda não segue ninguém. Abra o perfil de um autor e toque em “Seguir”.",
                color = SecondaryText,
                fontSize = 13.sp
            )
        }
        followed.forEach { author ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardColor),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                ) {
                    Text(author.name, color = PrimaryText, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        unfollowAuthor(context, author.ownerId)
                        followed = loadFollowedAuthors(context)
                    }) { Text("Deixar de seguir", color = DangerColor, fontSize = 12.sp) }
                }
            }
        }

        OutlinedButton(
            onClick = {
                scheduleNewContentChecks(context)
                checkNewContentNow(context)
                checkMessage = "Verificação iniciada. Se houver novidades, você será avisado em instantes."
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Verificar novidades agora", color = Accent) }
        checkMessage?.let { Text(it, color = SecondaryText, fontSize = 12.sp) }
        Text(
            "O app confere o catálogo online cerca de uma vez por hora quando há internet. " +
                "O Android pode atrasar a verificação para poupar bateria.",
            color = SecondaryText,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun NotificationSwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = PrimaryText, fontWeight = FontWeight.Medium)
                Text(description, color = SecondaryText, fontSize = 12.sp)
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = OnAccent)
            )
        }
    }
}
