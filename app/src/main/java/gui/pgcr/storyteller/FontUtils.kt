package gui.pgcr.storyteller

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.IOException

private const val CUSTOM_FONT_PREFIX = "custom:"
private const val MAX_FONT_BYTES = 20L * 1024 * 1024
private val FONT_EXTENSIONS = setOf("ttf", "otf")

internal data class CustomFont(val id: String, val label: String)

private fun fontsDir(context: Context): File = File(context.filesDir, "fonts").apply { mkdirs() }

/** Fontes .ttf/.otf que o usuário carregou (guardadas no armazenamento privado do app). */
internal fun listCustomFonts(context: Context): List<CustomFont> =
    fontsDir(context).listFiles { f -> f.isFile && f.extension.lowercase() in FONT_EXTENSIONS }
        ?.sortedBy { it.name.lowercase() }
        ?.map { CustomFont("$CUSTOM_FONT_PREFIX${it.name}", it.nameWithoutExtension) }
        ?: emptyList()

private val customFamilyCache = HashMap<String, FontFamily>()

/** FontFamily de uma fonte carregada ("custom:<arquivo>"); null se o arquivo sumiu ou é inválido. */
internal fun customFontFamily(context: Context, id: String): FontFamily? {
    if (!id.startsWith(CUSTOM_FONT_PREFIX)) return null
    val name = id.removePrefix(CUSTOM_FONT_PREFIX).substringAfterLast('/')
    val file = File(fontsDir(context), name)
    if (!file.isFile) return null
    val key = "${file.absolutePath}:${file.lastModified()}"
    customFamilyCache[key]?.let { return it }
    return runCatching { FontFamily(Font(file)) }.getOrNull()?.also { customFamilyCache[key] = it }
}

internal fun deleteCustomFont(context: Context, id: String) {
    val name = id.removePrefix(CUSTOM_FONT_PREFIX).substringAfterLast('/')
    runCatching { File(fontsDir(context), name).delete() }
    customFamilyCache.clear()
}

private fun looksLikeFont(file: File): Boolean {
    val head = ByteArray(4)
    val read = runCatching { file.inputStream().use { it.read(head) } }.getOrDefault(-1)
    if (read < 4) return false
    val isTrueType = head[0].toInt() == 0 && head[1].toInt() == 1 && head[2].toInt() == 0 && head[3].toInt() == 0
    val tag = String(head, Charsets.ISO_8859_1)
    return isTrueType || tag == "true" || tag == "OTTO"
}

/** Copia a fonte escolhida para o app. Devolve o id ("custom:<arquivo>") ou o motivo da falha. */
internal fun importCustomFont(context: Context, uri: Uri): Result<String> {
    val resolver = context.contentResolver
    var displayName: String? = null
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) displayName = c.getString(0)
        }
    }
    val raw = displayName ?: uri.lastPathSegment ?: "fonte.ttf"
    val ext = raw.substringAfterLast('.', "").lowercase()
    if (ext !in FONT_EXTENSIONS) return Result.failure(IllegalArgumentException("Escolha um arquivo de fonte .ttf (ou .otf)."))
    val base = raw.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9 _-]"), "_").trim().take(48).ifBlank { "fonte" }
    val dir = fontsDir(context)
    val target = File(dir, "$base.$ext")
    val tmp = File(dir, "$base.tmp")
    return try {
        val input = resolver.openInputStream(uri) ?: return Result.failure(IOException("Não foi possível abrir o arquivo."))
        input.use { src ->
            tmp.outputStream().use { out ->
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val n = src.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_FONT_BYTES) throw IOException("A fonte passa de 20 MB.")
                    out.write(buffer, 0, n)
                }
            }
        }
        if (!looksLikeFont(tmp)) throw IOException("O arquivo não parece ser uma fonte TrueType/OpenType válida.")
        Typeface.createFromFile(tmp) // lança exceção se o Android não conseguir ler a fonte
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) throw IOException("Não foi possível salvar a fonte.")
        customFamilyCache.clear()
        Result.success("$CUSTOM_FONT_PREFIX${target.name}")
    } catch (e: Exception) {
        Result.failure(IOException(e.message ?: "Não foi possível carregar a fonte."))
    } finally {
        if (tmp.exists()) tmp.delete()
    }
}

/** Lista "Minhas fontes" (com remover) e o botão para carregar um .ttf. */
@Composable
internal fun CustomFontSection(
    selectedId: String,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    var fonts by remember { mutableStateOf(listCustomFonts(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importCustomFont(context, uri)
                .onSuccess { id ->
                    fonts = listCustomFonts(context)
                    error = null
                    onSelect(id)
                }
                .onFailure { error = it.message ?: "Não foi possível carregar a fonte." }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Minhas fontes", color = SecondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        if (fonts.isEmpty()) {
            Text("Nenhuma fonte carregada ainda.", color = SecondaryText, fontSize = 13.sp)
        }
        fonts.forEach { font ->
            val selected = selectedId == font.id
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(font.id) }
                    .padding(vertical = 2.dp)
            ) {
                RadioButton(
                    selected = selected,
                    onClick = { onSelect(font.id) },
                    colors = RadioButtonDefaults.colors(selectedColor = Accent)
                )
                Text(
                    font.label,
                    color = if (selected) PrimaryText else SecondaryText,
                    fontFamily = customFontFamily(context, font.id) ?: FontFamily.Default,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    deleteCustomFont(context, font.id)
                    fonts = listCustomFonts(context)
                    if (selected) onSelect("system")
                }) { Text("Remover", color = DangerColor, fontSize = 12.sp) }
            }
        }
        OutlinedButton(
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("＋  Carregar fonte (.ttf)", color = Accent) }
        error?.let { Text(it, color = DangerColor, fontSize = 12.sp) }
    }
}
