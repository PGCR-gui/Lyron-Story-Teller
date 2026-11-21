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
internal fun SettingsHubScreen(
    profile: UserProfile,
    settings: AppSettings,
    onNavigate: (AppScreen) -> Unit
) {
    val currentThemeName = THEME_OPTIONS.firstOrNull { it.first == settings.themeId }?.second ?: "Escuro clássico"
    val currentReaderThemeName = READER_THEME_OPTIONS.firstOrNull { it.first == settings.readerThemeId }?.second ?: "Amethyst Night"
    val currentReaderModeName = if (settings.readerMode == "paginated") "Paginado" else "Contínuo"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Configurações", color = PrimaryText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Escolha uma categoria para personalizar sua experiência.", color = SecondaryText, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
        }
        item {
            SettingsCategoryCard(
                icon = "𖠌",
                title = "Perfil e conta",
                subtitle = "Foto, @usuário, apelido, biografia e privacidade (${profile.authorName})",
                onClick = { onNavigate(AppScreen.SETTINGS_PROFILE) }
            )
        }
        item {
            SettingsCategoryCard(
                icon = "◑",
                title = "Aparência",
                subtitle = "Tema geral do aplicativo · Atual: $currentThemeName",
                onClick = { onNavigate(AppScreen.SETTINGS_APPEARANCE) }
            )
        }
        item {
            SettingsCategoryCard(
                icon = "Aa",
                title = "Leitor de histórias",
                subtitle = "$currentReaderThemeName · $currentReaderModeName · ${settings.defaultReaderFontSize.toInt()} sp",
                onClick = { onNavigate(AppScreen.SETTINGS_READER) }
            )
        }
        item {
            SettingsCategoryCard(
                icon = "🔔",
                title = "Notificações",
                subtitle = "Capítulos novos de favoritas e de autores que você segue",
                onClick = { onNavigate(AppScreen.SETTINGS_NOTIFICATIONS) }
            )
        }
        item {
            SettingsCategoryCard(
                icon = "❖",
                title = "Conteúdo e exportação",
                subtitle = "Opções de arquivos .lyr e confirmação antes de exportar",
                onClick = { onNavigate(AppScreen.SETTINGS_CONTENT) }
            )
        }
        item {
            SettingsCategoryCard(
                icon = "ⓘ",
                title = "Sobre o aplicativo",
                subtitle = "Versão atual e informações do StoryVerse",
                onClick = { onNavigate(AppScreen.SETTINGS_ABOUT) }
            )
        }
    }
}

@Composable
internal fun SettingsCategoryCard(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = SecondaryText, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = Accent, fontSize = 26.sp)
        }
    }
}

@Composable
internal fun SettingsProfileScreen(
    profile: UserProfile,
    isLoggedIn: Boolean,
    profileBusy: Boolean,
    profileStatus: Pair<String, Boolean>?,
    onCheckUsername: suspend (String) -> Boolean?,
    onSaveProfile: (UserProfile) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProfileSettingsSection(
            profile = profile,
            isLoggedIn = isLoggedIn,
            busy = profileBusy,
            status = profileStatus,
            onCheckUsername = onCheckUsername,
            onSave = onSaveProfile
        )
    }
}

