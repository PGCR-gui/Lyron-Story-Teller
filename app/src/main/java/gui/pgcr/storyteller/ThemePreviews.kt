package gui.pgcr.storyteller

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Texto de exemplo usado em todas as pré-visualizações do leitor. */
internal const val READER_SAMPLE_TITLE = "Capítulo 1 · A porta do farol"
internal const val READER_SAMPLE_TEXT =
    "A chuva batia no vidro quando Iris ouviu o primeiro passo na escada. " +
        "Ninguém mais morava ali — ou foi o que lhe disseram."

/** Miniatura de como o app fica com um tema: fundo, cartão, textos e botão de destaque. */
@Composable
internal fun AppThemeMiniPreview(colors: AppThemeColors, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .width(108.dp)
            .clip(shape)
            .background(colors.background)
            .border(1.dp, colors.secondaryText.copy(alpha = 0.45f), shape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text("Lyron", color = colors.primaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(colors.card)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text("Título", color = colors.primaryText, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("Texto secundário", color = colors.secondaryText, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Clip)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.accent)
                .padding(horizontal = 9.dp, vertical = 3.dp)
        ) {
            Text("Botão", color = colors.onAccent, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun readerFontLabel(fontId: String): String {
    val context = LocalContext.current
    return remember(fontId) {
        READER_FONT_OPTIONS.firstOrNull { it.first == fontId }?.second
            ?: listCustomFonts(context).firstOrNull { it.id == fontId }?.label
            ?: "Sistema"
    }
}

@Composable
private fun rememberReaderFont(fontId: String): FontFamily {
    val context = LocalContext.current
    // Mesma regra do leitor: fontes embutidas e carregadas sempre ficam disponíveis.
    return remember(fontId) { resolveFontFamily(fontId, loadCustom = true, context = context) }
}

/** Uma opção de tema do leitor, já desenhada com as cores do tema e a fonte escolhida. */
@Composable
internal fun ReaderThemeOptionCard(
    themeId: String,
    label: String,
    fontId: String,
    fontSize: Float,
    lineHeightMult: Float,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = readerThemeColors(themeId)
    val font = rememberReaderFont(fontId)
    val shape = RoundedCornerShape(14.dp)
    val previewSize = fontSize.coerceIn(12f, 20f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.background)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Accent else theme.secondary.copy(alpha = 0.4f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                label,
                color = theme.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Text("✓ Em uso", color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            READER_SAMPLE_TEXT,
            color = theme.text,
            fontFamily = font,
            fontSize = previewSize.sp,
            lineHeight = (previewSize * lineHeightMult).sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "Texto secundário e anotações",
            color = theme.secondary,
            fontFamily = font,
            fontSize = 12.sp
        )
    }
}

/** Pré-visualização fiel do leitor: tema, fonte, tamanho, espaçamento e margem atuais. */
@Composable
internal fun ReaderLivePreview(settings: AppSettings, modifier: Modifier = Modifier) {
    val theme = readerThemeColors(settings.readerThemeId)
    val font = rememberReaderFont(settings.readerFontFamily)
    val fontName = readerFontLabel(settings.readerFontFamily)
    val shape = RoundedCornerShape(14.dp)
    val size = settings.defaultReaderFontSize
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.background)
            .border(1.dp, theme.secondary.copy(alpha = 0.4f), shape)
            .padding(horizontal = settings.readerMarginDp.coerceIn(8f, 28f).dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            READER_SAMPLE_TITLE,
            color = theme.accent,
            fontFamily = font,
            fontSize = (size + 2f).coerceAtMost(26f).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            READER_SAMPLE_TEXT,
            color = theme.text,
            fontFamily = font,
            fontSize = size.sp,
            lineHeight = (size * settings.readerLineHeight).sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.padding(top = 2.dp))
        Text(
            "Pré-visualização · $fontName · ${size.toInt()} sp",
            color = theme.secondary,
            fontSize = 11.sp
        )
    }
}

/** Linha de fonte: o nome e uma amostra escritos na própria fonte. */
@Composable
internal fun ReaderFontSample(fontId: String, label: String, modifier: Modifier = Modifier) {
    val font = rememberReaderFont(fontId)
    Column(modifier = modifier) {
        Text(label, color = PrimaryText, fontFamily = font, fontSize = 16.sp)
        Text("Aa Bb Cc 123 — A rápida raposa marrom", color = SecondaryText, fontFamily = font, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
