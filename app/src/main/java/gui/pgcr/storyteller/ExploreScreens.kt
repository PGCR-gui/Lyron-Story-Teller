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

@Composable
internal fun OnlineExploreScreen(
    isLoggedIn: Boolean,
    busy: Boolean,
    onImportRemote: (RemoteStory) -> Unit,
    onNeedLogin: () -> Unit,
    onToast: (String) -> Unit,
    onMessage: (String) -> Unit,
    onAuthorClick: (ownerId: String?, authorName: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingUnpublish by remember { mutableStateOf<RemoteStory?>(null) }
    var unpublishing by remember { mutableStateOf(false) }
    var stories by remember { mutableStateOf<List<RemoteStory>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTick) {
        // Mostra na hora o catálogo salvo (se houver) enquanto a versão nova carrega.
        val cached = if (stories.isEmpty()) withContext(Dispatchers.IO) { loadCatalogCache(context) } else null
        if (cached != null && cached.isNotEmpty()) {
            stories = cached.take(40)
            loading = false
        } else {
            loading = true
        }
        error = null
        try {
            stories = withContext(Dispatchers.IO) { fetchPublicStoriesCached(context) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Não foi possível carregar o catálogo online."
            stories = emptyList()
        } finally {
            loading = false
        }
    }

    pendingUnpublish?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!unpublishing) pendingUnpublish = null },
            title = { Text("Remover história?") },
            text = {
                Text(
                    "“${target.title}” sai do catálogo online e o arquivo é apagado do servidor. " +
                        "Cópias já baixadas por outras pessoas continuam com elas. Sua cópia local não é afetada."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !unpublishing,
                    onClick = {
                        unpublishing = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { unpublishStory(target) }
                                pendingUnpublish = null
                                refreshTick++
                                onToast("História removida")
                            } catch (e: Exception) {
                                pendingUnpublish = null
                                onMessage("Não foi possível remover: ${e.message ?: "erro"}")
                            } finally {
                                unpublishing = false
                            }
                        }
                    }
                ) { Text(if (unpublishing) "Aguarde…" else "Remover", color = DangerColor) }
            },
            dismissButton = {
                TextButton(enabled = !unpublishing, onClick = { pendingUnpublish = null }) {
                    Text("Cancelar", color = SecondaryText)
                }
            },
            containerColor = CardColor,
            titleContentColor = PrimaryText,
            textContentColor = SecondaryText
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Explorar", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Explorar no Story Teller", color = PrimaryText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Histórias públicas hospedadas no Supabase. Baixe para ler e guardar na biblioteca.",
                    color = SecondaryText,
                    fontSize = 14.sp
                )
                if (catalogOffline.value) {
                    Text(
                        "Sem conexão: mostrando a última versão salva no aparelho.",
                        color = WarnColor,
                        fontSize = 12.sp
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { refreshTick++ }, enabled = !loading && !busy) {
                    Text("Atualizar", color = Accent)
                }
                if (!isLoggedIn) {
                    TextButton(onClick = onNeedLogin) {
                        Text("Entrar para publicar", color = Accent)
                    }
                }
            }
        }
        when {
            loading -> item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            }
            error != null -> item {
                Text(error ?: "", color = DangerColor, fontSize = 14.sp)
            }
            stories.isEmpty() -> item {
                Text(
                    "Nenhuma história pública ainda. Faça login no Perfil e publique a sua pela tela de detalhes.",
                    color = SecondaryText,
                    fontSize = 14.sp
                )
            }
            else -> items(stories, key = { it.id ?: it.title }) { remote ->
                RemoteStoryCard(
                    remote = remote,
                    isLoggedIn = isLoggedIn,
                    busy = busy,
                    onImportRemote = onImportRemote,
                    onAuthorClick = onAuthorClick,
                    onRequestUnpublish = { pendingUnpublish = it },
                    unpublishEnabled = !unpublishing
                )
            }
        }
    }
}

