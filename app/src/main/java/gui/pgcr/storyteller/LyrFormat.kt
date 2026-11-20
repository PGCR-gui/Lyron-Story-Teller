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

// O contêiner usa uma chave do formato compartilhada por versões compatíveis do app.
// AES-GCM autentica o conteúdo; o prefixo também identifica a família de arquivos .lyr.
internal val LYR_MAGIC = "LYRON01!".toByteArray(Charsets.US_ASCII)
// LYRON02! = arquivo editável protegido por senha: a chave AES depende também da senha do autor.
internal val LYR_MAGIC_PROTECTED = "LYRON02!".toByteArray(Charsets.US_ASCII)
internal const val LYR_MIN_PASSWORD_LENGTH = 6

/** Senha ausente ou incorreta ao abrir um .lyr editável. */
internal class LyrPasswordException(message: String) : IllegalArgumentException(message)
internal const val LYR_FORMAT_SECRET = "LyronStory-LYR-format-v1-7f3c91b8d24e6a50"
internal const val LYR_KDF_ITERATIONS = 160_000
internal const val MAX_LYR_FILE_BYTES = 32 * 1024 * 1024
internal const val MAX_LYR_JSON_BYTES = 6 * 1024 * 1024
internal const val MAX_LYR_MEDIA_ENTRY_BYTES = 10 * 1024 * 1024
internal const val MAX_LYR_MEDIA_TOTAL_BYTES = 20 * 1024 * 1024
internal const val MAX_LYR_MEDIA_COUNT = 200

internal fun storyFileName(title: String): String {
    val safeTitle = title.trim().replace(Regex("[^\\p{L}\\p{N}._-]+"), "_").trim('.', '_').take(64)
    return "${safeTitle.ifBlank { "historia" }}.lyr"
}

