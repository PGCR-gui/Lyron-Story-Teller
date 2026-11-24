package gui.pgcr.storyteller

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Atualizador do app: lê as Releases do GitHub e baixa/instala o APK.
 * Fonte: https://github.com/PGCR-gui/Lyron-Story-Teller
 *
 * O Android só aceita a atualização se o APK da release tiver sido assinado com a mesma chave do app instalado.
 */
internal const val UPDATE_REPO_OWNER = "PGCR-gui"
internal const val UPDATE_REPO_NAME = "Lyron-Story-Teller"
internal const val UPDATE_REPO_URL = "https://github.com/$UPDATE_REPO_OWNER/$UPDATE_REPO_NAME"
internal const val MUSIC_PLAYER_URL = "https://github.com/PGCR-gui/Lyron-Music-Player"
internal const val FLATICON_MAGNIFIC_URL = "https://www.flaticon.com/br/autores/magnific"

/** Canal escolhido pela pessoa: só versões estáveis (Latest) ou também pré-lançamentos. */
internal enum class UpdateChannel(val label: String) {
    LATEST("Latest"),
    PRE_RELEASE("Pre-release")
}

private const val UPDATER_PREFS = "updater_prefs"

internal fun loadUpdateChannel(context: Context): UpdateChannel {
    val raw = context.getSharedPreferences(UPDATER_PREFS, Context.MODE_PRIVATE).getString("channel", null)
    return runCatching { UpdateChannel.valueOf(raw ?: "") }.getOrDefault(UpdateChannel.LATEST)
}

internal fun saveUpdateChannel(context: Context, channel: UpdateChannel) {
    context.getSharedPreferences(UPDATER_PREFS, Context.MODE_PRIVATE).edit().putString("channel", channel.name).apply()
}

internal data class AppRelease(
    val tag: String,
    val name: String,
    val prerelease: Boolean,
    val notes: String,
    val publishedAtMillis: Long?,
    val pageUrl: String,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long,
    /** SHA-256 em hexadecimal quando o GitHub informa o digest do arquivo. */
    val sha256: String?
)

internal fun installedVersionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

private fun installedUpdateTime(context: Context): Long =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime }.getOrDefault(0L)

private fun openGithub(path: String): HttpURLConnection {
    val conn = URL(path).openConnection() as HttpURLConnection
    conn.connectTimeout = 10_000
    conn.readTimeout = 20_000
    conn.setRequestProperty("Accept", "application/vnd.github+json")
    conn.setRequestProperty("User-Agent", "LyronStoryTeller-Updater")
    return conn
}

/** Lista as releases publicadas (a mais nova primeiro) que tenham um APK anexado. Rascunhos ficam de fora. */
internal fun fetchReleases(): List<AppRelease> {
    val conn = openGithub("https://api.github.com/repos/$UPDATE_REPO_OWNER/$UPDATE_REPO_NAME/releases?per_page=30")
    try {
        val code = conn.responseCode
        if (code == 403 || code == 429) error("Limite de consultas do GitHub atingido. Tente de novo em alguns minutos.")
        if (code != 200) error("O GitHub respondeu com erro $code.")
        val array = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
        val result = mutableListOf<AppRelease>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            if (item.optBoolean("draft", false)) continue
            val assets = item.optJSONArray("assets") ?: continue
            val apks = (0 until assets.length()).map { assets.getJSONObject(it) }
                .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
            // Prefere um APK que não seja de debug.
            val asset = apks.firstOrNull { !it.optString("name").contains("debug", ignoreCase = true) }
                ?: apks.firstOrNull() ?: continue
            val tag = item.optString("tag_name")
            result += AppRelease(
                tag = tag,
                name = item.optString("name").ifBlank { tag },
                prerelease = item.optBoolean("prerelease", false),
                notes = item.optString("body").trim(),
                publishedAtMillis = runCatching { java.time.Instant.parse(item.optString("published_at")).toEpochMilli() }.getOrNull(),
                pageUrl = item.optString("html_url").ifBlank { "$UPDATE_REPO_URL/releases" },
                apkName = asset.optString("name"),
                apkUrl = asset.optString("browser_download_url"),
                apkSize = asset.optLong("size", 0L),
                sha256 = asset.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase()
            )
        }
        return result
    } finally {
        conn.disconnect()
    }
}

