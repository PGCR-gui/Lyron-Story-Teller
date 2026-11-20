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
internal fun ProfileScreen(
    profile: UserProfile,
    isLoggedIn: Boolean,
    authEmail: String,
    writtenCount: Int,
    favoriteCount: Int,
    onOpenSettings: () -> Unit,
    onLogin: (email: String, password: String, isSignUp: Boolean) -> Unit,
    onLogout: () -> Unit,
    authBusy: Boolean,
    onManagePosted: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        if (!isLoggedIn) {
            Text("Conta Lyron", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "Entre para publicar histórias online e sincronizar seu catálogo.",
                color = SecondaryText,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("E-mail") },
                singleLine = true
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Senha (mín. 6)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Switch(
                    checked = isSignUp,
                    onCheckedChange = { isSignUp = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent)
                )
                Text(if (isSignUp) "Criar nova conta" else "Já tenho conta", color = SecondaryText)
            }
            Button(
                onClick = { onLogin(email.trim(), password, isSignUp) },
                enabled = !authBusy && email.contains("@") && password.length >= 6,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF17121F)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (authBusy) "Aguarde…" else if (isSignUp) "Criar conta" else "Entrar")
            }
            HorizontalDivider(color = SecondaryText.copy(alpha = 0.25f), modifier = Modifier.padding(vertical = 8.dp))
        } else {
            Text("Logado como", color = SecondaryText, fontSize = 12.sp)
            Text(authEmail.ifBlank { "conta online" }, color = Accent, fontWeight = FontWeight.Bold)
            TextButton(onClick = onLogout) { Text("Sair da conta", color = Color(0xFFFF8B9A)) }
            HorizontalDivider(color = SecondaryText.copy(alpha = 0.25f))
        }

        Box(
            modifier = Modifier
                .size(108.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF302744), RoundedCornerShape(50))
                .clickable { onOpenSettings() },
            contentAlignment = Alignment.Center
        ) {
            if (profile.photoUri != null) {
                StoredImage(profile.photoUri, Modifier.fillMaxSize(), ContentScale.Crop, maxDimension = 512)
            } else {
                Text(
                    (profile.nickname.ifBlank { profile.fullName }.ifBlank { "?" }).take(1).uppercase(),
                    color = Accent,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(profile.authorName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (profile.username.isNotBlank()) {
            Text("@${profile.username}", color = Accent, fontSize = 15.sp)
        } else if (isLoggedIn) {
            Text(
                "Escolha seu nome de usuário em Perfil e configurações.",
                color = SecondaryText,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
        Card(colors = CardDefaults.cardColors(containerColor = CardColor), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Nome de autor atual", color = SecondaryText, fontSize = 13.sp)
                Text(profile.authorName, color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Card(colors = CardDefaults.cardColors(containerColor = CardColor), shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Escritas", color = SecondaryText, fontSize = 12.sp)
                    Text("$writtenCount", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = CardColor), shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Favoritas", color = SecondaryText, fontSize = 12.sp)
                    Text("$favoriteCount", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (isLoggedIn) {
            OutlinedButton(onClick = onManagePosted, modifier = Modifier.fillMaxWidth()) {
                Text("📚  Gerenciar histórias postadas", color = Accent)
            }
        }
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("⚙  Perfil e configurações", color = Accent)
        }
        Spacer(Modifier.height(12.dp))
    }
}


@Composable
internal fun ManagePostedScreen(
    isLoggedIn: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onToast: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var stories by remember { mutableStateOf<List<RemoteStory>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingUnpublish by remember { mutableStateOf<RemoteStory?>(null) }
    var unpublishing by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    val myId = currentUserId()

    LaunchedEffect(refreshTick, isLoggedIn) {
        if (!isLoggedIn) {
            stories = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            val all = withContext(Dispatchers.IO) { fetchPublicStories(limit = 100) }
            stories = all.filter { it.ownerId != null && it.ownerId == myId }
        } catch (e: Exception) {
            error = e.message ?: "Não foi possível carregar suas publicações."
            stories = emptyList()
        } finally {
            loading = false
        }
    }

    pendingUnpublish?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!unpublishing) pendingUnpublish = null },
            title = { Text("Remover publicação?") },
            text = {
                Text(
                    "“${target.title}” será removida do catálogo online e o arquivo apagado do servidor. " +
                        "Cópias já baixadas por outras pessoas não são afetadas."
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
                                onToast("Publicação removida")
                                pendingUnpublish = null
                                refreshTick++
                            } catch (e: Exception) {
                                onToast(e.message ?: "Falha ao remover")
                            } finally {
                                unpublishing = false
                            }
                        }
                    }
                ) { Text(if (unpublishing) "Removendo…" else "Remover", color = Color(0xFFFF8B9A)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnpublish = null }, enabled = !unpublishing) {
                    Text("Cancelar", color = SecondaryText)
                }
            },
            containerColor = CardColor,
            titleContentColor = Color.White,
            textContentColor = SecondaryText
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Histórias postadas", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "Gerencie as histórias que você publicou no catálogo online.",
                color = SecondaryText,
                fontSize = 13.sp
            )
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("← Voltar ao perfil", color = Accent)
            }
        }
        when {
            loading -> item { Text("Carregando…", color = SecondaryText) }
            error != null -> item { Text(error!!, color = Color(0xFFFF8B9A)) }
            stories.isEmpty() -> item {
                Text(
                    "Você ainda não publicou nenhuma história online, ou todas já foram removidas.",
                    color = SecondaryText,
                    fontSize = 14.sp
                )
            }
            else -> items(stories, key = { it.id ?: it.title }) { remote ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(remote.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(remote.author, color = SecondaryText, fontSize = 13.sp)
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
                                contentDescription = remote.rating,
                                modifier = Modifier.height(28.dp).wrapContentWidth()
                            )
                        }
                        OutlinedButton(
                            onClick = { pendingUnpublish = remote },
                            enabled = !busy && !unpublishing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Remover do catálogo online", color = Color(0xFFFF8B9A))
                        }
                    }
                }
            }
        }
    }
}