@Composable
internal fun SettingsReaderScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
    // Pré-visualização sempre visível: muda na hora ao trocar tema, fonte, tamanho, espaçamento ou margem.
    ReaderLivePreview(
        settings = settings,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Modo de leitura padrão", color = PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = settings.readerMode == "continuous",
                onClick = { onSettingsChange(settings.copy(readerMode = "continuous")) },
                label = { Text("Contínuo (rolagem)") }
            )
            FilterChip(
                selected = settings.readerMode == "paginated",
                onClick = { onSettingsChange(settings.copy(readerMode = "paginated")) },
                label = { Text("Paginado (livro)") }
            )
        }

        HorizontalDivider(color = SecondaryText.copy(alpha = 0.25f))
        Text(
            "Tamanho padrão do texto: ${settings.defaultReaderFontSize.toInt()} sp",
            color = PrimaryText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Slider(
            value = settings.defaultReaderFontSize,
            onValueChange = { onSettingsChange(settings.copy(defaultReaderFontSize = it)) },
            valueRange = 8f..28f,
            steps = 19,
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = SecondaryText.copy(alpha = 0.3f)
            )
        )

        HorizontalDivider(color = SecondaryText.copy(alpha = 0.25f))
        Text("Tema do leitor", color = PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Cada tema aparece com a fonte e o tamanho escolhidos.", color = SecondaryText, fontSize = 12.sp)
        READER_THEME_OPTIONS.forEach { (id, label) ->
            ReaderThemeOptionCard(
                themeId = id,
                label = label,
                fontId = settings.readerFontFamily,
                fontSize = settings.defaultReaderFontSize,
                lineHeightMult = settings.readerLineHeight,
                selected = settings.readerThemeId == id,
                onClick = { onSettingsChange(settings.copy(readerThemeId = id)) }
            )
        }

        HorizontalDivider(color = SecondaryText.copy(alpha = 0.25f))
        Text("Fonte do leitor", color = PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        READER_FONT_OPTIONS.forEach { (id, label) ->
            val selected = settings.readerFontFamily == id
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSettingsChange(settings.copy(readerFontFamily = id)) }
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(
                    selected = selected,
                    onClick = { onSettingsChange(settings.copy(readerFontFamily = id)) },
                    colors = RadioButtonDefaults.colors(selectedColor = Accent)
                )
                ReaderFontSample(fontId = id, label = label, modifier = Modifier.weight(1f))
            }
        }
        CustomFontSection(
            selectedId = settings.readerFontFamily,
            onSelect = { onSettingsChange(settings.copy(readerFontFamily = it)) }
        )

        HorizontalDivider(color = SecondaryText.copy(alpha = 0.25f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Carregar fontes personalizadas", color = PrimaryText, fontWeight = FontWeight.Medium)
                Text("Habilita as fontes especiais embutidas no leitor.", color = SecondaryText, fontSize = 12.sp)
            }
            Switch(
                checked = settings.loadCustomFonts,
                onCheckedChange = { onSettingsChange(settings.copy(loadCustomFonts = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = Accent)
            )
        }
    }
    }
}

@Composable
internal fun SettingsContentScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Exportação de histórias", color = PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Card(
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Confirmar modo ao exportar .lyr", color = PrimaryText, fontWeight = FontWeight.Medium)
                    Text(
                        "Pergunta se o arquivo deve ser somente leitura ou editável com senha antes de salvar.",
                        color = SecondaryText,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = settings.confirmBeforeExport,
                    onCheckedChange = { onSettingsChange(settings.copy(confirmBeforeExport = it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent)
                )
            }
        }
    }
}

@Composable
internal fun SettingsAboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Lyron Story Teller", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Versão de testes 0.05", color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Tudo aqui ainda é muito experimental e se encontra em processo de desenvolvimento. " +
                        "Crie histórias lineares ou interativas, exporte pacotes .lyr com mídias e leia no seu ritmo.",
                    color = SecondaryText,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }

        val aboutContext = LocalContext.current
        Card(
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Créditos", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "Ícone do app",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFFFF4DC))
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Ícone do app", color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Ícone criado por Magnific, via Flaticon.",
                            color = SecondaryText,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
                Text(
                    "Magnific no Flaticon",
                    color = Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        runCatching {
                            aboutContext.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.flaticon.com/br/autores/magnific"))
                            )
                        }
                    }
                )
                Text(
                    "Aviso: este é um ícone temporário e será substituído por um ícone próprio em uma versão futura.",
                    color = SecondaryText,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}


internal enum class UsernameCheck { IDLE, INVALID, CHECKING, AVAILABLE, MINE, TAKEN, UNKNOWN }

