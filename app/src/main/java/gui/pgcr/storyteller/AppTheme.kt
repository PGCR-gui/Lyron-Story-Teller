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

internal data class AppThemeColors(
    val background: Color,
    val card: Color,
    val accent: Color,
    val secondaryText: Color
)

internal fun themeColorsFor(themeId: String): AppThemeColors = when (themeId) {
    "amethyst" -> AppThemeColors(
        background = Color(0xFF12091C),
        card = Color(0xFF231532),
        accent = Color(0xFFD4A5FF),
        secondaryText = Color(0xFFB9A8C9)
    )
    "night_blue" -> AppThemeColors(
        background = Color(0xFF0B1220),
        card = Color(0xFF152238),
        accent = Color(0xFF7EB6FF),
        secondaryText = Color(0xFF9BB0C9)
    )
    "soft_light" -> AppThemeColors(
        background = Color(0xFFF4F1EA),
        card = Color(0xFFFFFFFF),
        accent = Color(0xFF6B4EFF),
        secondaryText = Color(0xFF6A6475)
    )
    else -> AppThemeColors(
        background = Color(0xFF101014),
        card = Color(0xFF1D1D25),
        accent = Color(0xFFB69CFF),
        secondaryText = Color(0xFFAAA7B5)
    )
}

internal val appThemeState = mutableStateOf(themeColorsFor("dark"))
internal val Background: Color get() = appThemeState.value.background
internal val CardColor: Color get() = appThemeState.value.card
internal val Accent: Color get() = appThemeState.value.accent
internal val SecondaryText: Color get() = appThemeState.value.secondaryText


internal data class ReaderThemeColors(
    val background: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color,
    val surface: Color
)

internal fun readerThemeColors(id: String): ReaderThemeColors = when (id) {
    "soft_light" -> ReaderThemeColors(
        background = Color(0xFFF7F3E8),
        text = Color(0xFF2A2A2A),
        secondary = Color(0xFF6B6B6B),
        accent = Color(0xFF5B4A3A),
        surface = Color(0xFFEFE9DC)
    )
    "sepia" -> ReaderThemeColors(
        background = Color(0xFFE8D9C0),
        text = Color(0xFF3D2B1F),
        secondary = Color(0xFF7A5C45),
        accent = Color(0xFF8B5E3C),
        surface = Color(0xFFDCC9A8)
    )
    "dark_obsidian" -> ReaderThemeColors(
        background = Color(0xFF0A0A0A),
        text = Color(0xFFD0D0D0),
        secondary = Color(0xFF8A8A8A),
        accent = Color(0xFFB0B0B0),
        surface = Color(0xFF161616)
    )
    else -> ReaderThemeColors( // amethyst_night
        background = Color(0xFF17121F),
        text = Color(0xFFE8E0F0),
        secondary = Color(0xFFA89BB8),
        accent = Color(0xFFC9A0FF),
        surface = Color(0xFF241C30)
    )
}

internal val READER_THEME_OPTIONS = listOf(
    "soft_light" to "Soft Light",
    "sepia" to "Sepia Literary",
    "dark_obsidian" to "Dark Obsidian",
    "amethyst_night" to "Amethyst Night"
)

internal val READER_FONT_OPTIONS = listOf(
    "system" to "Sistema",
    "serif" to "Serif",
    "sans" to "Sans-Serif",
    "mono" to "Monoespaçada",
    "lyron_wordmark" to "Lyron Wordmark",
    "shareit" to "Shareit",
    "ryga" to "Ryga",
    "pixelise" to "Pixelise",
    "matcha" to "Matcha Style",
    "julia" to "Julia Metal",
    "grim" to "Grim Creeper",
    "gak" to "Gak Tahu",
    "terminal" to "Terminal 16",
    "vcr" to "Better VCR"
)

/** Fontes embutidas (res/font). Usadas no leitor e, se ativado, no app. */
internal val LyronFontWordmark by lazy {
    FontFamily(Font(R.font.lyron_wordmark))
}
internal val LyronFontShareit by lazy {
    FontFamily(Font(R.font.shareit))
}
internal val LyronFontRyga by lazy {
    FontFamily(Font(R.font.ryga))
}
internal val LyronFontPixelise by lazy {
    FontFamily(Font(R.font.pixelise))
}
internal val LyronFontMatcha by lazy {
    FontFamily(Font(R.font.matcha_style))
}
internal val LyronFontJulia by lazy {
    FontFamily(Font(R.font.juliametal_regular))
}
internal val LyronFontGrim by lazy {
    FontFamily(Font(R.font.grim_creeper))
}
internal val LyronFontGak by lazy {
    FontFamily(Font(R.font.gak_tahu))
}
internal val LyronFontTerminal by lazy {
    FontFamily(Font(R.font.terminal_16_unicode))
}
internal val LyronFontVcr by lazy {
    FontFamily(Font(R.font.better_vcr))
}

internal fun resolveFontFamily(id: String, loadCustom: Boolean): FontFamily {
    if (!loadCustom) {
        return when (id) {
            "serif" -> FontFamily.Serif
            "sans" -> FontFamily.SansSerif
            "mono" -> FontFamily.Monospace
            else -> FontFamily.Default
        }
    }
    return when (id) {
        "serif" -> FontFamily.Serif
        "sans" -> FontFamily.SansSerif
        "mono" -> FontFamily.Monospace
        "lyron_wordmark" -> LyronFontWordmark
        "shareit" -> LyronFontShareit
        "ryga" -> LyronFontRyga
        "pixelise" -> LyronFontPixelise
        "matcha" -> LyronFontMatcha
        "julia" -> LyronFontJulia
        "grim" -> LyronFontGrim
        "gak" -> LyronFontGak
        "terminal" -> LyronFontTerminal
        "vcr" -> LyronFontVcr
        else -> FontFamily.Default
    }
}



internal val THEME_OPTIONS = listOf(
    "dark" to "Escuro clássico",
    "amethyst" to "Ametista",
    "night_blue" to "Noite azul",
    "soft_light" to "Claro suave"
)