internal fun readBoundedBytes(input: InputStream, limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        require(total <= limit) { "O arquivo ultrapassa o limite suportado pelo app." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

internal fun copyBounded(input: InputStream, output: java.io.OutputStream, limit: Long): Long {
    val buffer = ByteArray(8192)
    var total = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        require(total <= limit) { "O arquivo ultrapassa o limite suportado pelo app." }
        output.write(buffer, 0, count)
    }
    return total
}

internal fun deriveLyrKey(salt: ByteArray, userPassword: String? = null): SecretKeySpec {
    val secret = if (userPassword == null) LYR_FORMAT_SECRET else LYR_FORMAT_SECRET + "\u0000" + userPassword
    val password = secret.toCharArray()
    val spec = PBEKeySpec(password, salt, LYR_KDF_ITERATIONS, 256)
    return try {
        SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    } finally {
        spec.clearPassword()
        password.fill('\u0000')
    }
}

/** true se o .lyr é editável e protegido por senha; lança erro se não for um .lyr do app. */
internal fun lyrRequiresPassword(context: Context, uri: Uri): Boolean {
    val input = context.contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("Não foi possível abrir o arquivo selecionado.")
    input.use { source ->
        val magic = readExactBytes(source, LYR_MAGIC.size)
        return when {
            magic.contentEquals(LYR_MAGIC_PROTECTED) -> true
            magic.contentEquals(LYR_MAGIC) -> false
            else -> throw IllegalArgumentException("Este arquivo não é um .lyr compatível com o Lyron Story Teller.")
        }
    }
}

internal fun exportStoryToLyr(
    context: Context,
    story: Story,
    destination: java.io.OutputStream,
    editable: Boolean = true,
    password: String? = null
) {
    // Editável = exige senha (arquivo LYRON02!). Somente leitura continua sem senha (LYRON01!).
    require(!editable || (password != null && password.length >= LYR_MIN_PASSWORD_LENGTH)) {
        "Defina uma senha de pelo menos $LYR_MIN_PASSWORD_LENGTH caracteres para exportar como editável."
    }
    // Trava de segurança: história recebida em somente leitura jamais pode ser reexportada como editável.
    require(!editable || story.editable) {
        "Esta história é somente leitura e não pode ser exportada como editável."
    }
    val magic = if (editable) LYR_MAGIC_PROTECTED else LYR_MAGIC
    require(story.chapters.size in 1..500) { "O formato .lyr aceita histórias de 1 a 500 capítulos." }
    val exportedChapterIds = story.chapters.map { UUID.randomUUID().toString() }
    val exportedChapterIdBySourceId = linkedMapOf<String, String>()
    story.chapters.forEachIndexed { index, chapter ->
        if (chapter.id !in exportedChapterIdBySourceId) exportedChapterIdBySourceId[chapter.id] = exportedChapterIds[index]
    }
    val mediaEntries = LinkedHashMap<String, String>()
    fun addImage(uriString: String, entryName: String): String {
        require(mediaEntries.size < MAX_LYR_MEDIA_COUNT) { "A história excede o limite de fotos do formato .lyr." }
        mediaEntries[entryName] = uriString
        return entryName
    }

    val tagsJson = JSONArray()
    story.tags.forEach { tagsJson.put(it) }
    val classificationsJson = JSONArray()
    story.classifications.forEach { classificationsJson.put(it) }
    val genresJson = JSONArray()
    story.genres.forEach { genresJson.put(it) }
    val storyJson = JSONObject()
        .put("format", "lyron-story")
        .put("schemaVersion", 1)
        .put("title", story.title)
        .put("author", story.author)
        .put("genre", story.genre)
        .put("genres", genresJson)
        .put("description", story.description)
        .put("rating", story.rating)
        .put("tags", tagsJson)
        .put("classifications", classificationsJson)
        .put("contentWarnings", story.contentWarnings)
        .put("status", story.status)
        .put("editable", editable)
        .put("coverAsset", story.coverImageUri?.let { addImage(it, "media/cover.bin") } ?: JSONObject.NULL)
    val chaptersJson = JSONArray()
    story.chapters.forEachIndexed { chapterIndex, chapter ->
        val spans = JSONArray()
        chapter.spans.forEach { span ->
            spans.put(JSONObject().put("start", span.start).put("end", span.end)
                .put("bold", span.bold).put("italic", span.italic).put("underline", span.underline).put("strikethrough", span.strikethrough).put("heading", span.heading))
        }
        val paragraphs = JSONArray()
        chapter.paragraphs.forEach { paragraph ->
            paragraphs.put(JSONObject().put("start", paragraph.start).put("end", paragraph.end)
                .put("alignment", paragraph.alignment))
        }
        val choices = JSONArray()
        chapter.choices.forEach { choice ->
            choices.put(JSONObject().put("text", choice.text)
                .put("nextChapterId", choice.nextChapterId?.let { exportedChapterIdBySourceId[it] } ?: JSONObject.NULL)
                .put("endsStory", choice.endsStory).put("id", choice.id))
        }
        val images = JSONArray()
        chapter.images.forEachIndexed { imageIndex, image ->
            val entry = "media/chapter_${chapterIndex}_image_${imageIndex}.bin"
            images.put(JSONObject().put("asset", addImage(image.uri, entry))
                .put("position", image.position).put("caption", image.caption).put("id", image.id))
        }
        chaptersJson.put(JSONObject().put("title", chapter.title).put("body", chapter.body)
            .put("spans", spans).put("paragraphs", paragraphs).put("id", exportedChapterIds[chapterIndex])
            .put("choices", choices).put("isEnding", chapter.isEnding)
            .put("endingMessage", chapter.endingMessage).put("images", images).put("isDraft", chapter.isDraft))
    }
    storyJson.put("chapters", chaptersJson)
    val storyJsonBytes = storyJson.toString().toByteArray(Charsets.UTF_8)
    require(storyJsonBytes.size <= MAX_LYR_JSON_BYTES) { "O texto e os capítulos excedem o limite do formato .lyr." }

    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, deriveLyrKey(salt, if (editable) password else null), GCMParameterSpec(128, nonce))
    cipher.updateAAD(magic)
    var mediaBytesTotal = 0L
    try {
        destination.write(magic)
        destination.write(salt)
        destination.write(nonce)
        ZipOutputStream(CipherOutputStream(destination, cipher)).use { zip ->
            zip.putNextEntry(ZipEntry("story.json"))
            zip.write(storyJsonBytes)
            zip.closeEntry()
            mediaEntries.forEach { (entryName, uriString) ->
                val input = context.contentResolver.openInputStream(Uri.parse(uriString))
                    ?: throw IllegalStateException("Não foi possível acessar uma das fotos desta história.")
                zip.putNextEntry(ZipEntry(entryName))
                val copied = input.use { copyBounded(it, zip, MAX_LYR_MEDIA_ENTRY_BYTES.toLong()) }
                require(copied > 0) { "Uma das fotos da história está vazia ou indisponível." }
                mediaBytesTotal += copied
                require(mediaBytesTotal <= MAX_LYR_MEDIA_TOTAL_BYTES) { "As fotos excedem o limite de tamanho para exportação .lyr." }
                zip.closeEntry()
            }
        }
    } catch (error: Exception) {
        runCatching { destination.close() }
        throw error
    }
}

