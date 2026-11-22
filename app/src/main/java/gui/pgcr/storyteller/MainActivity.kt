package gui.pgcr.storyteller

import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gui.pgcr.storyteller.ui.theme.MyComposeApplicationTheme
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Abas das telas iniciais, na ordem da barra inferior. */
private val MAIN_TAB_SCREENS = listOf(AppScreen.HOME, AppScreen.EXPLORE, AppScreen.LIBRARY, AppScreen.PROFILE)

private fun mainTabIndex(screen: AppScreen): Int = MAIN_TAB_SCREENS.indexOf(screen)

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    /** Link de confirmação de e-mail do Supabase: gui.pgcr.storyteller://login-callback#access_token=... */
    private fun handleAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (!isAuthCallbackUri(data)) return
        lifecycleScope.launch {
            accountNotice.value = withContext(Dispatchers.IO) { handleAuthCallback(data) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleAuthIntent(intent)
        setContent {
            MyComposeApplicationTheme {
                StoryVerseApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryVerseApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    val userStories = remember {
        mutableStateListOf<Story>().apply {
            addAll(loadUserStories(context))
            cleanupLyrStoryMedia(context, this)
            cleanupInterruptedLyrImports(context)
        }
    }
    var selectedStory by remember {
        mutableStateOf(
            Story(
                title = "",
                author = "",
                description = "",
                rating = "Livre",
                chapters = emptyList()
            )
        )
    }
    var userProfile by remember { mutableStateOf(loadUserProfile(context)) }
    var appSettings by remember {
        val loaded = loadAppSettings(context)
        appThemeState.value = themeColorsFor(loaded)
        mutableStateOf(loaded)
    }
    val favoriteIds = remember {
        mutableStateListOf<String>().apply { addAll(loadFavoriteIds(context)) }
    }
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    fun toggleFavorite(storyId: String) {
        if (storyId in favoriteIds) {
            favoriteIds.remove(storyId)
            saveFavoriteIds(context, favoriteIds.toSet())
            queueRemoteUnfavorite(context, userStories.firstOrNull { it.id == storyId }?.remoteId)
        } else {
            favoriteIds.add(storyId)
            saveFavoriteIds(context, favoriteIds.toSet())
            // Favoritar é o momento natural de pedir permissão para avisar de capítulos novos.
            requestNotificationPermission(false)
            scheduleNewContentChecks(context)
            cancelPendingRemovals(context, remoteStoryId = userStories.firstOrNull { it.id == storyId }?.remoteId)
            requestCloudSync(context) // grava em story_favorites (para o push do servidor)
        }
    }
    var storyPendingExportId by rememberSaveable { mutableStateOf<String?>(null) }
    var storyPendingExportEditable by rememberSaveable { mutableStateOf(true) }
    var showExportModeDialog by remember { mutableStateOf(false) }
    var storyPendingDelete by remember { mutableStateOf<Story?>(null) }
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    // A senha fica só em memória (nunca em rememberSaveable) e é apagada logo após o export.
    var storyPendingExportPassword by remember { mutableStateOf<String?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showImportPasswordDialog by remember { mutableStateOf(false) }
    var importPasswordError by remember { mutableStateOf<String?>(null) }
    var profileBusy by remember { mutableStateOf(false) }
    var profileStatus by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var transferBusy by remember { mutableStateOf(false) }
    var transferMessage by remember { mutableStateOf<String?>(null) }
    var saveToast by remember { mutableStateOf<String?>(null) }
    var editorSaveTrigger by remember { mutableStateOf(0) }
    var isLoggedIn by remember { mutableStateOf(currentUserId() != null) }
    var authEmail by remember { mutableStateOf(currentUserEmail().orEmpty()) }
    var authUserId by remember { mutableStateOf(currentUserId()) }
    var onlineBusy by remember { mutableStateOf(false) }
    var chapterIndex by remember { mutableStateOf(0) }
    var updateNotices by remember { mutableStateOf<List<String>>(emptyList()) }
    var viewingAuthorOwnerId by remember { mutableStateOf<String?>(null) }
    var viewingAuthorName by remember { mutableStateOf("") }
    var authorProfileReturnScreen by remember { mutableStateOf(AppScreen.DETAILS) }
    LaunchedEffect(Unit) {
        createNotificationChannels(context)
        scheduleNewContentChecks(context)
    }
    LaunchedEffect(authUserId) { saveMyUserIdForNotifications(context, authUserId) }
    // Logado: registra o aparelho para push e sincroniza favoritas/seguidos com o servidor.
    // Também roda de novo quando os interruptores de notificação mudam.
    LaunchedEffect(authUserId, appSettings.notifyFavorites, appSettings.notifyFollowedAuthors) {
        if (authUserId != null) {
            syncPushRegistration(context)
            requestCloudSync(context)
        }
    }
    fun openAuthorProfile(ownerId: String?, authorName: String, returnTo: AppScreen) {
        viewingAuthorOwnerId = ownerId
        viewingAuthorName = authorName
        authorProfileReturnScreen = returnTo
        currentScreen = AppScreen.AUTHOR_PROFILE
    }
    /** Baixa uma história do catálogo online (usada em Explorar e na Pesquisa). */
    fun importRemoteStory(remote: RemoteStory) {
        if (remote.filePath.isNullOrBlank()) {
            transferMessage = "Esta história online não tem arquivo .lyr."
            return
        }
        onlineBusy = true
        coroutineScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { downloadStoryBytesCached(context, remote) }
                val tmp = java.io.File(context.cacheDir, "online_${remote.id}.lyr")
                tmp.writeBytes(bytes)
                val imported = withContext(Dispatchers.IO) {
                    importStoryFromLyr(context, android.net.Uri.fromFile(tmp))
                }.copy(
                    remoteId = remote.id,
                    remoteUpdatedAt = remote.updatedAt,
                    remoteOwnerId = remote.ownerId,
                    lastUpdated = System.currentTimeMillis()
                )
                tmp.delete()
                // Remove versão antiga da mesma história remota, se houver
                userStories.removeAll { it.remoteId != null && it.remoteId == remote.id }
                userStories.removeAll { it.id == imported.id }
                userStories.add(0, imported)
                saveUserStories(context, userStories)
                selectedStory = imported
                currentScreen = AppScreen.DETAILS
                saveToast = "História baixada"
            } catch (e: Exception) {
                transferMessage = "Falha ao baixar: ${e.message ?: "erro"}"
            } finally {
                onlineBusy = false
            }
        }
    }
    LaunchedEffect(isLoggedIn, currentScreen) {
        if (!isLoggedIn) return@LaunchedEffect
        if (currentScreen != AppScreen.LIBRARY && currentScreen != AppScreen.HOME) return@LaunchedEffect
        val tracked = userStories.filter { !it.remoteId.isNullOrBlank() }
        if (tracked.isEmpty()) return@LaunchedEffect
        runCatching {
            val remoteList = withContext(Dispatchers.IO) { fetchPublicStoriesCached(context, limit = 100) }
            val notices = mutableListOf<String>()
            tracked.forEach { local ->
                val remote = remoteList.firstOrNull { it.id == local.remoteId } ?: return@forEach
                val remoteTs = remote.updatedAt.orEmpty()
                if (remoteTs.isNotBlank() && remoteTs != local.remoteUpdatedAt.orEmpty()) {
                    notices.add("“${local.title}” tem uma atualização online.")
                }
            }
            if (notices.isNotEmpty()) updateNotices = notices
        }
    }

    LaunchedEffect(Unit) {
        sessionStatusFlow().collect { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    isLoggedIn = true
                    authEmail = status.session.user?.email.orEmpty()
                    authUserId = status.session.user?.id
                }
                else -> {
                    isLoggedIn = false
                    authEmail = ""
                    authUserId = null
                }
            }
        }
    }
    // Ao entrar numa conta, traz nome de usuário, apelido, nome completo e foto do servidor.
    LaunchedEffect(authUserId) {
        val uid = authUserId ?: return@LaunchedEffect
        try {
            val remote = withContext(Dispatchers.IO) { fetchMyProfile(uid) }
            if (remote == null) {
                // Conta nova (ou outra conta): ainda não há perfil online.
                if (userProfile.username.isNotEmpty() || userProfile.avatarPath != null) {
                    val cleared = userProfile.copy(username = "", avatarPath = null)
                    userProfile = cleared
                    saveUserProfile(context, cleared)
                }
            } else {
                val remoteProfile = remote.first
                val remotePath = remoteProfile.avatarPath
                var photoUri = userProfile.photoUri
                var avatarPath = userProfile.avatarPath
                if (remotePath == null) {
                    if (avatarPath != null) { photoUri = null; avatarPath = null }
                } else if (remotePath != avatarPath || photoUri == null ||
                    !withContext(Dispatchers.IO) { isImageReadable(context, photoUri!!) }
                ) {
                    val bytes = withContext(Dispatchers.IO) { downloadAvatarBytes(remotePath) }
                    photoUri = withContext(Dispatchers.IO) { storeAvatarLocally(context, bytes) }
                    avatarPath = remotePath
                }
                val merged = userProfile.copy(
                    fullName = remote.second,
                    nickname = remoteProfile.nickname,
                    username = remoteProfile.username,
                    photoUri = photoUri,
                    avatarPath = avatarPath,
                    // Só traz a bio do servidor se este aparelho ainda não tiver nenhuma.
                    bio = userProfile.bio.ifBlank { remoteProfile.bio }
                )
                userProfile = merged
                saveUserProfile(context, merged)
                withContext(Dispatchers.IO) { pruneOwnAvatars(context, merged.photoUri) }
            }
        } catch (_: Exception) {
            // Sem rede ou tabelas ainda não criadas: mantém o perfil local.
        }
    }
    // Login e criação de conta compartilham o mesmo fluxo; ao terminar, volta para a tela de perfil.
    fun runAuth(email: String, password: String, isSignUp: Boolean, username: String = "", nickname: String = "") {
        onlineBusy = true
        coroutineScope.launch {
            try {
                if (isSignUp && username.isNotBlank()) {
                    // Confere o nome de usuário antes de criar (se não der para conferir, o banco garante a unicidade).
                    val available = runCatching {
                        withContext(Dispatchers.IO) { isUsernameAvailable(username, null) }
                    }.getOrNull()
                    if (available == false) {
                        accountNotice.value = "Esse nome de usuário já está em uso. Escolha outro."
                        return@launch
                    }
                }
                withContext(Dispatchers.IO) {
                    if (isSignUp) signUpEmail(email, password, username, nickname) else signInEmail(email, password)
                }
                if (isSignUp) {
                    userProfile = userProfile.copy(nickname = nickname, username = username)
                    saveUserProfile(context, userProfile)
                }
                isLoggedIn = currentUserId() != null
                authEmail = currentUserEmail().orEmpty()
                authUserId = currentUserId()
                if (isSignUp && !isLoggedIn) {
                    accountNotice.value = "Conta criada! Foi enviado um e-mail de confirmação para $email. " +
                        "Abra o link no próprio celular para voltar ao app já conectado."
                } else {
                    saveToast = if (isSignUp) "Conta criada" else "Login feito"
                }
                if (isSignUp) currentScreen = AppScreen.PROFILE
            } catch (e: Exception) {
                accountNotice.value = friendlyAuthError(e, isSignUp)
            } finally {
                onlineBusy = false
            }
        }
    }
    fun saveProfile(draft: UserProfile) {
        profileBusy = true
        profileStatus = null
        coroutineScope.launch {
            var uploadedPath: String? = null
            try {
                val uid = currentUserId()
                val previous = userProfile
                if (uid == null) {
                    val keepAvatar = if (draft.photoUri == previous.photoUri) previous.avatarPath else null
                    val saved = draft.copy(username = "", avatarPath = keepAvatar)
                    userProfile = saved
                    saveUserProfile(context, saved)
                    withContext(Dispatchers.IO) { pruneOwnAvatars(context, saved.photoUri) }
                    profileStatus = "Perfil salvo neste aparelho." to false
                } else {
                    val username = draft.username.trim().lowercase()
                    if (!USERNAME_REGEX.matches(username)) {
                        profileStatus = "Escolha um nome de usuário válido (3 a 20 caracteres: a–z, 0–9 e _)." to true
                        return@launch
                    }
                    val available = withContext(Dispatchers.IO) { isUsernameAvailable(username, uid) }
                    if (!available) {
                        profileStatus = "Esse nome de usuário já está em uso. Escolha outro." to true
                        return@launch
                    }
                    var avatarPath = previous.avatarPath
                    var oldAvatarPath: String? = null
                    val photoUri = draft.photoUri
                    if (photoUri == null) {
                        oldAvatarPath = avatarPath
                        avatarPath = null
                    } else if (photoUri != previous.photoUri || avatarPath == null) {
                        val bytes = withContext(Dispatchers.IO) { avatarJpegBytes(context, photoUri) }
                            ?: throw IllegalStateException("Não foi possível ler a imagem escolhida.")
                        val newPath = "$uid/avatar_${System.currentTimeMillis()}.jpg"
                        withContext(Dispatchers.IO) { uploadAvatarFile(newPath, bytes) }
                        uploadedPath = newPath
                        oldAvatarPath = avatarPath
                        avatarPath = newPath
                    }
                    withContext(Dispatchers.IO) {
                        saveRemoteProfile(
                            RemoteProfile(
                            id = uid,
                            username = username,
                            nickname = draft.nickname.trim(),
                            avatarPath = avatarPath,
                            bio = draft.bio.trim(),
                            publicBio = draft.publicBio,
                            publicWritten = draft.publicWritten,
                            publicFavorites = draft.publicFavorites
                        ),
                            draft.fullName.trim()
                        )
                    }
                    oldAvatarPath?.let { old -> runCatching { withContext(Dispatchers.IO) { deleteAvatarFile(old) } } }
                    val saved = draft.copy(username = username, avatarPath = avatarPath)
                    userProfile = saved
                    saveUserProfile(context, saved)
                    withContext(Dispatchers.IO) { pruneOwnAvatars(context, saved.photoUri) }
                    profileStatus = "Perfil salvo online." to false
                    saveToast = "Perfil salvo"
                }
            } catch (e: Exception) {
                uploadedPath?.let { path -> runCatching { withContext(Dispatchers.IO) { deleteAvatarFile(path) } } }
                val raw = e.message.orEmpty()
                profileStatus = if (raw.contains("23505") || raw.contains("duplicate key", ignoreCase = true)) {
                    "Esse nome de usuário acabou de ser usado por outra pessoa. Escolha outro." to true
                } else {
                    "Falha ao salvar o perfil: ${raw.ifBlank { "erro de rede" }}" to true
                }
            } finally {
                profileBusy = false
            }
        }
    }
    val readingHistory = remember { mutableStateListOf<Int>() }
    var showStoryEnding by remember { mutableStateOf(false) }
    val storyStats = remember { mutableStateMapOf<String, StoryStats>().apply { putAll(loadStoryStats(context)) } }
    fun deleteStoryFromLibrary(story: Story) {
        userStories.removeAll { it.id == story.id }
        if (story.id in favoriteIds) {
            favoriteIds.remove(story.id)
            saveFavoriteIds(context, favoriteIds.toSet())
            queueRemoteUnfavorite(context, story.remoteId)
        }
        if (storyStats.remove(story.id) != null) saveStoryStats(context, storyStats.toMap())
        val remaining = userStories.toList()
        coroutineScope.launch(Dispatchers.IO) {
            saveUserStories(context, remaining)
            cleanupLyrStoryMedia(context, remaining) // apaga fotos/capas que ninguém mais usa
        }
        storyPendingDelete = null
        currentScreen = AppScreen.LIBRARY
        saveToast = "História excluída"
    }
    fun updateStats(story: Story, update: (StoryStats) -> StoryStats) {
        val updated = update(storyStats[story.id] ?: StoryStats())
        storyStats[story.id] = updated
        saveStoryStats(context, storyStats.toMap())
    }
    fun recordChapterVisit(story: Story, chapter: Chapter, completed: Boolean = false) {
        updateStats(story) { current ->
            val visits = current.chapterVisits.toMutableMap().apply { this[chapter.id] = (this[chapter.id] ?: 0) + 1 }
            val endings = current.endingVisits.toMutableMap()
            if (completed) endings["chapter:${chapter.id}"] = (endings["chapter:${chapter.id}"] ?: 0) + 1
            current.copy(
                chapterVisits = visits,
                endingVisits = endings,
                completedReadings = current.completedReadings + if (completed) 1 else 0
            )
        }
    }
    fun recordTransition(story: Story, pathKey: String, destination: Chapter?, endingKey: String? = null) {
        updateStats(story) { current ->
            val paths = current.pathTraversals.toMutableMap().apply { this[pathKey] = (this[pathKey] ?: 0) + 1 }
            val chapters = current.chapterVisits.toMutableMap()
            if (destination != null) chapters[destination.id] = (chapters[destination.id] ?: 0) + 1
            val endings = current.endingVisits.toMutableMap()
            if (endingKey != null) endings[endingKey] = (endings[endingKey] ?: 0) + 1
            current.copy(
                chapterVisits = chapters,
                pathTraversals = paths,
                endingVisits = endings,
                completedReadings = current.completedReadings + if (endingKey != null) 1 else 0
            )
        }
    }
    fun startLyrImport(fileUri: Uri, password: String?) {
        coroutineScope.launch {
            transferBusy = true
            try {
                val imported = withContext(Dispatchers.IO) { importStoryFromLyr(context, fileUri, password) }
                pendingImportUri = null
                showImportPasswordDialog = false
                importPasswordError = null
                userStories.add(imported)
                val persistedStories = userStories.toList()
                withContext(Dispatchers.IO) { saveUserStories(context, persistedStories) }
                selectedStory = imported
                currentScreen = AppScreen.DETAILS
                transferMessage = if (imported.editable) {
                    "“${imported.title}” foi importada como história autoral (editável), com fotos e capítulos."
                } else {
                    "“${imported.title}” foi importada em modo somente leitura para sua biblioteca."
                }
            } catch (error: LyrPasswordException) {
                importPasswordError = error.message
                showImportPasswordDialog = true
            } catch (error: Exception) {
                pendingImportUri = null
                showImportPasswordDialog = false
                transferMessage = "Não foi possível importar o arquivo .lyr: ${error.message ?: "arquivo inválido"}"
            } finally {
                transferBusy = false
            }
        }
    }
    val importStoryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { fileUri ->
        if (fileUri != null) coroutineScope.launch {
            val needsPassword = try {
                withContext(Dispatchers.IO) { lyrRequiresPassword(context, fileUri) }
            } catch (error: Exception) {
                transferMessage = "Não foi possível importar o arquivo .lyr: ${error.message ?: "arquivo inválido"}"
                return@launch
            }
            if (needsPassword) {
                pendingImportUri = fileUri
                importPasswordError = null
                showImportPasswordDialog = true
            } else {
                startLyrImport(fileUri, null)
            }
        }
    }
    val exportStoryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { destination ->
        val pendingId = storyPendingExportId
        val exportEditable = storyPendingExportEditable
        val exportPassword = storyPendingExportPassword
        storyPendingExportPassword = null
        storyPendingExportId = null
        val story = pendingId?.let { id -> (userStories + sampleStories).firstOrNull { it.id == id } }
        if (destination != null && story != null) coroutineScope.launch {
            transferBusy = true
            try {
                if (exportEditable && exportPassword.isNullOrEmpty()) {
                    throw IllegalStateException("A senha do arquivo editável foi perdida. Exporte novamente.")
                }
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(destination)
                        ?: throw IllegalStateException("O Android não abriu o destino escolhido.")
                    exportStoryToLyr(context, story, output, editable = exportEditable, password = exportPassword)
                }
                val modeLabel = if (exportEditable) "editável, protegido por senha" else "somente leitura"
                transferMessage = "“${story.title}” foi exportada como .lyr ($modeLabel)."
            } catch (error: Exception) {
                transferMessage = "Não foi possível exportar a história: ${error.message ?: "erro ao criar o arquivo"}. Se um arquivo incompleto tiver sido criado, remova-o no local escolhido."
            } finally {
                transferBusy = false
            }
        }
    }
    var fontSize by remember { mutableStateOf(appSettings.defaultReaderFontSize) }
    var readerMenuTrigger by remember { mutableStateOf(0) }
    var pendingReaderPageIndex by remember { mutableStateOf(0) }
    var editingStory by remember { mutableStateOf<Story?>(null) }
    var editingChapterIndex by remember { mutableStateOf<Int?>(null) }

    val isMainScreen = currentScreen == AppScreen.HOME ||
        currentScreen == AppScreen.EXPLORE ||
        currentScreen == AppScreen.LIBRARY ||
        currentScreen == AppScreen.PROFILE

    BackHandler(enabled = !isMainScreen) {
        if (currentScreen == AppScreen.EDITOR && editingChapterIndex != null) {
            editingChapterIndex = null
        } else {
            currentScreen = when (currentScreen) {
                AppScreen.READER, AppScreen.STATS -> AppScreen.DETAILS
                AppScreen.EDITOR -> AppScreen.LIBRARY
                AppScreen.SETTINGS, AppScreen.MANAGE_POSTED, AppScreen.CREATE_ACCOUNT -> AppScreen.PROFILE
                AppScreen.AUTHOR_PROFILE -> authorProfileReturnScreen
                AppScreen.SETTINGS_PROFILE,
                AppScreen.SETTINGS_APPEARANCE,
                AppScreen.SETTINGS_READER,
                AppScreen.SETTINGS_NOTIFICATIONS,
                AppScreen.SETTINGS_CONTENT,
                AppScreen.SETTINGS_ABOUT -> AppScreen.SETTINGS
                else -> AppScreen.HOME
            }
        }
    }

    val selectedTab = mainTabIndex(currentScreen)

    Scaffold(
        containerColor = Background,
        topBar = {
            if (!(currentScreen == AppScreen.EDITOR && editingChapterIndex != null)) CenterAlignedTopAppBar(
                title = {
                    if (currentScreen == AppScreen.READER && selectedStory.chapters.isNotEmpty()) {
                        val ch = selectedStory.chapters.getOrNull(chapterIndex)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                selectedStory.title,
                                color = SecondaryText,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                (ch?.title?.takeIf { it.isNotBlank() } ?: "Capítulo ${chapterIndex + 1}"),
                                color = PrimaryText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Text(
                            text = when {
                                currentScreen == AppScreen.STATS -> "Estatísticas"
                                currentScreen == AppScreen.SETTINGS -> "Configurações"
                                currentScreen == AppScreen.SETTINGS_PROFILE -> "Editar perfil"
                                currentScreen == AppScreen.SETTINGS_APPEARANCE -> "Aparência"
                                currentScreen == AppScreen.SETTINGS_READER -> "Leitor de histórias"
                                currentScreen == AppScreen.SETTINGS_NOTIFICATIONS -> "Notificações"
                                currentScreen == AppScreen.SETTINGS_CONTENT -> "Conteúdo e exportação"
                                currentScreen == AppScreen.SETTINGS_ABOUT -> "Sobre o app"
                                currentScreen == AppScreen.MANAGE_POSTED -> "Histórias postadas"
                                currentScreen == AppScreen.CREATE_ACCOUNT -> "Criar conta"
                                currentScreen == AppScreen.AUTHOR_PROFILE -> "Perfil do autor"
                                currentScreen == AppScreen.EDITOR && editingChapterIndex != null -> "Editar capítulo"
                                currentScreen == AppScreen.EDITOR -> "Editor de histórias"
                                else -> "Lyron Story Teller"
                            },
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                },
                navigationIcon = {
                    if (!isMainScreen) {
                        TextButton(onClick = {
                            if (currentScreen == AppScreen.EDITOR && editingChapterIndex != null) {
                                editingChapterIndex = null
                            } else {
                                currentScreen = when (currentScreen) {
                                    AppScreen.READER, AppScreen.STATS -> AppScreen.DETAILS
                                    AppScreen.EDITOR -> AppScreen.LIBRARY
                                    AppScreen.SETTINGS, AppScreen.MANAGE_POSTED, AppScreen.CREATE_ACCOUNT -> AppScreen.PROFILE
                                    AppScreen.AUTHOR_PROFILE -> authorProfileReturnScreen
                                    AppScreen.SETTINGS_PROFILE,
                                    AppScreen.SETTINGS_APPEARANCE,
                                    AppScreen.SETTINGS_READER,
                                    AppScreen.SETTINGS_NOTIFICATIONS,
                                    AppScreen.SETTINGS_CONTENT,
                                    AppScreen.SETTINGS_ABOUT -> AppScreen.SETTINGS
                                    else -> AppScreen.HOME
                                }
                            }
                        }) {
                            Text("‹", color = Accent, fontSize = 30.sp)
                        }
                    }
                },
                actions = {
                    if (currentScreen == AppScreen.EDITOR) {
                        TextButton(onClick = { editorSaveTrigger += 1 }) {
                            Text("Salvar", color = Accent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (currentScreen == AppScreen.READER) {
                        Text(
                            "${chapterIndex + 1}/${selectedStory.chapters.size.coerceAtLeast(1)}",
                            color = SecondaryText,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        TextButton(onClick = { readerMenuTrigger += 1 }) {
                            Text("Aa", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Background,
                    titleContentColor = PrimaryText
                )
            )
        },
        floatingActionButton = {
            if (currentScreen == AppScreen.LIBRARY) {
                FloatingActionButton(
                    onClick = { editingStory = null; editingChapterIndex = null; currentScreen = AppScreen.EDITOR },
                    containerColor = Accent,
                    contentColor = OnAccent
                ) { Text("+", fontSize = 28.sp) }
            }
        },
        bottomBar = {
            if (isMainScreen) {
                NavigationBar(containerColor = CardColor, contentColor = PrimaryText) {
                    val tabs = listOf("Explorar", "Pesquisar", "Biblioteca", "Perfil")
                    val symbols = listOf("꧂", "⌕", "✍", "𖠌")
                    val destinations = listOf(
                        AppScreen.HOME,
                        AppScreen.EXPLORE,
                        AppScreen.LIBRARY,
                        AppScreen.PROFILE
                    )
                    tabs.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { currentScreen = destinations[index] },
                            icon = { Text(symbols[index], fontSize = 23.sp) },
                            label = { Text(title, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Accent,
                                selectedTextColor = Accent,
                                indicatorColor = Accent.copy(alpha = 0.22f),
                                unselectedIconColor = SecondaryText,
                                unselectedTextColor = SecondaryText
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                // Deslizar para os lados nas telas iniciais troca de aba (esquerda = próxima, direita = anterior).
                .then(
                    if (isMainScreen) Modifier.pointerInput(selectedTab) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragCancel = { total = 0f },
                            onDragEnd = {
                                val threshold = 72.dp.toPx()
                                val target = when {
                                    total <= -threshold -> selectedTab + 1
                                    total >= threshold -> selectedTab - 1
                                    else -> selectedTab
                                }
                                if (target != selectedTab && target in MAIN_TAB_SCREENS.indices) {
                                    currentScreen = MAIN_TAB_SCREENS[target]
                                }
                                total = 0f
                            },
                            onHorizontalDrag = { _, dragAmount -> total += dragAmount }
                        )
                    } else Modifier
                )
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    val from = mainTabIndex(initialState)
                    val to = mainTabIndex(targetState)
                    if (from >= 0 && to >= 0 && from != to) {
                        // Entre abas: a nova tela entra do lado para onde a pessoa deslizou.
                        val dir = if (to > from) 1 else -1
                        (fadeIn() + slideInHorizontally { dir * it / 3 }) togetherWith
                            (fadeOut() + slideOutHorizontally { -dir * it / 3 })
                    } else {
                        (fadeIn() + slideInHorizontally { it / 8 }) togetherWith (fadeOut() + slideOutHorizontally { -it / 8 })
                    }
                },
                label = "screenTransition"
            ) { screen ->
            when (screen) {
                AppScreen.HOME -> OnlineExploreScreen(
                    isLoggedIn = isLoggedIn,
                    busy = onlineBusy,
                    onImportRemote = { importRemoteStory(it) },
                    onNeedLogin = { currentScreen = AppScreen.PROFILE },
                    onToast = { saveToast = it },
                    onMessage = { transferMessage = it },
                    onAuthorClick = { ownerId, authorName ->
                        openAuthorProfile(ownerId, authorName, AppScreen.HOME)
                    }
                )
                AppScreen.EXPLORE -> ExploreScreen(
                    allStories = (userStories + if (appSettings.showSampleStories) sampleStories else emptyList())
                        .distinctBy { it.id },
                    isLoggedIn = isLoggedIn,
                    busy = onlineBusy,
                    onStoryClick = {
                        selectedStory = it
                        currentScreen = AppScreen.DETAILS
                    },
                    onImportRemote = { importRemoteStory(it) },
                    onAuthorClick = { ownerId, authorName ->
                        openAuthorProfile(ownerId, authorName, AppScreen.EXPLORE)
                    }
                )
                AppScreen.LIBRARY -> LibraryScreen(
                    userWritten = userStories.filter { it.editable }.toList(),
                    favorites = (userStories + sampleStories).filter { it.id in favoriteIds }.distinctBy { it.id },
                    others = userStories.filter { !it.editable && it.id !in favoriteIds },
                    transferBusy = transferBusy,
                    onImportClick = { importStoryLauncher.launch(arrayOf("*/*")) },
                    onStoryClick = {
                        selectedStory = it
                        currentScreen = AppScreen.DETAILS
                    }
                )
                AppScreen.PROFILE -> ProfileScreen(
                    profile = userProfile,
                    isLoggedIn = isLoggedIn,
                    authEmail = authEmail,
                    writtenCount = userStories.count { it.editable },
                    favoriteCount = favoriteIds.size,
                    onOpenSettings = { currentScreen = AppScreen.SETTINGS },
                    onLogin = { email, password -> runAuth(email, password, isSignUp = false) },
                    onCreateAccount = { currentScreen = AppScreen.CREATE_ACCOUNT },
                    onLogout = {
                        coroutineScope.launch {
                            runCatching { withContext(Dispatchers.IO) { runCatching { unregisterPushToken(context) }; signOut() } }
                            isLoggedIn = false
                            authEmail = ""
                            authUserId = null
                            val cleared = userProfile.copy(username = "", avatarPath = null)
                            userProfile = cleared
                            saveUserProfile(context, cleared)
                        }
                    },
                    authBusy = onlineBusy,
                    onManagePosted = { currentScreen = AppScreen.MANAGE_POSTED }
                )
                AppScreen.CREATE_ACCOUNT -> CreateAccountScreen(
                    authBusy = onlineBusy,
                    onCheckUsername = { name ->
                        runCatching {
                            withContext(Dispatchers.IO) { isUsernameAvailable(name, null) }
                        }.getOrNull()
                    },
                    onCreateAccount = { email, password, username, nickname ->
                        runAuth(email, password, isSignUp = true, username = username, nickname = nickname)
                    },
                    onHaveAccount = { currentScreen = AppScreen.PROFILE }
                )
                AppScreen.MANAGE_POSTED -> ManagePostedScreen(
                    isLoggedIn = isLoggedIn,
                    busy = onlineBusy,
                    onBack = { currentScreen = AppScreen.PROFILE },
                    onToast = { saveToast = it }
                )
                AppScreen.SETTINGS -> SettingsHubScreen(
                    profile = userProfile,
                    settings = appSettings,
                    onNavigate = { target -> currentScreen = target }
                )
                AppScreen.SETTINGS_PROFILE -> SettingsProfileScreen(
                    profile = userProfile,
                    isLoggedIn = isLoggedIn,
                    profileBusy = profileBusy,
                    profileStatus = profileStatus,
                    onCheckUsername = { name ->
                        runCatching {
                            withContext(Dispatchers.IO) { isUsernameAvailable(name, currentUserId()) }
                        }.getOrNull()
                    },
                    onSaveProfile = { draft -> saveProfile(draft) }
                )
                AppScreen.SETTINGS_APPEARANCE -> SettingsAppearanceScreen(
                    settings = appSettings,
                    onSettingsChange = { updated ->
                        appSettings = updated
                        appThemeState.value = themeColorsFor(updated)
                        saveAppSettings(context, updated)
                    }
                )
                AppScreen.SETTINGS_READER -> SettingsReaderScreen(
                    settings = appSettings,
                    onSettingsChange = { updated ->
                        appSettings = updated
                        saveAppSettings(context, updated)
                        fontSize = updated.defaultReaderFontSize
                    }
                )
                AppScreen.SETTINGS_NOTIFICATIONS -> SettingsNotificationsScreen(
                    settings = appSettings,
                    onSettingsChange = { updated ->
                        appSettings = updated
                        saveAppSettings(context, updated)
                    }
                )
                AppScreen.SETTINGS_CONTENT -> SettingsContentScreen(
                    settings = appSettings,
                    onSettingsChange = { updated ->
                        appSettings = updated
                        saveAppSettings(context, updated)
                    }
                )
                AppScreen.SETTINGS_ABOUT -> SettingsAboutScreen()
                AppScreen.DETAILS -> StoryDetailsScreen(
                    story = selectedStory,
                    isFavorite = selectedStory.id in favoriteIds,
                    onFavoriteClick = { toggleFavorite(selectedStory.id) },
                    // Quem já alcançou um final (alternativo ou definitivo) não recebe mais "Continuar de onde parou".
                    hasProgress = run {
                        val ended = storyStats[selectedStory.id]?.let { it.endingVisits.isNotEmpty() || it.completedReadings > 0 } == true
                        !ended && loadReadingProgressMap(context)[selectedStory.id]?.let { p ->
                            p.updatedAt > 0L && (p.chapterIndex > 0 || p.pageIndex > 0)
                        } == true
                    },
                    onContinueClick = {
                        val progress = loadReadingProgressMap(context)[selectedStory.id]
                        chapterIndex = (progress?.chapterIndex ?: 0).coerceIn(0, selectedStory.chapters.lastIndex.coerceAtLeast(0))
                        pendingReaderPageIndex = progress?.pageIndex ?: 0
                        selectedStory.chapters.getOrNull(chapterIndex)?.let { ch ->
                            recordChapterVisit(selectedStory, ch, completed = false)
                        }
                        readingHistory.clear()
                        showStoryEnding = false
                        currentScreen = AppScreen.READER
                    },
                    onReadClick = {
                        clearReadingProgress(context, selectedStory.id)
                        chapterIndex = 0
                        pendingReaderPageIndex = 0
                        selectedStory.chapters.firstOrNull()?.let { first ->
                            recordChapterVisit(selectedStory, first, completed = first.isEnding || (!selectedStory.chapters.any { it.choices.isNotEmpty() || it.isEnding } && selectedStory.chapters.size == 1))
                        }
                        readingHistory.clear()
                        showStoryEnding = false
                        currentScreen = AppScreen.READER
                    },
                    unlockedChapterCount = run {
                        val authorCanEdit = userStories.any { it.id == selectedStory.id } && selectedStory.editable
                        if (authorCanEdit || selectedStory.isChoiceStory) {
                            Int.MAX_VALUE
                        } else {
                            // Leitor só abre capítulos que já alcançou lendo (o autor abre todos para revisar).
                            val visited = (storyStats[selectedStory.id] ?: StoryStats()).chapterVisits.keys
                            val farthest = selectedStory.chapters.indexOfLast { it.id in visited }
                            val saved = loadReadingProgressMap(context)[selectedStory.id]?.chapterIndex ?: 0
                            maxOf(farthest, saved, 0) + 1
                        }
                    },
                    onChapterClick = { index ->
                        chapterIndex = index
                        pendingReaderPageIndex = 0
                        val isChoiceStory = selectedStory.isChoiceStory
                        selectedStory.chapters.getOrNull(index)?.let { chapter ->
                            val isStandardFinal = !isChoiceStory && index == selectedStory.chapters.lastIndex
                            recordChapterVisit(selectedStory, chapter, completed = chapter.isEnding || isStandardFinal)
                        }
                        readingHistory.clear()
                        showStoryEnding = false
                        currentScreen = AppScreen.READER
                    },
                    canEdit = userStories.any { it.id == selectedStory.id } && selectedStory.editable,
                    canDelete = userStories.any { it.id == selectedStory.id },
                    onDeleteClick = { storyPendingDelete = selectedStory },
                    onEditClick = { editingStory = selectedStory; editingChapterIndex = null; currentScreen = AppScreen.EDITOR },
                    onExportClick = {
                        storyPendingExportId = selectedStory.id
                        if (!selectedStory.editable) {
                            // Somente leitura nunca vira editável: exporta direto, sem opção nem senha.
                            storyPendingExportEditable = false
                            storyPendingExportPassword = null
                            exportStoryLauncher.launch(storyFileName(selectedStory.title))
                        } else if (appSettings.confirmBeforeExport) {
                            showExportModeDialog = true
                        } else {
                            // Sem confirmação de modo, exporta como editável — e editável sempre pede senha.
                            storyPendingExportEditable = true
                            showExportPasswordDialog = true
                        }
                    },
                    canPublishOnline = isLoggedIn && selectedStory.editable && userStories.any { it.id == selectedStory.id },
                    onPublishOnline = {
                        val uid = currentUserId()
                        if (uid == null) {
                            transferMessage = "Faça login ou crie um Perfil para publicar."
                            return@StoryDetailsScreen
                        }
                        onlineBusy = true
                        coroutineScope.launch {
                            try {
                                val storyId = selectedStory.id.ifBlank { UUID.randomUUID().toString() }
                                val filePath = "$uid/$storyId.lyr"
                                val bytes = withContext(Dispatchers.IO) {
                                    val baos = java.io.ByteArrayOutputStream()
                                    exportStoryToLyr(context, selectedStory, baos, editable = false)
                                    baos.toByteArray()
                                }
                                withContext(Dispatchers.IO) {
                                    uploadStoryFile(filePath, bytes)
                                    var coverPath: String? = null
                                    val coverUri = selectedStory.coverImageUri
                                    if (!coverUri.isNullOrBlank()) {
                                        runCatching {
                                            val coverBytes = withContext(Dispatchers.IO) {
                                                val uri = Uri.parse(coverUri)
                                                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                            }
                                            if (coverBytes != null && coverBytes.isNotEmpty()) {
                                                coverPath = "$uid/$storyId.jpg"
                                                uploadCoverFile(coverPath!!, coverBytes)
                                            }
                                        }
                                    }
                                    val published = publishStoryMetadata(
                                        RemoteStory(
                                            id = storyId,
                                            ownerId = uid,
                                            title = selectedStory.title,
                                            author = selectedStory.author.ifBlank { userProfile.authorName },
                                            description = selectedStory.description,
                                            rating = selectedStory.rating,
                                            status = selectedStory.status,
                                            genres = selectedStory.genres,
                                            tags = selectedStory.tags,
                                            classifications = selectedStory.classifications,
                                            editable = false,
                                            isPublic = true,
                                            filePath = filePath,
                                            coverPath = coverPath,
                                            chapterCount = selectedStory.chapters.count { !it.isDraft },
                                            wordCount = selectedStory.chapters.filter { !it.isDraft }.sumOf { it.body.length }
                                        )
                                    )
                                    // Marca a cópia local com id remoto e timestamp
                                    val idxLocal = userStories.indexOfFirst { it.id == selectedStory.id }
                                    if (idxLocal >= 0) {
                                        userStories[idxLocal] = selectedStory.copy(
                                            remoteId = published.id ?: storyId,
                                            remoteUpdatedAt = published.updatedAt,
                                            remoteOwnerId = uid,
                                            lastUpdated = System.currentTimeMillis()
                                        )
                                        selectedStory = userStories[idxLocal]
                                        saveUserStories(context, userStories)
                                    }
                                }
                                saveToast = "História publicada"
                            } catch (e: Exception) {
                                transferMessage = "Falha ao publicar: ${e.message ?: "erro"}"
                            } finally {
                                onlineBusy = false
                            }
                        }
                    },
                    transferBusy = transferBusy || onlineBusy,
                    onStatsClick = { currentScreen = AppScreen.STATS },
                    stats = storyStats[selectedStory.id] ?: StoryStats(),
                    onAuthorClick = if (selectedStory.hasOnlineAuthor) {
                        {
                            openAuthorProfile(
                                ownerId = selectedStory.remoteOwnerId,
                                authorName = selectedStory.author,
                                returnTo = AppScreen.DETAILS
                            )
                        }
                    } else null
                )
                AppScreen.AUTHOR_PROFILE -> AuthorProfileScreen(
                    ownerId = viewingAuthorOwnerId,
                    authorName = viewingAuthorName,
                    isLoggedIn = isLoggedIn,
                    busy = onlineBusy,
                    onImportRemote = { remote ->
                        if (remote.filePath.isNullOrBlank()) {
                            transferMessage = "Esta história online não tem arquivo .lyr."
                            return@AuthorProfileScreen
                        }
                        onlineBusy = true
                        coroutineScope.launch {
                            try {
                                val bytes = withContext(Dispatchers.IO) { downloadStoryBytes(remote.filePath) }
                                val tmp = java.io.File(context.cacheDir, "online_${remote.id}.lyr")
                                tmp.writeBytes(bytes)
                                val imported = withContext(Dispatchers.IO) {
                                    importStoryFromLyr(context, android.net.Uri.fromFile(tmp))
                                }.copy(
                                    remoteId = remote.id,
                                    remoteUpdatedAt = remote.updatedAt,
                                    remoteOwnerId = remote.ownerId,
                                    lastUpdated = System.currentTimeMillis()
                                )
                                tmp.delete()
                                userStories.removeAll { it.remoteId != null && it.remoteId == remote.id }
                                userStories.removeAll { it.id == imported.id }
                                userStories.add(0, imported)
                                saveUserStories(context, userStories)
                                selectedStory = imported
                                currentScreen = AppScreen.DETAILS
                                saveToast = "História baixada"
                            } catch (e: Exception) {
                                transferMessage = "Falha ao baixar: ${e.message ?: "erro"}"
                            } finally {
                                onlineBusy = false
                            }
                        }
                    },
                    onNeedLogin = { currentScreen = AppScreen.PROFILE },
                    onToast = { saveToast = it },
                    onMessage = { transferMessage = it }
                )
                AppScreen.STATS -> StoryStatsScreen(
                    story = selectedStory,
                    stats = storyStats[selectedStory.id] ?: StoryStats()
                )
                AppScreen.EDITOR -> StoryEditorScreen(
                    initialStory = editingStory,
                    activeChapterIndex = editingChapterIndex,
                    defaultAuthor = userProfile.authorName,
                    saveTrigger = editorSaveTrigger,
                    onChapterSelect = { editingChapterIndex = it },
                    onCancel = { editingChapterIndex = null; currentScreen = AppScreen.LIBRARY },
                    onSave = { story ->
                        val old = editingStory
                        if (old != null) {
                            val index = userStories.indexOfFirst { it.id == old.id }
                            if (index >= 0) userStories[index] = story else userStories.add(story)
                        } else userStories.add(story)
                        saveUserStories(context, userStories)
                        selectedStory = story
                        editingStory = story
                        editingChapterIndex = null
                        saveToast = "História Salva"
                        currentScreen = AppScreen.DETAILS
                    }
                )
                AppScreen.READER -> ReaderScreen(
                    story = selectedStory,
                    chapterIndex = chapterIndex,
                    initialPageIndex = pendingReaderPageIndex,
                    openMenuTick = readerMenuTrigger,
                    fontSize = fontSize,
                    onFontSizeChange = { size ->
                        fontSize = size
                        appSettings = appSettings.copy(defaultReaderFontSize = size)
                        saveAppSettings(context, appSettings)
                    },
                    lineHeightMult = appSettings.readerLineHeight,
                    onLineHeightChange = { lh ->
                        appSettings = appSettings.copy(readerLineHeight = lh)
                        saveAppSettings(context, appSettings)
                    },
                    pageMarginDp = appSettings.readerMarginDp,
                    onPageMarginChange = { m ->
                        appSettings = appSettings.copy(readerMarginDp = m)
                        saveAppSettings(context, appSettings)
                    },
                    readerThemeId = appSettings.readerThemeId,
                    onReaderThemeChange = { id ->
                        appSettings = appSettings.copy(readerThemeId = id)
                        saveAppSettings(context, appSettings)
                    },
                    readerMode = appSettings.readerMode,
                    onReaderModeChange = { mode ->
                        appSettings = appSettings.copy(readerMode = mode)
                        saveAppSettings(context, appSettings)
                    },
                    readerFontFamily = appSettings.readerFontFamily,
                    onReaderFontFamilyChange = { ff ->
                        appSettings = appSettings.copy(readerFontFamily = ff)
                        saveAppSettings(context, appSettings)
                    },
                    onPrevious = {
                        if (readingHistory.isNotEmpty()) {
                            chapterIndex = readingHistory.removeAt(readingHistory.lastIndex)
                        } else if (chapterIndex > 0) {
                            chapterIndex--
                        }
                    },
                    onNext = { if (chapterIndex < selectedStory.chapters.lastIndex) {
                        val from = selectedStory.chapters[chapterIndex]
                        val destinationIndex = chapterIndex + 1
                        val destination = selectedStory.chapters[destinationIndex]
                        val isChoiceStory = selectedStory.chapters.any { it.choices.isNotEmpty() || it.isEnding }
                        val completes = destination.isEnding || (!isChoiceStory && destinationIndex == selectedStory.chapters.lastIndex)
                        recordTransition(selectedStory, "linear:${from.id}->${destination.id}", destination, if (completes) "chapter:${destination.id}" else null)
                        readingHistory.add(chapterIndex); chapterIndex++
                        // A mensagem do final só aparece ao tocar em "Fim" (depois de ler o capítulo).
                    } else {
                        // Já no último capítulo
                        showStoryEnding = true
                    } },
                    onChoice = { choice ->
                        if (choice.endsStory) {
                            recordTransition(selectedStory, "choice:${choice.id}", null, "choice:${choice.id}")
                            showStoryEnding = true
                        } else {
                            val destinationIndex = selectedStory.chapters.indexOfFirst { it.id == choice.nextChapterId }
                            if (destinationIndex >= 0) {
                                val destination = selectedStory.chapters[destinationIndex]
                                recordTransition(selectedStory, "choice:${choice.id}", destination, if (destination.isEnding) "chapter:${destination.id}" else null)
                                readingHistory.add(chapterIndex); chapterIndex = destinationIndex
                            }
                        }
                    },
                    onFinishChapter = { showStoryEnding = true },
                    canGoBack = readingHistory.isNotEmpty(),
                    showEnding = showStoryEnding,
                    onDismissEnding = { showStoryEnding = false; currentScreen = AppScreen.DETAILS }
                )
            }
            } // AnimatedContent
        }
    }
    if (updateNotices.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { updateNotices = emptyList() },
            title = { Text("Atualizações disponíveis") },
            text = {
                Column {
                    updateNotices.forEach { Text(it, color = SecondaryText, fontSize = 14.sp) }
                    Spacer(Modifier.height(8.dp))
                    Text("Baixe de novo em Explorar para obter a versão nova.", color = SecondaryText, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { updateNotices = emptyList() }) { Text("OK", color = Accent) }
            },
            containerColor = CardColor,
            titleContentColor = PrimaryText,
            textContentColor = SecondaryText
        )
    }
    if (showExportModeDialog) {
        AlertDialog(
            onDismissRequest = {
                showExportModeDialog = false
                storyPendingExportId = null
            },
            title = { Text("Exportar .lyr") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Escolha como esta história poderá ser usada por quem importar o arquivo:",
                        color = SecondaryText,
                        fontSize = 14.sp
                    )
                    Text(
                        "• Somente leitura — entra na biblioteca e não pode ser editada.\n" +
                            "• Editável — carrega como história autoral e pode ser alterada. " +
                            "Você define uma senha, e quem importar precisa digitá-la.",
                        color = SecondaryText,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    storyPendingExportEditable = true
                    showExportModeDialog = false
                    showExportPasswordDialog = true
                }) { Text("Editável (com senha)", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    storyPendingExportEditable = false
                    storyPendingExportPassword = null
                    showExportModeDialog = false
                    val id = storyPendingExportId
                    val story = id?.let { sid -> (userStories + sampleStories).firstOrNull { it.id == sid } }
                    if (story != null) exportStoryLauncher.launch(storyFileName(story.title))
                    else storyPendingExportId = null
                }) { Text("Somente leitura", color = SecondaryText) }
            },
            containerColor = CardColor,
            titleContentColor = PrimaryText,
            textContentColor = SecondaryText
        )
    }
    storyPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { storyPendingDelete = null },
            title = { Text("Excluir história?") },
            text = {
                Text(
                    "“${target.title}” será removida da sua biblioteca neste aparelho, junto com " +
                        "favorito, estatísticas e fotos. Isso não pode ser desfeito" +
                        if (target.editable) " — exporte um .lyr antes se quiser guardar uma cópia." else "."
                )
            },
            confirmButton = {
                TextButton(onClick = { deleteStoryFromLibrary(target) }) {
                    Text("Excluir", color = DangerColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { storyPendingDelete = null }) { Text("Cancelar", color = SecondaryText) }
            },
            containerColor = CardColor,
            titleContentColor = PrimaryText,
            textContentColor = SecondaryText
        )
    }
    if (showExportPasswordDialog) {
        LyrPasswordDialog(
            title = "Senha do arquivo editável",
            message = "Quem importar este .lyr vai precisar dessa senha para abrir e editar a história. " +
                "Guarde-a bem: sem ela não há como recuperar o arquivo.",
            confirmLabel = "Escolher destino",
            askConfirmation = true,
            errorMessage = null,
            busy = false,
            onConfirm = { pw ->
                showExportPasswordDialog = false
                val story = storyPendingExportId
                    ?.let { sid -> (userStories + sampleStories).firstOrNull { it.id == sid } }
                if (story != null) {
                    storyPendingExportEditable = true
                    storyPendingExportPassword = pw
                    exportStoryLauncher.launch(storyFileName(story.title))
                } else {
                    storyPendingExportId = null
                }
            },
            onDismiss = {
                showExportPasswordDialog = false
                storyPendingExportId = null
            }
        )
    }
    if (showImportPasswordDialog) {
        val uriToImport = pendingImportUri
        LyrPasswordDialog(
            title = "Arquivo protegido",
            message = "Este .lyr é editável e protegido por senha. Digite a senha definida por quem o exportou.",
            confirmLabel = "Importar",
            askConfirmation = false,
            errorMessage = importPasswordError,
            busy = transferBusy,
            onConfirm = { pw -> if (uriToImport != null) startLyrImport(uriToImport, pw) },
            onDismiss = {
                showImportPasswordDialog = false
                pendingImportUri = null
                importPasswordError = null
            }
        )
    }
    accountNotice.value?.let { notice ->
        AlertDialog(
            onDismissRequest = { accountNotice.value = null },
            title = { Text("Conta") },
            text = { Text(notice) },
            confirmButton = { TextButton(onClick = { accountNotice.value = null }) { Text("OK") } },
            containerColor = CardColor,
            titleContentColor = PrimaryText,
            textContentColor = SecondaryText
        )
    }
    if (transferMessage != null) {
        AlertDialog(
            onDismissRequest = { transferMessage = null },
            title = { Text("Arquivo .lyr") },
            text = { Text(transferMessage ?: "") },
            confirmButton = { TextButton(onClick = { transferMessage = null }) { Text("OK") } },
            containerColor = CardColor,
            titleContentColor = PrimaryText,
            textContentColor = SecondaryText
        )
    }
    if (saveToast != null) {
        LaunchedEffect(saveToast) {
            kotlinx.coroutines.delay(1800)
            saveToast = null
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 96.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                color = SurfaceAlt,
                shape = RoundedCornerShape(50),
                shadowElevation = 6.dp
            ) {
                Text(
                    saveToast ?: "",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = SuccessColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