/** Latest = a release estável mais nova. Pre-release = a mais nova de todas (estável ou não). */
internal fun pickRelease(releases: List<AppRelease>, channel: UpdateChannel): AppRelease? = when (channel) {
    UpdateChannel.LATEST -> releases.firstOrNull { !it.prerelease }
    UpdateChannel.PRE_RELEASE -> releases.firstOrNull()
}

private fun versionNumbers(raw: String): List<Int>? =
    Regex("""\d+(?:\.\d+)*""").find(raw)?.value?.split('.')?.map { it.toIntOrNull() ?: 0 }

/**
 * A release é mais nova que o app instalado?
 * Compara os números da versão (v0.10 > 0.09). Se forem iguais (ex.: dois pré-lançamentos da mesma versão),
 * considera nova a release publicada depois da última vez que o app foi instalado/atualizado.
 */
internal fun isNewerThanInstalled(context: Context, release: AppRelease): Boolean {
    val remote = versionNumbers(release.tag) ?: versionNumbers(release.name)
    val local = versionNumbers(installedVersionName(context))
    if (remote == null || local == null) return true // sem como comparar: deixa a pessoa decidir
    val size = maxOf(remote.size, local.size)
    for (i in 0 until size) {
        val r = remote.getOrElse(i) { 0 }
        val l = local.getOrElse(i) { 0 }
        if (r != l) return r > l
    }
    val published = release.publishedAtMillis ?: return false
    return published > installedUpdateTime(context)
}

private fun updatesDir(context: Context): File = File(context.cacheDir, "updates").apply { mkdirs() }

/** Baixa o APK para o cache do app, confere o SHA-256 (se o GitHub informou) e devolve o arquivo. */
internal suspend fun downloadReleaseApk(
    context: Context,
    release: AppRelease,
    onProgress: (percent: Int?) -> Unit
): File = withContext(Dispatchers.IO) {
    val dir = updatesDir(context)
    dir.listFiles()?.forEach { runCatching { it.delete() } } // apaga APKs de tentativas anteriores
    val target = File(dir, "update.apk")
    val conn = openGithub(release.apkUrl)
    conn.instanceFollowRedirects = true
    conn.setRequestProperty("Accept", "application/octet-stream")
    try {
        if (conn.responseCode != 200) error("Falha ao baixar o APK (erro ${conn.responseCode}).")
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize.takeIf { it > 0 }
        val digest = MessageDigest.getInstance("SHA-256")
        var done = 0L
        var lastPercent = -1
        conn.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    digest.update(buffer, 0, n)
                    done += n
                    val percent = total?.let { (done * 100 / it).toInt().coerceIn(0, 100) }
                    if (percent != lastPercent) {
                        lastPercent = percent ?: -1
                        onProgress(percent)
                    }
                }
            }
        }
        if (release.sha256 != null) {
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(release.sha256, ignoreCase = true)) {
                target.delete()
                error("O arquivo baixado não confere com o checksum da release. Tente novamente.")
            }
        }
        target
    } catch (e: Exception) {
        runCatching { target.delete() }
        throw e
    } finally {
        conn.disconnect()
    }
}

internal enum class InstallStart { STARTED, NEEDS_PERMISSION, FAILED }

/** Abre o instalador do sistema com o APK. Se a permissão de "instalar apps desconhecidos" faltar, abre a tela dela. */
internal fun installApk(context: Context, apk: File): InstallStart {
    return try {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return InstallStart.NEEDS_PERMISSION
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        InstallStart.STARTED
    } catch (_: Exception) {
        InstallStart.FAILED
    }
}

// ---------------------------------------------------------------------------
// Interface
// ---------------------------------------------------------------------------

private sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val release: AppRelease) : UpdateUiState
    data class Downloading(val release: AppRelease, val percent: Int?) : UpdateUiState
    data class Ready(val release: AppRelease, val apk: File) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

private fun formatSize(bytes: Long): String =
    if (bytes <= 0) "" else String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)

