package gui.pgcr.storyteller

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

private fun hexOf(color: Color): String = "%06X".format(color.toArgb() and 0xFFFFFF)

@Composable
internal fun SettingsAppearanceScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit
) {
    // 0 = primária, 1 = secundária, 2 = terciária (do tema personalizado em edição)
    var editingRole by remember { mutableStateOf<Int?>(null) }
    val baseIsDark = themeColorsFor(settings.copy(amoled = false)).isDark

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Tema do aplicativo", color = PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Escolha a paleta de cores da interface principal.", color = SecondaryText, fontSize = 13.sp)

        THEME_GROUPS.forEach { (groupTitle, options) ->
            Text(
                groupTitle,
                color = SecondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp)
            )
            options.forEach { (id, label) ->
                val selected = settings.themeId == id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSettingsChange(settings.copy(themeId = id)) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) Accent.copy(alpha = 0.16f) else CardColor
                    ),
                    border = if (selected) BorderStroke(1.dp, Accent) else null,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onSettingsChange(settings.copy(themeId = id)) },
                            colors = RadioButtonDefaults.colors(selectedColor = Accent)
                        )
                        Text(
                            label,
                            color = if (selected) PrimaryText else SecondaryText,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        AppThemeMiniPreview(
                            colors = themeColorsFor(settings.copy(themeId = id, amoled = settings.amoled))
                        )
                    }
                }
                if (selected && id.startsWith("custom_")) {
                    CustomThemeEditor(
                        settings = settings,
                        light = id == "custom_light",
                        onPick = { editingRole = it },
                        onReset = {
                            val d = AppSettings()
                            onSettingsChange(
                                if (id == "custom_light") settings.copy(
                                    customLightPrimary = d.customLightPrimary,
                                    customLightSecondary = d.customLightSecondary,
                                    customLightTertiary = d.customLightTertiary
                                ) else settings.copy(
                                    customDarkPrimary = d.customDarkPrimary,
                                    customDarkSecondary = d.customDarkSecondary,
                                    customDarkTertiary = d.customDarkTertiary
                                )
                            )
                        }
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Modo AMOLED", color = PrimaryText, fontWeight = FontWeight.Medium)
                    Text(
                        if (baseIsDark) "Fundo preto puro nos temas escuros, para economizar bateria em telas OLED."
                        else "Disponível apenas com um tema escuro selecionado.",
                        color = SecondaryText,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = settings.amoled && baseIsDark,
                    enabled = baseIsDark,
                    onCheckedChange = { onSettingsChange(settings.copy(amoled = it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = OnAccent)
                )
            }
        }
    }

    editingRole?.let { role ->
        val light = settings.themeId == "custom_light"
        val current = Color(
            (when (role) {
                0 -> if (light) settings.customLightPrimary else settings.customDarkPrimary
                1 -> if (light) settings.customLightSecondary else settings.customDarkSecondary
                else -> if (light) settings.customLightTertiary else settings.customDarkTertiary
            }).toInt()
        )
        ColorPickerDialog(
            title = CUSTOM_ROLES[role].first,
            initial = current,
            onDismiss = { editingRole = null },
            themePreview = { picked ->
                themeColorsFor(withCustomColor(settings, light, role, picked.toArgbLong()).copy(amoled = settings.amoled))
            },
            onConfirm = { picked ->
                onSettingsChange(withCustomColor(settings, light, role, picked.toArgbLong()))
                editingRole = null
            }
        )
    }
}

/** Devolve [settings] com a cor [argb] aplicada ao papel (0 primária, 1 secundária, 2 terciária) do tema personalizado. */
private fun withCustomColor(settings: AppSettings, light: Boolean, role: Int, argb: Long): AppSettings =
    if (light) when (role) {
        0 -> settings.copy(customLightPrimary = argb)
        1 -> settings.copy(customLightSecondary = argb)
        else -> settings.copy(customLightTertiary = argb)
    } else when (role) {
        0 -> settings.copy(customDarkPrimary = argb)
        1 -> settings.copy(customDarkSecondary = argb)
        else -> settings.copy(customDarkTertiary = argb)
    }

private val CUSTOM_ROLES = listOf(
    "Primária" to "Destaque: botões, seleção e links",
    "Secundária" to "Fundo do app (os cartões se ajustam sozinhos)",
    "Terciária" to "Textos secundários e detalhes"
)