/** Cartão de uma história do catálogo online (usado em Explorar e na Pesquisa). */
@Composable
internal fun RemoteStoryCard(
    remote: RemoteStory,
    isLoggedIn: Boolean,
    busy: Boolean,
    onImportRemote: (RemoteStory) -> Unit,
    onAuthorClick: (ownerId: String?, authorName: String) -> Unit,
    onRequestUnpublish: ((RemoteStory) -> Unit)? = null,
    unpublishEnabled: Boolean = true
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RemoteCoverImage(
                    remote.coverPath,
                    Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)),
                    version = remote.updatedAt
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(remote.title, color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "por ${remote.author}",
                        color = Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            onAuthorClick(remote.ownerId, remote.author)
                        }
                    )
                    if (remote.genres.isNotEmpty()) {
                        Text(remote.genres.joinToString(" · "), color = Accent, fontSize = 12.sp)
                    }
                    Text(
                        buildString {
                            append("${remote.chapterCount} cap.")
                            remote.updatedAt?.take(10)?.let { append(" · atualizado $it") }
                        },
                        color = SecondaryText,
                        fontSize = 11.sp
                    )
                }
            }
            if (remote.description.isNotBlank()) {
                var expanded by remember(remote.id) { mutableStateOf(false) }
                val longText = remote.description.length > 160
                Text(
                    remote.description,
                    color = SecondaryText,
                    fontSize = 13.sp,
                    maxLines = if (expanded || !longText) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (longText) {
                    Text(
                        if (expanded) "Mostrar menos" else "Ler sinopse completa",
                        color = Accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { expanded = !expanded }
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(remote.status, fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = when (remote.status) {
                            "Completa" -> Color(0xFF1B5E3B)
                            "Cancelada" -> Color(0xFF5E1B2A)
                            else -> Color(0xFF1B3A5E)
                        },
                        labelColor = Color.White
                    )
                )
                Image(
                    painter = painterResource(id = classifDrawableRes(remote.rating)),
                    contentDescription = "Classificação ${remote.rating}",
                    modifier = Modifier.height(28.dp).wrapContentWidth()
                )
            }
            val publishedByMe = isLoggedIn && remote.ownerId != null && remote.ownerId == currentUserId()
            if (publishedByMe) {
                Text("Publicada por você", color = Accent, fontSize = 12.sp)
            }
            Button(
                onClick = { onImportRemote(remote) },
                enabled = !busy && !remote.filePath.isNullOrBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "Baixando…" else "Baixar para a biblioteca")
            }
            if (publishedByMe && onRequestUnpublish != null) {
                OutlinedButton(
                    onClick = { onRequestUnpublish(remote) },
                    enabled = !busy && unpublishEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Remover do Lyron Story Teler", color = DangerColor)
                }
            }
        }
    }
}

/**
 * Perfil público de um autor online: bio (se liberada), histórias publicadas e favoritos públicos.
 * Usa o catálogo público e, quando possível, o perfil remoto do dono.
 */
