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
import androidx.compose.ui.graphics.luminance
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
    val secondaryText: Color,
    /** Cor dos textos principais: branca nos temas escuros, preta nos claros. */
    val primaryText: Color = Color.White,
    /** Cor do texto/ícone sobre o destaque (botões). */
    val onAccent: Color = Color(0xFF17121F),
    val isDark: Boolean = true
)

/** Monta um tema a partir das 4 cores base; texto principal e texto sobre o destaque saem da luminância. */
internal fun buildAppTheme(background: Color, card: Color, accent: Color, secondaryText: Color): AppThemeColors {
    val dark = background.luminance() < 0.5f
    return AppThemeColors(
        background = background,
        card = card,
        accent = accent,
        secondaryText = secondaryText,
        primaryText = if (dark) Color.White else Color(0xFF121212),
        onAccent = if (accent.luminance() > 0.35f) Color(0xFF17121F) else Color.White,
        isDark = dark
    )
}

/** Razão de contraste WCAG entre duas cores (1 = igual, 21 = preto sobre branco). */
internal fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/** Aproxima [fg] de branco/preto (conforme [bg]) até atingir [minRatio] de contraste; mantém o matiz o quanto der. */
internal fun ensureContrast(fg: Color, bg: Color, minRatio: Float): Color {
    if (contrastRatio(fg, bg) >= minRatio) return fg
    val target = if (bg.luminance() < 0.5f) Color.White else Color.Black
    var t = 0f
    var out = fg
    while (t < 1f && contrastRatio(out, bg) < minRatio) {
        t += 0.05f
        out = lerp(fg, target, t.coerceAtMost(1f))
    }
    return out
}

/** Escurece/clareia [c] (mantendo o matiz) até a luminância ficar no limite pedido. */
private fun clampLuminance(c: Color, maxLum: Float? = null, minLum: Float? = null): Color {
    var out = c
    if (maxLum != null && out.luminance() > maxLum) {
        var t = 0f
        while (t < 1f && out.luminance() > maxLum) { t += 0.02f; out = lerp(c, Color.Black, t.coerceAtMost(1f)) }
    }
    if (minLum != null && out.luminance() < minLum) {
        var t = 0f
        while (t < 1f && out.luminance() < minLum) { t += 0.02f; out = lerp(c, Color.White, t.coerceAtMost(1f)) }
    }
    return out
}

/**
 * [forceDark]: true = o tema SEMPRE fica escuro (o fundo escolhido é escurecido se for claro demais),
 * false = SEMPRE claro (fundo clareado se for escuro demais), null = decide pela luminância (temas prontos).
 * O destaque e o texto secundário ganham contraste mínimo contra o fundo/cartão, para nada ficar ilegível.
 */
private fun customTheme(primary: Long, secondary: Long, tertiary: Long, forceDark: Boolean? = null): AppThemeColors {
    var bg = Color(secondary.toInt()).copy(alpha = 1f)
    when (forceDark) {
        true -> bg = clampLuminance(bg, maxLum = 0.18f)
        false -> bg = clampLuminance(bg, minLum = 0.60f)
        null -> {}
    }
    val dark = bg.luminance() < 0.5f
    // Cartões: levemente mais claros que o fundo nos escuros, quase brancos nos claros.
    val card = if (dark) lerp(bg, Color.White, 0.07f) else lerp(bg, Color.White, 0.7f)
    val accent = Color(primary.toInt()).copy(alpha = 1f)
    val tertiaryColor = Color(tertiary.toInt()).copy(alpha = 1f)
    return if (forceDark == null) {
        buildAppTheme(bg, card, accent, tertiaryColor)
    } else {
        buildAppTheme(bg, card, ensureContrast(accent, card, 3f), ensureContrast(tertiaryColor, card, 4.5f))
    }
}

/** Temas prontos criados a partir das combinações do usuário: id -> (primária, secundária, terciária). */
private val PRESET_CUSTOM_THEMES: Map<String, Triple<Long, Long, Long>> = mapOf(
    "tangerina" to Triple(0xFF005433, 0xFFFF640F, 0xFF833B8F),
    "ambar" to Triple(0xFF004DAB, 0xFFFF8F00, 0xFF9E4255),
    "floresta" to Triple(0xFF75A20E, 0xFF1B332F, 0xFFBA5BBB),
    // Destaque e textos secundários escurecidos (no original eram branco e laranja-claro, ilegíveis no fundo ciano).
    "piscina" to Triple(0xFF00606E, 0xFF00F3FF, 0xFFB35C00),
    "pergaminho" to Triple(0xFF1E778C, 0xFFF4DDA9, 0xFF503B75),
    "meia_noite" to Triple(0xFF09656C, 0xFF1E1B33, 0xFFB55858),
    "carmim" to Triple(0xFF1490E8, 0xFFA02825, 0xFF5391AA),
    "musgo" to Triple(0xFFAE1E1E, 0xFF4D6461, 0xFF97AF55)
)