internal fun readExactBytes(input: InputStream, size: Int): ByteArray {
    val result = ByteArray(size)
    var offset = 0
    while (offset < size) {
        val count = input.read(result, offset, size - offset)
        if (count < 0) throw IllegalArgumentException("O arquivo .lyr está incompleto ou inválido.")
        offset += count
    }
    return result
}

internal fun importStoryFromLyr(context: Context, uri: Uri, password: String? = null): Story {
    val stagingDir = File(context.cacheDir, "lyr_import_${UUID.randomUUID()}").apply { mkdirs() }
    val importedFiles = mutableListOf<File>()
    try {
        val encryptedInput = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Não foi possível abrir o arquivo selecionado.")
        val headerSize = LYR_MAGIC.size + 16 + 12
        val archiveFile = File(stagingDir, "payload.zip")
        var importedProtected = false
        encryptedInput.use { source ->
            val header = readExactBytes(source, headerSize)
            val fileMagic = header.copyOfRange(0, LYR_MAGIC.size)
            val protectedFile = fileMagic.contentEquals(LYR_MAGIC_PROTECTED)
            importedProtected = protectedFile
            require(protectedFile || fileMagic.contentEquals(LYR_MAGIC)) {
                "Este arquivo não é um .lyr compatível com o Lyron Story Teller."
            }
            if (protectedFile && password.isNullOrEmpty()) {
                throw LyrPasswordException("Este .lyr editável é protegido por senha. Digite a senha para importar.")
            }
            val saltStart = LYR_MAGIC.size
            val nonceStart = saltStart + 16
            val cipherStart = nonceStart + 12
            val salt = header.copyOfRange(saltStart, nonceStart)
            val nonce = header.copyOfRange(nonceStart, cipherStart)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveLyrKey(salt, if (protectedFile) password else null), GCMParameterSpec(128, nonce))
            cipher.updateAAD(fileMagic)
            var encryptedBytesRead = headerSize.toLong()
            var plainBytesWritten = 0L
            val buffer = ByteArray(8192)
            FileOutputStream(archiveFile).use { archiveOutput ->
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    encryptedBytesRead += count
                    require(encryptedBytesRead <= MAX_LYR_FILE_BYTES) { "O arquivo ultrapassa o limite suportado pelo app." }
                    val plain = cipher.update(buffer, 0, count)
                    if (plain != null && plain.isNotEmpty()) {
                        plainBytesWritten += plain.size
                        require(plainBytesWritten <= MAX_LYR_FILE_BYTES) { "O conteúdo descriptografado excede o limite suportado." }
                        archiveOutput.write(plain)
                    }
                }
                val finalBytes = try {
                    cipher.doFinal()
                } catch (_: AEADBadTagException) {
                    if (protectedFile) throw LyrPasswordException("Senha incorreta (ou arquivo corrompido). Tente novamente.")
                    throw IllegalArgumentException("Não foi possível validar este .lyr. O arquivo pode estar corrompido ou não ser do app.")
                }
                plainBytesWritten += finalBytes.size
                require(plainBytesWritten <= MAX_LYR_FILE_BYTES) { "O conteúdo descriptografado excede o limite suportado." }
                archiveOutput.write(finalBytes)
            }
        }

        var storyJson: JSONObject? = null
        val mediaFiles = LinkedHashMap<String, File>()
        var mediaBytesTotal = 0L
        ZipInputStream(FileInputStream(archiveFile)).use { zip ->
            var entryCount = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= MAX_LYR_MEDIA_COUNT + 1) { "O arquivo contém itens demais." }
                when {
                    entry.name == "story.json" -> {
                        require(storyJson == null) { "O arquivo .lyr contém dados duplicados." }
                        storyJson = JSONObject(String(readBoundedBytes(zip, MAX_LYR_JSON_BYTES), Charsets.UTF_8))
                    }
                    entry.name.matches(Regex("media/[A-Za-z0-9._-]{1,120}")) -> {
                        require(!mediaFiles.containsKey(entry.name)) { "O arquivo .lyr contém fotos duplicadas." }
                        val stagedImage = File(stagingDir, "media_${mediaFiles.size}.bin")
                        val copied = FileOutputStream(stagedImage).use {
                            copyBounded(zip, it, MAX_LYR_MEDIA_ENTRY_BYTES.toLong())
                        }
                        require(copied > 0) { "Uma foto do arquivo .lyr está vazia." }
                        mediaBytesTotal += copied
                        require(mediaBytesTotal <= MAX_LYR_MEDIA_TOTAL_BYTES) { "As fotos do arquivo excedem o limite de importação." }
                        mediaFiles[entry.name] = stagedImage
                    }
                    else -> throw IllegalArgumentException("O arquivo .lyr contém uma estrutura não suportada.")
                }
                zip.closeEntry()
            }
        }
        val data = storyJson ?: throw IllegalArgumentException("Este .lyr não contém uma história válida.")
        require(data.optString("format") == "lyron-story" && data.optInt("schemaVersion") == 1) {
            "A versão deste arquivo .lyr não é compatível com o app."
        }
        val chapterArray = data.optJSONArray("chapters") ?: JSONArray()
        require(chapterArray.length() in 1..500) { "A história deve ter entre 1 e 500 capítulos." }
        val oldChapterIds = (0 until chapterArray.length()).map { index ->
            chapterArray.getJSONObject(index).optString("id").takeIf { it.isNotBlank() && it != "null" } ?: "legacy-chapter-$index"
        }
        require(oldChapterIds.distinct().size == oldChapterIds.size) { "A história contém identificadores de capítulo duplicados." }
        val newChapterIds = oldChapterIds.associateWith { UUID.randomUUID().toString() }
        val storedAssetUris = HashMap<String, String>()
        var assetReferenceCount = 0

        fun storeAsset(name: String?): String? {
            if (name == null) return null
            assetReferenceCount++
            require(assetReferenceCount <= MAX_LYR_MEDIA_COUNT) { "A história excede o limite de fotos do formato .lyr." }
            require(name.matches(Regex("media/[A-Za-z0-9._-]{1,120}"))) { "Referência de foto inválida no .lyr." }
            storedAssetUris[name]?.let { return it }
            val staged = mediaFiles[name] ?: throw IllegalArgumentException("Uma foto da história está ausente no arquivo .lyr.")
            val directory = File(context.filesDir, "lyr_story_media").apply { mkdirs() }
            val permanent = File(directory, "${UUID.randomUUID()}.img")
            importedFiles.add(permanent)
            FileInputStream(staged).use { input -> FileOutputStream(permanent).use { input.copyTo(it) } }
            return Uri.fromFile(permanent).toString().also { storedAssetUris[name] = it }
        }

        val chapters = buildList {
            for (chapterIndex in 0 until chapterArray.length()) {
                val chapter = chapterArray.getJSONObject(chapterIndex)
                val spans = buildList {
                    val array = chapter.optJSONArray("spans") ?: JSONArray()
                    for (index in 0 until array.length()) {
                        val value = array.getJSONObject(index)
                        add(RichSpan(value.optInt("start"), value.optInt("end"), value.optBoolean("bold"), value.optBoolean("italic"), value.optBoolean("underline"), value.optBoolean("strikethrough"), value.optInt("heading")))
                    }
                }
                val paragraphs = buildList {
                    val array = chapter.optJSONArray("paragraphs") ?: JSONArray()
                    for (index in 0 until array.length()) {
                        val value = array.getJSONObject(index)
                        add(RichParagraph(value.optInt("start"), value.optInt("end"), value.optString("alignment", "left")))
                    }
                }
                val choices = buildList {
                    val array = chapter.optJSONArray("choices") ?: JSONArray()
                    for (index in 0 until array.length()) {
                        val choice = array.getJSONObject(index)
                        val oldDestination = choice.optString("nextChapterId").takeIf { it.isNotBlank() && it != "null" }
                        add(StoryChoice(
                            text = choice.optString("text"),
                            nextChapterId = oldDestination?.let { newChapterIds[it] },
                            endsStory = choice.optBoolean("endsStory"),
                            id = UUID.randomUUID().toString()
                        ))
                    }
                }
                val images = buildList {
                    val array = chapter.optJSONArray("images") ?: JSONArray()
                    for (index in 0 until array.length()) {
                        val image = array.getJSONObject(index)
                        val imageUri = storeAsset(image.optString("asset").takeIf { it.isNotBlank() && it != "null" })
                            ?: throw IllegalArgumentException("Uma foto do capítulo está sem arquivo associado.")
                        add(ChapterImage(
                            uri = imageUri,
                            position = image.optInt("position").coerceIn(0, chapter.optString("body").length),
                            caption = image.optString("caption"),
                            id = image.optString("id").ifBlank { UUID.randomUUID().toString() }
                        ))
                    }
                }
                val oldId = oldChapterIds[chapterIndex]
                add(Chapter(
                    title = chapter.optString("title"),
                    body = chapter.optString("body"),
                    spans = spans,
                    paragraphs = paragraphs,
                    id = newChapterIds.getValue(oldId),
                    choices = choices,
                    isEnding = chapter.optBoolean("isEnding"),
                    endingMessage = chapter.optString("endingMessage", "Você chegou a um desfecho desta história."),
                    images = images,
                    isDraft = chapter.optBoolean("isDraft", false)
                ))
            }
        }
        val coverUri = storeAsset(data.optString("coverAsset").takeIf { it.isNotBlank() && it != "null" })
        val tagsJson = data.optJSONArray("tags") ?: JSONArray()
        val tags = buildList {
            for (t in 0 until tagsJson.length()) {
                val tag = tagsJson.optString(t).trim()
                if (tag.isNotBlank()) add(tag)
            }
        }.distinct()
        val classificationsJson = data.optJSONArray("classifications") ?: JSONArray()
        val classifications = buildList {
            for (c in 0 until classificationsJson.length()) {
                val value = classificationsJson.optString(c).trim()
                if (value in STORY_CLASSIFICATIONS) add(value)
            }
        }.distinct()
        val genresJson = data.optJSONArray("genres")
        val genres = if (genresJson != null) {
            buildList {
                for (g in 0 until genresJson.length()) {
                    val value = genresJson.optString(g).trim()
                    if (value.isNotBlank()) add(value)
                }
            }.distinct().take(MAX_STORY_GENRES)
        } else {
            data.optString("genre").trim().takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty()
        }
        // Só o formato com senha (LYRON02!) pode entregar uma história editável. Arquivos sem senha
        // (LYRON01!) entram sempre como somente leitura, mesmo que o JSON interno diga o contrário.
        val editable = importedProtected && data.optBoolean("editable", true)
        val status = data.optString("status", STATUS_IN_PROGRESS).takeIf { it in STORY_STATUSES } ?: STATUS_IN_PROGRESS
        val ratingValue = data.optString("rating").ifBlank { "Livre" }
        return Story(
            title = data.optString("title").ifBlank { "História importada" },
            author = data.optString("author").ifBlank { "Autor" },
            genres = genres,
            description = data.optString("description"),
            rating = ratingValue,
            chapters = chapters,
            coverImageUri = coverUri,
            tags = tags,
            classifications = classifications,
            contentWarnings = data.optString("contentWarnings").ifBlank { defaultContentWarningFor(ratingValue) },
            status = status,
            editable = editable,
            id = UUID.randomUUID().toString()
        )
    } catch (error: Throwable) {
        importedFiles.forEach { it.delete() }
        throw error
    } finally {
        stagingDir.deleteRecursively()
    }
}