@Composable
internal fun UpdaterCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var channel by remember { mutableStateOf(loadUpdateChannel(context)) }
    var state by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var hint by remember { mutableStateOf<String?>(null) }

    fun check() {
        state = UpdateUiState.Checking
        hint = null
        scope.launch {
            state = try {
                val releases = withContext(Dispatchers.IO) { fetchReleases() }
                val pick = pickRelease(releases, channel)
                when {
                    pick == null -> UpdateUiState.Failed(
                        if (channel == UpdateChannel.LATEST) "Nenhuma versão estável com APK foi publicada ainda."
                        else "Nenhuma release com APK foi publicada ainda."
                    )
                    isNewerThanInstalled(context, pick) -> UpdateUiState.Available(pick)
                    else -> UpdateUiState.UpToDate
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateUiState.Failed(e.message ?: "Não foi possível consultar o GitHub. Verifique a conexão.")
            }
        }
    }

    fun download(release: AppRelease) {
        state = UpdateUiState.Downloading(release, 0)
        hint = null
        scope.launch {
            state = try {
                val apk = downloadReleaseApk(context, release) { p ->
                    state = UpdateUiState.Downloading(release, p)
                }
                UpdateUiState.Ready(release, apk)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateUiState.Failed(e.message ?: "Falha ao baixar a atualização.")
            }
        }
    }

    fun install(release: AppRelease, apk: File) {
        when (installApk(context, apk)) {
            InstallStart.STARTED -> hint = null
            InstallStart.NEEDS_PERMISSION ->
                hint = "Permita instalar apps desta fonte para o Lyron Story Teller e toque em Instalar de novo."
            InstallStart.FAILED -> {
                hint = null
                state = UpdateUiState.Failed("Não foi possível abrir o instalador. Baixe o APK pela página da release.")
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Atualizações", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Versão instalada: ${installedVersionName(context)}",
                color = PrimaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text("Canal de atualização", color = SecondaryText, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UpdateChannel.values().forEach { option ->
                    FilterChip(
                        selected = channel == option,
                        enabled = state !is UpdateUiState.Checking && state !is UpdateUiState.Downloading,
                        onClick = {
                            channel = option
                            saveUpdateChannel(context, option)
                            state = UpdateUiState.Idle
                            hint = null
                        },
                        label = { Text(option.label, fontSize = 12.sp) }
                    )
                }
            }
            Text(
                if (channel == UpdateChannel.LATEST) "Só versões estáveis."
                else "Inclui versões de teste (pré-lançamentos), que podem ter erros.",
                color = SecondaryText,
                fontSize = 12.sp
            )

            when (val s = state) {
                UpdateUiState.Idle -> {}
                UpdateUiState.Checking -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
                    Text("Consultando o GitHub…", color = SecondaryText, fontSize = 13.sp)
                }
                UpdateUiState.UpToDate -> Text(
                    "Você já está na versão mais recente do canal ${channel.label}.",
                    color = SuccessColor,
                    fontSize = 13.sp
                )
                is UpdateUiState.Available -> ReleaseInfo(s.release)
                is UpdateUiState.Downloading -> {
                    ReleaseInfo(s.release)
                    if (s.percent != null) {
                        LinearProgressIndicator(progress = { s.percent / 100f }, modifier = Modifier.fillMaxWidth(), color = Accent)
                        Text("Baixando… ${s.percent}%", color = SecondaryText, fontSize = 12.sp)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent)
                        Text("Baixando…", color = SecondaryText, fontSize = 12.sp)
                    }
                }
                is UpdateUiState.Ready -> {
                    ReleaseInfo(s.release)
                    Text("Download concluído.", color = SuccessColor, fontSize = 13.sp)
                }
                is UpdateUiState.Failed -> Text(s.message, color = DangerColor, fontSize = 13.sp)
            }
            hint?.let { Text(it, color = WarnColor, fontSize = 12.sp) }

            when (val s = state) {
                is UpdateUiState.Available -> Button(
                    onClick = { download(s.release) },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Baixar ${s.release.tag}") }
                is UpdateUiState.Ready -> Button(
                    onClick = { install(s.release, s.apk) },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Instalar") }
                else -> OutlinedButton(
                    onClick = { check() },
                    enabled = state !is UpdateUiState.Checking && state !is UpdateUiState.Downloading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Verificar atualizações", color = Accent) }
            }
            Text(
                "Código-fonte e releases no GitHub",
                color = Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$UPDATE_REPO_URL/releases")))
                    }
                }
            )
        }
    }
}

@Composable
private fun ReleaseInfo(release: AppRelease) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            buildString {
                append("Nova versão: ${release.tag}")
                if (release.prerelease) append(" (pré-lançamento)")
                val size = formatSize(release.apkSize)
                if (size.isNotEmpty()) append(" · $size")
            },
            color = PrimaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (release.notes.isNotBlank()) {
            Text(
                release.notes.take(700) + if (release.notes.length > 700) "…" else "",
                color = SecondaryText,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}
