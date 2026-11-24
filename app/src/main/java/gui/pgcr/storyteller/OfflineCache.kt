package gui.pgcr.storyteller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Cache offline: catálogo de histórias públicas, capas e arquivos .lyr já baixados.
 * Fica em filesDir (e não em cacheDir) para o sistema não apagar quando precisar de espaço;
 * cada pasta tem limite de arquivos e remove os mais antigos.
 */

/** true quando o catálogo na tela veio do cache porque a rede falhou. */
internal val catalogOffline = mutableStateOf(false)

private val cacheJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun cacheRoot(context: Context): File = File(context.filesDir, "offline_cache").apply { mkdirs() }
private fun catalogFile(context: Context): File = File(cacheRoot(context), "catalog.json")
private fun coversDir(context: Context): File = File(cacheRoot(context), "covers").apply { mkdirs() }
private fun lyrDir(context: Context): File = File(cacheRoot(context), "lyr").apply { mkdirs() }

private fun sha(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(40)

private fun coverKey(coverPath: String?, version: String?): String = sha("$coverPath|${version.orEmpty()}")
private fun lyrKey(remote: RemoteStory): String = sha("${remote.id}|${remote.updatedAt.orEmpty()}|${remote.filePath}")

/** Mantém só os [maxFiles] arquivos mais recentes da pasta. */
private fun prune(dir: File, maxFiles: Int) {
    val files = dir.listFiles()?.filter { it.isFile } ?: return
    if (files.size <= maxFiles) return
    files.sortedByDescending { it.lastModified() }.drop(maxFiles).forEach { runCatching { it.delete() } }
}

// ---------------------------------------------------------------------------
// Catálogo de histórias públicas
// ---------------------------------------------------------------------------

internal fun saveCatalogCache(context: Context, stories: List<RemoteStory>) {
    runCatching {
        val tmp = File(cacheRoot(context), "catalog.json.tmp")
        tmp.writeText(cacheJson.encodeToString(ListSerializer(RemoteStory.serializer()), stories))
        if (!tmp.renameTo(catalogFile(context))) {
            catalogFile(context).writeText(tmp.readText())
            tmp.delete()
        }
    }
}

internal fun loadCatalogCache(context: Context): List<RemoteStory>? {
    val file = catalogFile(context)
    if (!file.isFile) return null
    return runCatching {
        cacheJson.decodeFromString(ListSerializer(RemoteStory.serializer()), file.readText())
    }.getOrNull()
}

/** Busca o catálogo e atualiza o cache; sem rede, devolve a última versão salva (e liga [catalogOffline]). */
internal suspend fun fetchPublicStoriesCached(context: Context, limit: Int = 40): List<RemoteStory> {
    return try {
        val all = fetchPublicStories(limit = Int.MAX_VALUE)
        saveCatalogCache(context, all)
        catalogOffline.value = false
        all.take(limit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val cached = loadCatalogCache(context)
        if (cached != null) {
            catalogOffline.value = true
            cached.take(limit)
        } else {
            throw e
        }
    }
}

// ---------------------------------------------------------------------------
// Capas
// ---------------------------------------------------------------------------

private val coverMemory = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

private fun decodeSampled(bytes: ByteArray, maxDimension: Int = 1024): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

/** Baixa os bytes da capa. */
private fun fetchCoverBytesFromUrl(url: String): ByteArray? = runCatching {
    val conn = java.net.URL(url).openConnection()
    conn.connectTimeout = 8000
    conn.readTimeout = 8000
    conn.getInputStream().use { it.readBytes() }
}.getOrNull()

/**
 * Capa de uma história do catálogo: memória → disco → rede (e salva no disco).
 * [version] (ex.: updatedAt) entra na chave, assim uma capa trocada não fica velha no cache.
 * [maxDimension] limita o tamanho decodificado (miniaturas da grade usam bem menos memória que a capa grande dos detalhes).
 */
internal fun loadCoverBitmap(
    context: Context,
    coverPath: String?,
    version: String? = null,
    maxDimension: Int = 1024
): Bitmap? {
    val url = publicCoverUrl(coverPath) ?: return null
    val key = coverKey(coverPath, version)
    val memoryKey = "$key@$maxDimension"
    coverMemory.get(memoryKey)?.let { return it }
    val file = File(coversDir(context), key)
    if (file.isFile) {
        runCatching { decodeSampled(file.readBytes(), maxDimension) }.getOrNull()?.let {
            file.setLastModified(System.currentTimeMillis())
            coverMemory.put(memoryKey, it)
            return it
        }
        runCatching { file.delete() }
    }
    val bytes = fetchCoverBytesFromUrl(url) ?: return null
    val bitmap = runCatching { decodeSampled(bytes, maxDimension) }.getOrNull() ?: return null
    runCatching {
        file.writeBytes(bytes)
        prune(coversDir(context), COVER_CACHE_MAX_FILES)
    }
    coverMemory.put(memoryKey, bitmap)
    return bitmap
}

private const val COVER_CACHE_MAX_FILES = 800

/** Garante que a capa esteja salva no disco (sem decodificar). Usado para pré-carregar o catálogo em segundo plano. */
internal fun cacheCoverOnDisk(context: Context, remote: RemoteStory) {
    val url = publicCoverUrl(remote.coverPath) ?: return
    val file = File(coversDir(context), coverKey(remote.coverPath, remote.updatedAt))
    if (file.isFile && file.length() > 0) return
    val bytes = fetchCoverBytesFromUrl(url) ?: return
    // Só guarda se for imagem válida.
    if (runCatching { decodeSampled(bytes, 64) }.getOrNull() == null) return
    runCatching { file.writeBytes(bytes) }
}

/**
 * Apaga do disco capas e .lyr que não pertencem mais ao catálogo atual: histórias removidas do online
 * e versões antigas (capa trocada / história atualizada).
 */
internal fun pruneCatalogCaches(context: Context, current: List<RemoteStory>) {
    if (current.isEmpty()) return // lista vazia pode ser falha do servidor; não apaga nada
    runCatching {
        val coverKeys = current.filter { !it.coverPath.isNullOrBlank() }.map { coverKey(it.coverPath, it.updatedAt) }.toSet()
        coversDir(context).listFiles()?.forEach { if (it.isFile && it.name !in coverKeys) runCatching { it.delete() } }
        val lyrKeys = current.filter { !it.filePath.isNullOrBlank() }.map { lyrKey(it) }.toSet()
        lyrDir(context).listFiles()?.forEach { if (it.isFile && it.name !in lyrKeys) runCatching { it.delete() } }
    }
}

// ---------------------------------------------------------------------------
// Arquivos .lyr das histórias baixadas
// ---------------------------------------------------------------------------

/** Baixa o .lyr da história; se essa versão (id + updatedAt) já foi baixada antes, reaproveita o arquivo salvo. */
internal suspend fun downloadStoryBytesCached(context: Context, remote: RemoteStory): ByteArray {
    val path = remote.filePath ?: throw IllegalArgumentException("História sem arquivo.")
    val file = File(lyrDir(context), lyrKey(remote))
    if (file.isFile && file.length() > 0) {
        runCatching { file.readBytes() }.getOrNull()?.let {
            file.setLastModified(System.currentTimeMillis())
            return it
        }
    }
    val bytes = downloadStoryBytes(path)
    runCatching {
        file.writeBytes(bytes)
        prune(lyrDir(context), 20)
    }
    return bytes
}

/** Tamanho aproximado do cache offline, em bytes (para mostrar nas configurações). */
internal fun offlineCacheSize(context: Context): Long =
    cacheRoot(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }

internal fun clearOfflineCache(context: Context) {
    coverMemory.evictAll()
    cacheRoot(context).deleteRecursively()
}