@Composable
internal fun AuthorProfileScreen(
    ownerId: String?,
    authorName: String,
    isLoggedIn: Boolean,
    busy: Boolean,
    onImportRemote: (RemoteStory) -> Unit,
    onNeedLogin: () -> Unit,
    onToast: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var remoteProfile by remember { mutableStateOf<RemoteProfile?>(null) }
    var displayName by remember { mutableStateOf(authorName.ifBlank { "Autor" }) }
    var avatarUri by remember { mutableStateOf<String?>(null) }
    var written by remember { mutableStateOf<List<RemoteStory>>(emptyList()) }
    var favoriteStories by remember { mutableStateOf<List<RemoteStory>>(emptyList()) }
    var expandedSynopsisIds by remember { mutableStateOf(setOf<String>()) }
    var refreshTick by remember { mutableStateOf(0) }
    // Seguir autor: o app avisa quando ele publicar história ou capítulo novo.
    val canFollow = !ownerId.isNullOrBlank() && ownerId != currentUserId()
    var following by remember(ownerId) { mutableStateOf(isFollowingAuthor(context, ownerId)) }
    val requestNotificationPermission = rememberNotificationPermissionRequest()

    LaunchedEffect(ownerId, authorName, refreshTick) {
        loading = true
        error = null
        try {
            val all = withContext(Dispatchers.IO) { fetchPublicStoriesCached(context, limit = Int.MAX_VALUE) }
            written = when {
                !ownerId.isNullOrBlank() -> all.filter { it.ownerId == ownerId }
                authorName.isNotBlank() -> all.filter { it.author.equals(authorName, ignoreCase = true) }
                else -> emptyList()
            }
            if (!ownerId.isNullOrBlank()) {
                runCatching {
                    val remote = withContext(Dispatchers.IO) { fetchMyProfile(ownerId) }
                    if (remote != null) {
                        remoteProfile = remote.first
                        displayName = when {
                            remote.first.nickname.isNotBlank() -> remote.first.nickname
                            remote.second.isNotBlank() -> remote.second
                            remote.first.username.isNotBlank() -> remote.first.username
                            else -> authorName.ifBlank { "Autor" }
                        }
                        val path = remote.first.avatarPath
                        if (!path.isNullOrBlank()) {
                            val bytes = withContext(Dispatchers.IO) { downloadAvatarBytes(path) }
                            avatarUri = withContext(Dispatchers.IO) { storeAuthorAvatarCached(context, path, bytes) }
                        } else {
                            avatarUri = null
                        }
                    }
                }
            }
            // Favoritos públicos: só listamos se o perfil liberar; a lista real depende do backend.
            // Enquanto não houver endpoint dedicado, deixamos vazio (e a UI explica).
            favoriteStories = emptyList()
        } catch (e: Exception) {
            error = e.message ?: "Não foi possível carregar o perfil."
            written = emptyList()
        } finally {
            loading = false
        }
    }

    val profile = remoteProfile
    val showBio = profile != null && profile.publicBio && profile.bio.isNotBlank()
    val showWritten = profile?.publicWritten != false
    val showFavorites = profile?.publicFavorites == true

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(42.dp))
                        .background(SurfaceAlt),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUri != null) {
                        StoredImage(avatarUri!!, Modifier.fillMaxSize(), ContentScale.Crop, maxDimension = 256)
                    } else {
                        Text(
                            displayName.take(1).uppercase(),
                            color = Accent,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(displayName, color = PrimaryText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (!profile?.username.isNullOrBlank()) {
                        Text("@${profile!!.username}", color = Accent, fontSize = 14.sp)
                    }
                    Text(
                        buildString {
                            append("${written.size} ${if (written.size == 1) "história pública" else "histórias públicas"}")
                        },
                        color = SecondaryText,
                        fontSize = 13.sp
                    )
                }
            }
        }

        if (showBio) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Biografia", color = PrimaryText, fontWeight = FontWeight.SemiBold)
                        Text(profile!!.bio, color = SecondaryText, fontSize = 14.sp, lineHeight = 21.sp)
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (canFollow) {
                    if (following) {
                        OutlinedButton(onClick = {
                            unfollowAuthor(context, ownerId!!)
                            following = false
                            onToast("Você deixou de seguir $displayName")
                        }) { Text("✓ Seguindo", color = Accent) }
                    } else {
                        Button(
                            onClick = {
                                followAuthor(context, ownerId!!, displayName)
                                following = true
                                requestNotificationPermission(false)
                                onToast("Você segue $displayName")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)
                        ) { Text("＋ Seguir") }
                    }
                }
                OutlinedButton(onClick = { refreshTick++ }, enabled = !loading && !busy) {
                    Text("Atualizar", color = Accent)
                }
                if (!isLoggedIn) {
                    TextButton(onClick = onNeedLogin) {
                        Text("Entrar para baixar", color = Accent)
                    }
                }
            }
        }

        when {
            loading -> item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            }
            error != null -> item {
                Text(error ?: "", color = DangerColor, fontSize = 14.sp)
            }
            else -> {
                if (showWritten) {
                    item {
                        Text("Histórias publicadas", color = PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    if (written.isEmpty()) {
                        item {
                            Text(
                                "Nenhuma história pública deste autor no momento.",
                                color = SecondaryText,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        items(written, key = { it.id ?: it.title }) { remote ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardColor),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        RemoteCoverImage(
                                            remote.coverPath,
                                            Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                                            version = remote.updatedAt
                                        )
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(remote.title, color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            if (remote.genres.isNotEmpty()) {
                                                Text(remote.genres.joinToString(" · "), color = Accent, fontSize = 12.sp)
                                            }
                                            Text(
                                                "${remote.chapterCount} cap. · ${remote.status}",
                                                color = SecondaryText,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    if (remote.description.isNotBlank()) {
                                        val key = remote.id ?: remote.title
                                        val expanded = key in expandedSynopsisIds
                                        val longText = remote.description.length > 160
                                        Text(
                                            remote.description,
                                            color = SecondaryText,
                                            fontSize = 13.sp,
                                            maxLines = if (expanded || !longText) Int.MAX_VALUE else 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (longText) {
                                            Text(
                                                if (expanded) "Mostrar menos" else "Ler sinopse completa",
                                                color = Accent,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.clickable {
                                                    expandedSynopsisIds = if (expanded) {
                                                        expandedSynopsisIds - key
                                                    } else {
                                                        expandedSynopsisIds + key
                                                    }
                                                }
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = { onImportRemote(remote) },
                                        enabled = !busy && !remote.filePath.isNullOrBlank(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Accent,
                                            contentColor = OnAccent
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(if (busy) "Baixando…" else "Baixar para a biblioteca")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            "Este autor ocultou a lista de histórias escritas no perfil público.",
                            color = SecondaryText,
                            fontSize = 13.sp
                        )
                    }
                }

                if (showFavorites) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text("Favoritas", color = PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    if (favoriteStories.isEmpty()) {
                        item {
                            Text(
                                "Nenhuma favorita pública listada ainda.",
                                color = SecondaryText,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
internal fun StoryCard(story: Story, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor)
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceAlt, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (story.coverImageUri != null) StoredImage(story.coverImageUri, Modifier.fillMaxSize(), maxDimension = 256)
                else Text("✍", color = Accent, fontSize = 30.sp)
                Image(
                    painter = painterResource(id = classifDrawableRes(story.rating)),
                    contentDescription = story.rating,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .height(18.dp)
                        .wrapContentWidth()
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(story.title, color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(story.author, color = SecondaryText, fontSize = 12.sp)
                Text(
                    buildString {
                        append(story.genres.joinToString(" · ").ifBlank { story.genre })
                        if (story.classifications.isNotEmpty()) {
                            append(" · ")
                            append(story.classifications.joinToString(" · "))
                        }
                    },
                    color = Accent,
                    fontSize = 11.sp,
                    maxLines = 1
                )
                if (story.tags.isNotEmpty()) {
                    Text(
                        story.tags.joinToString(" · ") { "#$it" },
                        color = SecondaryText,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
                Text(story.description, color = SecondaryText, fontSize = 12.sp, maxLines = 2)
            }
        }
    }
}

@Composable
internal fun ExploreScreen(
    allStories: List<Story>,
    isLoggedIn: Boolean,
    busy: Boolean,
    onStoryClick: (Story) -> Unit,
    onImportRemote: (RemoteStory) -> Unit,
    onAuthorClick: (ownerId: String?, authorName: String) -> Unit = { _, _ -> }
) {
    var query by remember { mutableStateOf("") }
    var genreFilter by remember { mutableStateOf<String?>(null) }
    var tagFilter by remember { mutableStateOf<String?>(null) }

    // Catálogo online: a pesquisa precisa enxergar as histórias públicas, não só a biblioteca local.
    val searchContext = LocalContext.current
    var onlineStories by remember { mutableStateOf<List<RemoteStory>>(emptyList()) }
    var onlineLoading by remember { mutableStateOf(true) }
    var onlineError by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    LaunchedEffect(refreshTick) {
        val cached = if (onlineStories.isEmpty()) withContext(Dispatchers.IO) { loadCatalogCache(searchContext) } else null
        if (cached != null && cached.isNotEmpty()) {
            onlineStories = cached.take(500)
            onlineLoading = false
        } else {
            onlineLoading = true
        }
        onlineError = null
        try {
            onlineStories = withContext(Dispatchers.IO) { fetchPublicStoriesCached(searchContext, limit = 500) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            onlineError = e.message ?: "Não foi possível carregar as histórias online."
            onlineStories = emptyList()
        } finally {
            onlineLoading = false
        }
    }

    // O que já foi baixado aparece uma vez só, como história da biblioteca.
    val downloadedRemoteIds = remember(allStories) { allStories.mapNotNull { it.remoteId }.toSet() }
    val onlineCandidates = remember(onlineStories, downloadedRemoteIds) {
        onlineStories.filter { it.id == null || it.id !in downloadedRemoteIds }
    }
    val allGenres = remember(allStories, onlineCandidates) {
        (allStories.flatMap { it.genres } + onlineCandidates.flatMap { it.genres }).distinct().sorted()
    }
    val allTags = remember(allStories, onlineCandidates) {
        (allStories.flatMap { it.tags } + onlineCandidates.flatMap { it.tags }).distinct().sorted()
    }
    val filtered = remember(query, allStories, genreFilter, tagFilter) {
        val q = query.trim()
        allStories.filter { story ->
            val matchesQuery = q.isEmpty() ||
                story.title.contains(q, ignoreCase = true) ||
                story.author.contains(q, ignoreCase = true) ||
                story.genres.any { g -> g.contains(q, ignoreCase = true) } ||
                story.tags.any { t -> t.contains(q, ignoreCase = true) } ||
                story.description.contains(q, ignoreCase = true)
            val matchesGenre = genreFilter == null || genreFilter in story.genres
            val matchesTag = tagFilter == null || tagFilter in story.tags
            matchesQuery && matchesGenre && matchesTag
        }
    }
    val filteredOnline = remember(query, onlineCandidates, genreFilter, tagFilter) {
        val q = query.trim()
        onlineCandidates.filter { story ->
            val matchesQuery = q.isEmpty() ||
                story.title.contains(q, ignoreCase = true) ||
                story.author.contains(q, ignoreCase = true) ||
                story.genres.any { g -> g.contains(q, ignoreCase = true) } ||
                story.tags.any { t -> t.contains(q, ignoreCase = true) } ||
                story.description.contains(q, ignoreCase = true)
            val matchesGenre = genreFilter == null || genreFilter in story.genres
            val matchesTag = tagFilter == null || tagFilter in story.tags
            matchesQuery && matchesGenre && matchesTag
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Pesquisar histórias", color = PrimaryText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(
                "Busque pelo título, autor, gênero ou tag, na biblioteca e nas histórias públicas online.",
                color = SecondaryText,
                fontSize = 14.sp
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Nome da história…") },
                placeholder = { Text("Ex.: horizonte, fantasia…") }
            )
        }
        if (allGenres.isNotEmpty()) {
            item {
                Text("Gênero", color = PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(selected = genreFilter == null, onClick = { genreFilter = null }, label = { Text("Todos") })
                }
                // chips wrap via multiple rows roughly
                allGenres.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { g ->
                            FilterChip(
                                selected = genreFilter == g,
                                onClick = { genreFilter = if (genreFilter == g) null else g },
                                label = { Text(g, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }
        if (allTags.isNotEmpty()) {
            item {
                Text("Tags", color = PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                allTags.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { tag ->
                            FilterChip(
                                selected = tagFilter == tag,
                                onClick = { tagFilter = if (tagFilter == tag) null else tag },
                                label = { Text("#$tag", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }

        // Biblioteca
        item(key = "header_library") {
            Text("Na biblioteca", color = PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        if (filtered.isEmpty()) {
            item(key = "empty_library") {
                Text(
                    if (query.isBlank() && genreFilter == null && tagFilter == null) "Nenhuma história na biblioteca ainda."
                    else "Nenhum resultado na biblioteca.",
                    color = SecondaryText,
                    fontSize = 14.sp
                )
            }
        } else {
            items(filtered, key = { "local:${it.id}" }) { story ->
                StoryCard(story = story, onClick = { onStoryClick(story) })
            }
        }

        // Catálogo online
        item(key = "header_online") {
            Text("Histórias online", color = PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        when {
            onlineLoading -> item(key = "online_loading") {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            }
            onlineError != null -> item(key = "online_error") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(onlineError ?: "", color = DangerColor, fontSize = 14.sp)
                    TextButton(onClick = { refreshTick++ }) { Text("Tentar novamente", color = Accent) }
                }
            }
            filteredOnline.isEmpty() -> item(key = "online_empty") {
                Text(
                    if (query.isBlank() && genreFilter == null && tagFilter == null) "Nenhuma história pública disponível ainda."
                    else "Nenhuma história online encontrada.",
                    color = SecondaryText,
                    fontSize = 14.sp
                )
            }
            else -> items(filteredOnline, key = { "remote:${it.id ?: it.title}" }) { remote ->
                RemoteStoryCard(
                    remote = remote,
                    isLoggedIn = isLoggedIn,
                    busy = busy,
                    onImportRemote = onImportRemote,
                    onAuthorClick = onAuthorClick
                )
            }
        }
    }
}

@Composable
internal fun LibraryScreen(
    userWritten: List<Story>,
    favorites: List<Story>,
    others: List<Story>,
    transferBusy: Boolean,
    onImportClick: () -> Unit,
    onStoryClick: (Story) -> Unit
) {
    var selectedGenreFilter by remember { mutableStateOf<String?>(null) }
    val allGenres = remember(userWritten, favorites, others) {
        (userWritten + favorites + others).flatMap { it.genres }.distinct().sorted()
    }
    fun filterByGenre(list: List<Story>): List<Story> {
        val g = selectedGenreFilter ?: return list
        return list.filter { g in it.genres }
    }
    val filteredWritten = filterByGenre(userWritten)
    val filteredFavorites = filterByGenre(favorites)
    val filteredOthers = filterByGenre(others)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Sua biblioteca", color = PrimaryText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Favoritas, suas obras e histórias importadas.", color = SecondaryText, fontSize = 13.sp)
            OutlinedButton(onClick = onImportClick, enabled = !transferBusy, modifier = Modifier.fillMaxWidth()) {
                Text(if (transferBusy) "Processando arquivo…" else "Importar história .lyr", color = Accent)
            }
        }
        if (allGenres.isNotEmpty()) {
            item {
                Text("Filtrar por gênero", color = PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        color = if (selectedGenreFilter == null) Accent else CardColor,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.clickable { selectedGenreFilter = null }
                    ) {
                        Text(
                            "Todos",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = if (selectedGenreFilter == null) OnAccent else SecondaryText,
                            fontSize = 12.sp
                        )
                    }
                }
                allGenres.chunked(3).forEach { row ->
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { genre ->
                            val selected = selectedGenreFilter == genre
                            Surface(
                                color = if (selected) Accent else CardColor,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.clickable {
                                    selectedGenreFilter = if (selected) null else genre
                                }
                            ) {
                                Text(
                                    genre,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = if (selected) OnAccent else SecondaryText,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Text("★ Favoritas", color = WarnColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (filteredFavorites.isEmpty()) {
            item { Text("Nenhuma história favorita ainda.", color = SecondaryText, fontSize = 13.sp) }
        } else {
            items(filteredFavorites, key = { "fav-${it.id}" }) { story ->
                StoryCard(story = story, onClick = { onStoryClick(story) })
            }
        }
        item {
            Text("✎ Minhas histórias", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (filteredWritten.isEmpty()) {
            item { Text("Você ainda não escreveu nenhuma história.", color = SecondaryText, fontSize = 13.sp) }
        } else {
            items(filteredWritten, key = { "mine-${it.id}" }) { story ->
                StoryCard(story = story, onClick = { onStoryClick(story) })
            }
        }
        if (filteredOthers.isNotEmpty()) {
            item {
                Text("Importadas e Baixadas", color = SecondaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            items(filteredOthers, key = { "other-${it.id}" }) { story ->
                StoryCard(story = story, onClick = { onStoryClick(story) })
            }
        }
    }
}