@Composable
private fun CustomThemeEditor(
    settings: AppSettings,
    light: Boolean,
    onPick: (Int) -> Unit,
    onReset: () -> Unit
) {
    val colors = if (light) {
        listOf(settings.customLightPrimary, settings.customLightSecondary, settings.customLightTertiary)
    } else {
        listOf(settings.customDarkPrimary, settings.customDarkSecondary, settings.customDarkTertiary)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Cores do tema", color = PrimaryText, fontWeight = FontWeight.SemiBold)
            Text(
                if (light) "Tema sempre claro: se o fundo escolhido for escuro, ele é clareado. Destaque e textos ganham contraste automático."
                else "Tema sempre escuro: se o fundo escolhido for claro, ele é escurecido. Destaque e textos ganham contraste automático.",
                color = SecondaryText,
                fontSize = 12.sp
            )
            CUSTOM_ROLES.forEachIndexed { index, (name, hint) ->
                val color = Color(colors[index].toInt())
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(index) }
                        .padding(vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(1.dp, SecondaryText.copy(alpha = 0.5f), CircleShape)
                    )
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(name, color = PrimaryText, fontWeight = FontWeight.Medium)
                        Text(hint, color = SecondaryText, fontSize = 12.sp)
                    }
                    Text("#${hexOf(color)}", color = SecondaryText, fontSize = 12.sp)
                }
            }
            TextButton(onClick = onReset) { Text("Restaurar cores padrão", color = Accent) }
        }
    }
}

private val PRESET_SWATCHES = listOf(
    0xFFB69CFF, 0xFF6B4EFF, 0xFF7EB6FF, 0xFF1F6FD1, 0xFF4CD6A5, 0xFFFFB454, 0xFFFF6B8A, 0xFFE53935,
    0xFF000000, 0xFF101014, 0xFF1D1D25, 0xFF2B2B33, 0xFFF4F1EA, 0xFFFFFFFF, 0xFFAAA7B5, 0xFF6A6475
)

/** Seletor de cor simples: matiz / saturação / brilho, campo hexadecimal e cores prontas. */
@Composable
internal fun ColorPickerDialog(
    title: String,
    initial: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
    /** Se informado, mostra como o app ficaria com a cor escolhida (já com os ajustes automáticos). */
    themePreview: ((Color) -> AppThemeColors)? = null
) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) } }
    var hue by remember { mutableStateOf(start[0]) }
    var sat by remember { mutableStateOf(start[1]) }
    var value by remember { mutableStateOf(start[2]) }
    var hex by remember { mutableStateOf(hexOf(initial)) }

    fun currentColor() = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
    fun applyColor(color: Color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hue = hsv[0]; sat = hsv[1]; value = hsv[2]
        hex = hexOf(color)
    }

    val sliderColors = SliderDefaults.colors(
        thumbColor = Accent,
        activeTrackColor = Accent,
        inactiveTrackColor = SecondaryText.copy(alpha = 0.3f)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardColor,
        titleContentColor = PrimaryText,
        textContentColor = SecondaryText,
        title = { Text("Cor $title") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(currentColor())
                        .border(1.dp, SecondaryText.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                )
                themePreview?.let { build ->
                    Text("Como o app vai ficar", fontSize = 12.sp)
                    AppThemeMiniPreview(colors = build(currentColor()), modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                Text("Matiz", fontSize = 12.sp)
                Slider(
                    value = hue,
                    onValueChange = { hue = it; hex = hexOf(currentColor()) },
                    valueRange = 0f..360f,
                    colors = sliderColors
                )
                Text("Saturação", fontSize = 12.sp)
                Slider(
                    value = sat,
                    onValueChange = { sat = it; hex = hexOf(currentColor()) },
                    valueRange = 0f..1f,
                    colors = sliderColors
                )
                Text("Brilho", fontSize = 12.sp)
                Slider(
                    value = value,
                    onValueChange = { value = it; hex = hexOf(currentColor()) },
                    valueRange = 0f..1f,
                    colors = sliderColors
                )
                OutlinedTextField(
                    value = hex,
                    onValueChange = { raw ->
                        val cleaned = raw.removePrefix("#").filter { it.isLetterOrDigit() }.take(6).uppercase()
                        hex = cleaned
                        if (cleaned.length == 6 && cleaned.all { it in "0123456789ABCDEF" }) {
                            val parsed = Color(("FF$cleaned").toLong(16).toInt())
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(parsed.toArgb(), hsv)
                            hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                        }
                    },
                    prefix = { Text("#") },
                    label = { Text("Hexadecimal") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text("Cores prontas", fontSize = 12.sp)
                PRESET_SWATCHES.chunked(8).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { argb ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb.toInt()))
                                    .border(1.dp, SecondaryText.copy(alpha = 0.5f), CircleShape)
                                    .clickable { applyColor(Color(argb.toInt())) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentColor()) }) { Text("Aplicar", color = Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = SecondaryText) }
        }
    )
}