internal fun themeColorsFor(settings: AppSettings): AppThemeColors {
    PRESET_CUSTOM_THEMES[settings.themeId]?.let { (p, s, t) ->
        val preset = customTheme(p, s, t)
        return if (settings.amoled && preset.isDark) {
            preset.copy(background = Color.Black, card = lerp(preset.card, Color.Black, 0.4f))
        } else preset
    }
    val base = when (settings.themeId) {
        "amethyst" -> buildAppTheme(Color(0xFF12091C), Color(0xFF231532), Color(0xFFD4A5FF), Color(0xFFB9A8C9))
        "night_blue" -> buildAppTheme(Color(0xFF0B1220), Color(0xFF152238), Color(0xFF7EB6FF), Color(0xFF9BB0C9))
        "soft_light" -> buildAppTheme(Color(0xFFF4F1EA), Color(0xFFFFFFFF), Color(0xFF6B4EFF), Color(0xFF6A6475))
        "amethyst_light" -> buildAppTheme(Color(0xFFF5EFFA), Color(0xFFFFFFFF), Color(0xFF7B3FC4), Color(0xFF6E5B82))
        "custom_dark" -> customTheme(settings.customDarkPrimary, settings.customDarkSecondary, settings.customDarkTertiary, forceDark = true)
        "custom_light" -> customTheme(settings.customLightPrimary, settings.customLightSecondary, settings.customLightTertiary, forceDark = false)
        else -> buildAppTheme(Color(0xFF101014), Color(0xFF1D1D25), Color(0xFFB69CFF), Color(0xFFAAA7B5))
    }
    return if (settings.amoled && base.isDark) {
        base.copy(background = Color.Black, card = lerp(base.card, Color.Black, 0.4f))
    } else base
}

internal val appThemeState = mutableStateOf(themeColorsFor(AppSettings()))
internal val Background: Color get() = appThemeState.value.background
internal val CardColor: Color get() = appThemeState.value.card
internal val Accent: Color get() = appThemeState.value.accent
internal val SecondaryText: Color get() = appThemeState.value.secondaryText
/** Texto principal (era Color.White): branco nos temas escuros, preto nos claros. */
internal val PrimaryText: Color get() = appThemeState.value.primaryText
/** Texto sobre botões/chips preenchidos com a cor de destaque. */
internal val OnAccent: Color get() = appThemeState.value.onAccent
/** Superfície de apoio (placeholders, balões): mistura do cartão com o destaque. */
internal val SurfaceAlt: Color get() = appThemeState.value.let { lerp(it.card, it.accent, 0.18f) }
internal val DangerColor: Color get() = if (appThemeState.value.isDark) Color(0xFFFF8B9A) else Color(0xFFB3261E)
internal val SuccessColor: Color get() = if (appThemeState.value.isDark) Color(0xFF9DE0B8) else Color(0xFF1B6B3A)
internal val WarnColor: Color get() = if (appThemeState.value.isDark) Color(0xFFFFD18A) else Color(0xFF8A5A00)
internal val InfoColor: Color get() = if (appThemeState.value.isDark) Color(0xFF8BB8FF) else Color(0xFF1F5FBF)


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
    "dawn_mist" -> ReaderThemeColors( // claro, azulado e frio
        background = Color(0xFFF1F5F9),
        text = Color(0xFF1F2933),
        secondary = Color(0xFF5B6B7A),
        accent = Color(0xFF2F6FA3),
        surface = Color(0xFFE2E9F0)
    )
    "forest_night" -> ReaderThemeColors( // escuro, verde-musgo
        background = Color(0xFF0E1A16),
        text = Color(0xFFD5E6DC),
        secondary = Color(0xFF8FA89B),
        accent = Color(0xFF6FCF97),
        surface = Color(0xFF16261F)
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
    "amethyst_night" to "Amethyst Night",
    "dawn_mist" to "Dawn Mist (claro)",
    "forest_night" to "Forest Night (escuro)"
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

internal fun resolveFontFamily(id: String, loadCustom: Boolean, context: Context? = null): FontFamily {
    if (id.startsWith("custom:") && context != null) {
        customFontFamily(context, id)?.let { return it }
        return FontFamily.Default
    }
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



internal val THEME_GROUPS = listOf(
    // Os temas prontos ficam no grupo que o app usa para eles (fundo com brilho < 50% = escuro, texto branco).
    "Escuros" to listOf(
        "dark" to "Escuro clássico",
        "amethyst" to "Ametista",
        "night_blue" to "Noite azul",
        "tangerina" to "Tangerina",
        "ambar" to "Âmbar",
        "floresta" to "Floresta",
        "meia_noite" to "Meia-noite",
        "carmim" to "Carmim",
        "musgo" to "Musgo"
    ),
    "Claros" to listOf(
        "soft_light" to "Claro suave",
        "amethyst_light" to "Ametista claro",
        "piscina" to "Piscina",
        "pergaminho" to "Pergaminho"
    ),
    "Personalizados" to listOf(
        "custom_dark" to "Personalizado escuro",
        "custom_light" to "Personalizado claro"
    )
)

internal val THEME_OPTIONS = THEME_GROUPS.flatMap { it.second }
