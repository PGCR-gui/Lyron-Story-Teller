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

internal data class RichSpan(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    /** 0 = texto normal, 1 = título (H1), 2 = subtítulo (H2). */
    val heading: Int = 0
)

internal data class RichParagraph(
    val start: Int,
    val end: Int,
    val alignment: String
)

internal data class StoryChoice(
    val text: String,
    val nextChapterId: String? = null,
    val endsStory: Boolean = false,
    val id: String = UUID.randomUUID().toString()
)

internal data class ChapterImage(
    val uri: String,
    val position: Int,
    val caption: String = "",
    val id: String = UUID.randomUUID().toString()
)

internal data class Chapter(
    val title: String,
    val body: String,
    val spans: List<RichSpan> = emptyList(),
    val paragraphs: List<RichParagraph> = emptyList(),
    val id: String = UUID.nameUUIDFromBytes("${title.trim()}|${body.trim()}".toByteArray()).toString(),
    val choices: List<StoryChoice> = emptyList(),
    val isEnding: Boolean = false,
    val endingMessage: String = "Você chegou a um desfecho desta história.",
    val images: List<ChapterImage> = emptyList(),
    val isDraft: Boolean = false
)

internal data class Story(
    val title: String,
    val author: String,
    val genres: List<String> = emptyList(),
    val description: String,
    val rating: String,
    val chapters: List<Chapter>,
    val coverImageUri: String? = null,
    val tags: List<String> = emptyList(),
    val classifications: List<String> = emptyList(),
    /** Avisos de conteúdo editáveis pelo autor (texto livre). */
    val contentWarnings: String = "",
    /** Em progresso | Completa | Cancelada */
    val status: String = STATUS_IN_PROGRESS,
    /** Se false, a história foi importada/exportada como somente leitura e não pode ser editada. */
    val editable: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis(),
    val remoteId: String? = null,
    val remoteUpdatedAt: String? = null,
    /** Dono da publicação online (id do usuário no Supabase), quando a história veio do catálogo. */
    val remoteOwnerId: String? = null,
    val id: String = UUID.randomUUID().toString()
) {
    val genre: String
        get() = genres.firstOrNull().orEmpty().ifBlank { "Geral" }
    val isChoiceStory: Boolean
        get() = chapters.any { it.choices.isNotEmpty() || it.isEnding }
    /** História com vínculo online (baixada ou publicada) — autor pode abrir o perfil público. */
    val hasOnlineAuthor: Boolean
        get() = !remoteId.isNullOrBlank() || !remoteOwnerId.isNullOrBlank()
}

internal val STORY_CLASSIFICATIONS = listOf("Oneshot", "História Original", "Fanfic")
internal const val CLASSIFICATION_ONESHOT = "Oneshot"
internal const val CLASSIFICATION_ORIGINAL = "História Original"
internal const val CLASSIFICATION_FANFIC = "Fanfic"

internal const val STATUS_IN_PROGRESS = "Em progresso"
internal const val STATUS_COMPLETE = "Completa"
internal const val STATUS_CANCELLED = "Cancelada"
internal val STORY_STATUSES = listOf(STATUS_IN_PROGRESS, STATUS_COMPLETE, STATUS_CANCELLED)

internal val AGE_RATINGS = listOf("Livre", "6 anos", "10 anos", "12 anos", "14 anos", "16 anos", "18 anos")

/** Textos padrão de avisos de conteúdo por classificação indicativa (editáveis pelo autor). */
internal val DEFAULT_CONTENT_WARNINGS = mapOf(
    "Livre" to "Não expõe crianças a conteúdos potencialmente prejudiciais.",
    "6 anos" to "Pode conter temáticas sensíveis, leves situações de tensão, ou conteúdos que exijam maior compreensão, ainda que sem violência ou linguagem inadequada de maior impacto.",
    "10 anos" to "Conteúdo violento ou linguagem inapropriada para crianças, ainda que em menor intensidade.",
    "12 anos" to "As cenas podem conter agressão física, consumo de drogas e insinuação sexual.",
    "14 anos" to "Conteúdos mais violentos e/ou de linguagem sexual mais acentuada.",
    "16 anos" to "Conteúdos mais violentos ou com conteúdo sexual mais intenso, com cenas de tortura, suicídio ou nudez total.",
    "18 anos" to "Conteúdos violentos e sexuais extremos. Cenas de sexo, incesto ou atos repetidos de tortura, mutilação ou abuso sexual."
)

internal fun defaultContentWarningFor(rating: String): String =
    DEFAULT_CONTENT_WARNINGS[rating] ?: DEFAULT_CONTENT_WARNINGS["Livre"]!!