internal fun cleanupLyrStoryMedia(context: Context, stories: List<Story>) {
    val directory = File(context.filesDir, "lyr_story_media")
    if (!directory.exists()) return
    val directoryPath = runCatching { directory.canonicalPath }.getOrNull() ?: return
    val referencedPaths = mutableSetOf<String>()
    fun retain(uriString: String?) {
        if (uriString == null) return
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
        if (uri.scheme != "file") return
        val path = uri.path ?: return
        val file = File(path)
        val parentPath = runCatching { file.parentFile?.canonicalPath }.getOrNull()
        if (parentPath == directoryPath) runCatching { file.canonicalPath }.getOrNull()?.let(referencedPaths::add)
    }
    stories.forEach { story ->
        retain(story.coverImageUri)
        story.chapters.forEach { chapter -> chapter.images.forEach { retain(it.uri) } }
    }
    directory.listFiles()?.forEach { file ->
        val canonicalPath = runCatching { file.canonicalPath }.getOrNull()
        if (canonicalPath != null && canonicalPath !in referencedPaths) file.delete()
    }
}

internal fun cleanupInterruptedLyrImports(context: Context) {
    context.cacheDir.listFiles()
        ?.filter { it.isDirectory && it.name.startsWith("lyr_import_") }
        ?.forEach { it.deleteRecursively() }
}