internal val USERNAME_REGEX = Regex("^[a-z0-9_]{3,20}$")

internal fun normalizeUsername(raw: String): String =
    raw.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }.take(20)

@Composable
internal fun ProfileSettingsSection(
    profile: UserProfile,
    isLoggedIn: Boolean,
    busy: Boolean,
    status: Pair<String, Boolean>?,
    onCheckUsername: suspend (String) -> Boolean?,
    onSave: (UserProfile) -> Unit
) {
    val context = LocalContext.current
    var fullName by remember(profile) { mutableStateOf(profile.fullName) }
    var nickname by remember(profile) { mutableStateOf(profile.nickname) }
    var username by remember(profile) { mutableStateOf(profile.username) }
    var useFullName by remember(profile) { mutableStateOf(profile.useFullNameAsAuthor) }
    var photoUri by remember(profile) { mutableStateOf(profile.photoUri) }
    var photoError by remember { mutableStateOf("") }
    var usernameCheck by remember { mutableStateOf(UsernameCheck.IDLE) }
    var bio by remember(profile) { mutableStateOf(profile.bio) }
    var publicBio by remember(profile) { mutableStateOf(profile.publicBio) }
    var publicWritten by remember(profile) { mutableStateOf(profile.publicWritten) }
    var publicFavorites by remember(profile) { mutableStateOf(profile.publicFavorites) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            retainImageReadPermission(context, uri)
            val local = importPickedAvatar(context, uri.toString())
            if (local != null) {
                photoUri = local
                photoError = ""
            } else {
                photoError = "Não foi possível ler a foto. Tente outra imagem."
            }
        }
    }

    LaunchedEffect(username, isLoggedIn, profile.username) {
        val candidate = username.trim()
        usernameCheck = when {
            !isLoggedIn || candidate.isEmpty() -> UsernameCheck.IDLE
            !USERNAME_REGEX.matches(candidate) -> UsernameCheck.INVALID
            candidate == profile.username -> UsernameCheck.MINE
            else -> UsernameCheck.CHECKING
        }
        if (usernameCheck == UsernameCheck.CHECKING) {
            kotlinx.coroutines.delay(500) // espera o usuário parar de digitar
            usernameCheck = when (onCheckUsername(candidate)) {
                true -> UsernameCheck.AVAILABLE
                false -> UsernameCheck.TAKEN
                null -> UsernameCheck.UNKNOWN
            }
        }
    }

    val usernameAllowsSave = !isLoggedIn ||
        usernameCheck == UsernameCheck.AVAILABLE ||
        usernameCheck == UsernameCheck.MINE ||
        usernameCheck == UsernameCheck.UNKNOWN

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Perfil", color = PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(50))
                    .background(SurfaceAlt, RoundedCornerShape(50))
                    .clickable { photoPicker.launch(arrayOf("image/*")) },
                contentAlignment = Alignment.Center
            ) {
                val shownPhoto = photoUri
                if (shownPhoto != null) {
                    StoredImage(shownPhoto, Modifier.fillMaxSize(), ContentScale.Crop, maxDimension = 512)
                } else {
                    Text(
                        (nickname.ifBlank { fullName }.ifBlank { username }.ifBlank { "?" }).take(1).uppercase(),
                        color = Accent,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { photoPicker.launch(arrayOf("image/*")) }) {
                    Text(if (photoUri == null) "Adicionar foto" else "Trocar foto", color = Accent)
                }
                if (photoUri != null) {
                    TextButton(onClick = { photoUri = null }) {
                        Text("Remover foto", color = DangerColor)
                    }
                }
            }
            if (photoError.isNotBlank()) {
                Text(photoError, color = DangerColor, fontSize = 12.sp)
            }
        }

        OutlinedTextField(
            value = username,
            onValueChange = { username = normalizeUsername(it) },
            modifier = Modifier.fillMaxWidth(),
            enabled = isLoggedIn,
            label = { Text("Nome de usuário (único)") },
            prefix = { Text("@") },
            isError = usernameCheck == UsernameCheck.TAKEN || usernameCheck == UsernameCheck.INVALID,
            supportingText = {
                Text(
                    when {
                        !isLoggedIn -> "Entre na conta (aba Perfil) para escolher um nome de usuário único."
                        usernameCheck == UsernameCheck.INVALID -> "Use de 3 a 20 caracteres: a–z, 0–9 e _."
                        usernameCheck == UsernameCheck.CHECKING -> "Verificando disponibilidade…"
                        usernameCheck == UsernameCheck.AVAILABLE -> "Disponível ✓"
                        usernameCheck == UsernameCheck.MINE -> "Este é o seu nome de usuário atual."
                        usernameCheck == UsernameCheck.TAKEN -> "Esse nome de usuário já está em uso."
                        usernameCheck == UsernameCheck.UNKNOWN -> "Não deu para verificar agora; conferimos ao salvar."
                        else -> "3 a 20 caracteres: letras minúsculas, números e _"
                    }
                )
            },
            singleLine = true
        )
        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nome completo (opcional)") },
            singleLine = true
        )
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Apelido / pseudônimo") },
            singleLine = true
        )
        Text("Usar como nome de autor:", color = PrimaryText, fontWeight = FontWeight.SemiBold)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { useFullName = true }
        ) {
            RadioButton(
                selected = useFullName,
                onClick = { useFullName = true },
                colors = RadioButtonDefaults.colors(selectedColor = Accent)
            )
            Text(fullName.ifBlank { "Nome completo" }, color = if (fullName.isBlank()) SecondaryText else PrimaryText)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { useFullName = false }
        ) {
            RadioButton(
                selected = !useFullName,
                onClick = { useFullName = false },
                colors = RadioButtonDefaults.colors(selectedColor = Accent)
            )
            Text(nickname.ifBlank { "Apelido / pseudônimo" }, color = if (nickname.isBlank()) SecondaryText else PrimaryText)
        }
        Text(
            "Nome de autor atual: " + UserProfile(fullName.trim(), nickname.trim(), useFullName).authorName,
            color = Accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text("Biografia", color = PrimaryText, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = bio,
            onValueChange = { if (it.length <= 280) bio = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            label = { Text("Sobre você") },
            placeholder = { Text("Uma frase sobre o que você escreve…") }
        )
        Text("Privacidade do perfil público", color = PrimaryText, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Mostrar biografia", color = SecondaryText, modifier = Modifier.weight(1f))
            Switch(checked = publicBio, onCheckedChange = { publicBio = it }, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Mostrar histórias escritas", color = SecondaryText, modifier = Modifier.weight(1f))
            Switch(checked = publicWritten, onCheckedChange = { publicWritten = it }, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Mostrar favoritas", color = SecondaryText, modifier = Modifier.weight(1f))
            Switch(checked = publicFavorites, onCheckedChange = { publicFavorites = it }, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
        }
        Button(
            onClick = {
                onSave(
                    UserProfile(
                        fullName = fullName.trim(),
                        nickname = nickname.trim(),
                        useFullNameAsAuthor = useFullName,
                        photoUri = photoUri,
                        username = username.trim(),
                        avatarPath = profile.avatarPath,
                        bio = bio.trim(),
                        publicBio = publicBio,
                        publicWritten = publicWritten,
                        publicFavorites = publicFavorites
                    )
                )
            },
            enabled = !busy && usernameAllowsSave,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    busy -> "Salvando…"
                    isLoggedIn -> "Salvar perfil (online)"
                    else -> "Salvar perfil neste aparelho"
                }
            )
        }
        if (status != null) {
            Text(
                status.first,
                color = if (status.second) DangerColor else SuccessColor,
                fontSize = 13.sp
            )
        }
    }
}