internal fun classifDrawableRes(rating: String): Int = when {
    rating.equals("Livre", ignoreCase = true) -> R.drawable.classif_livre
    rating.startsWith("6") -> R.drawable.classif_6
    rating.startsWith("10") -> R.drawable.classif_10
    rating.startsWith("12") -> R.drawable.classif_12
    rating.startsWith("14") -> R.drawable.classif_14
    rating.startsWith("16") -> R.drawable.classif_16
    rating.startsWith("18") -> R.drawable.classif_18
    else -> R.drawable.classif_livre
}

internal data class UserProfile(
    val fullName: String = "",
    val nickname: String = "",
    /** true = usar nome completo; false = usar apelido/pseudônimo */
    val useFullNameAsAuthor: Boolean = false,
    val photoUri: String? = null,
    /** Nome de usuário único (só existe para quem tem conta online). */
    val username: String = "",
    /** Caminho da foto no bucket "avatars" (null = foto ainda não enviada ao servidor). */
    val avatarPath: String? = null,
    val bio: String = "",
    /** Exibir biografia no perfil público. */
    val publicBio: Boolean = true,
    /** Listar histórias escritas no perfil público. */
    val publicWritten: Boolean = true,
    /** Listar favoritas no perfil público. */
    val publicFavorites: Boolean = false
) {
    val authorName: String
        get() = when {
            useFullNameAsAuthor && fullName.isNotBlank() -> fullName.trim()
            nickname.isNotBlank() -> nickname.trim()
            fullName.isNotBlank() -> fullName.trim()
            else -> "Autor"
        }
}

internal data class AppSettings(
    /** dark | amethyst | night_blue | soft_light | amethyst_light | tangerina | ambar | floresta | piscina | pergaminho | meia_noite | carmim | musgo | custom_dark | custom_light */
    val themeId: String = "dark",
    /** Fundo preto puro nos temas escuros. */
    val amoled: Boolean = false,
    /** Temas personalizados (ARGB): primária = destaque, secundária = fundo, terciária = textos secundários. */
    val customDarkPrimary: Long = 0xFFB69CFF,
    val customDarkSecondary: Long = 0xFF101014,
    val customDarkTertiary: Long = 0xFFAAA7B5,
    val customLightPrimary: Long = 0xFF6B4EFF,
    val customLightSecondary: Long = 0xFFF4F1EA,
    val customLightTertiary: Long = 0xFF6A6475,
    val defaultReaderFontSize: Float = 18f,
    val showSampleStories: Boolean = false,
    val confirmBeforeExport: Boolean = true,
    /** soft_light | sepia | dark_obsidian | amethyst_night | dawn_mist | forest_night */
    val readerThemeId: String = "amethyst_night",
    /** continuous | paginated */
    val readerMode: String = "continuous",
    /** system | serif | sans | mono | id de fonte embutida | custom:<arquivo> (fonte .ttf/.otf carregada) */
    val readerFontFamily: String = "system",
    val appFontFamily: String = "system",
    val loadCustomFonts: Boolean = true,
    /** Multiplicador de espaçamento entre linhas (1.0 = apertado, 1.8 = folgado) */
    val readerLineHeight: Float = 1.55f,
    /** Margem horizontal da página em dp */
    val readerMarginDp: Float = 20f,
    /** Avisar quando uma história favoritada (baixada) recebe capítulos novos. */
    val notifyFavorites: Boolean = true,
    /** Avisar quando um autor seguido publica uma história ou capítulo novo. */
    val notifyFollowedAuthors: Boolean = true
)

internal val STORY_GENRES = listOf(
    "Romance",
    "Dark Romance",
    "Aventura",
    "Fantasia",
    "Ficção Científica",
    "Terror",
    "Suspense",
    "Thriller",
    "Policial",
    "Mistério",
    "Drama",
    "Contemporâneo",
    "Realista",
    "Distópico",
    "Crônicas",
    "Antologia",
    "Histórico",
    "Humor",
    "Comédia",
    "Poesia",
    "LGBTQ+",
    "Slice of Life",
    "Sobrenatural",
    "Urban Fantasy",
    "Comédia Romântica"
)
internal const val MAX_STORY_GENRES = 5

internal val sampleStories = emptyList<Story>()

internal enum class AppScreen {
    HOME, EXPLORE, LIBRARY, PROFILE, SETTINGS,
    SETTINGS_PROFILE, SETTINGS_APPEARANCE, SETTINGS_READER, SETTINGS_NOTIFICATIONS, SETTINGS_CONTENT, SETTINGS_ABOUT,
    DETAILS, STATS, READER, EDITOR, MANAGE_POSTED, AUTHOR_PROFILE, CREATE_ACCOUNT
}

internal data class StoryStats(
    val chapterVisits: Map<String, Int> = emptyMap(),
    val pathTraversals: Map<String, Int> = emptyMap(),
    val endingVisits: Map<String, Int> = emptyMap(),
    val completedReadings: Int = 0
)

internal data class ReadingProgress(
    val chapterIndex: Int = 0,
    val pageIndex: Int = 0,
    val updatedAt: Long = 0L
)
