package gui.pgcr.storyteller

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Catálogo online em memória, compartilhado por todas as telas (Explorar, Pesquisar…).
 *
 * Fluxo:
 *  1. A primeira tela que pedir carrega o cache do disco e mostra na hora;
 *  2. em segundo plano busca a lista atual no servidor;
 *  3. ao terminar, compara com o que estava na tela: histórias novas e atualizadas ganham destaque,
 *     as que sumiram do online saem da lista, e o cache (catálogo + capas) é atualizado/limpo.
 *
 * O trabalho roda no escopo próprio do repositório, então trocar de aba no meio do carregamento não o cancela.
 */
internal object CatalogStore {
    var stories by mutableStateOf<List<RemoteStory>>(emptyList())
        private set

    /** true até existir algo para mostrar (cache) ou a primeira tentativa de rede terminar. */
    var initialLoading by mutableStateOf(true)
        private set

    /** true enquanto a atualização em segundo plano está buscando no servidor. */
    var refreshing by mutableStateOf(false)
        private set

    /** Erro de rede só é exposto quando não há nada (nem cache) para mostrar. */
    var error by mutableStateOf<String?>(null)
        private set

    /** Ids de histórias que apareceram no último carregamento (destaque “NOVA”). */
    var newIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Ids de histórias que mudaram no servidor (destaque “ATUALIZADA”). */
    var updatedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Quantas histórias saíram do online desde que a pessoa viu a lista. */
    var removedCount by mutableStateOf(0)
        private set

    val hasChanges: Boolean get() = newIds.isNotEmpty() || updatedIds.isNotEmpty() || removedCount > 0

    // Preferências de exibição e filtros sobrevivem à troca de abas.
    var viewPrefs by mutableStateOf(CatalogViewPrefs())
        private set
    var filters by mutableStateOf(CatalogFilters())
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private var cacheLoaded = false
    private var prefsLoaded = false
    private var lastRefreshAt = 0L

    /** Chame ao abrir uma tela do catálogo. [force] ignora o intervalo mínimo entre atualizações (botão “Atualizar”). */
    fun refresh(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        if (!prefsLoaded) {
            prefsLoaded = true
            viewPrefs = loadCatalogViewPrefs(app)
        }
        val recentlyRefreshed = System.currentTimeMillis() - lastRefreshAt < MIN_REFRESH_INTERVAL_MS
        if (!force && recentlyRefreshed && stories.isNotEmpty()) return
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                // 1) cache primeiro: aparece na hora
                if (!cacheLoaded) {
                    cacheLoaded = true
                    val cached = loadCatalogCache(app)
                    if (!cached.isNullOrEmpty() && stories.isEmpty()) {
                        stories = cached
                        initialLoading = false
                    }
                }
                // 2) rede em segundo plano
                refreshing = true
                error = null
                val fresh = try {
                    fetchPublicStories(limit = Int.MAX_VALUE)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    catalogOffline.value = true
                    if (stories.isEmpty()) error = e.message ?: "Não foi possível carregar o catálogo online."
                    return@launch
                }
                // 3) novas, atualizadas e removidas
                applyFresh(app, fresh)
            } finally {
                refreshing = false
                initialLoading = false
                running.set(false)
            }
        }
    }

    private fun applyFresh(app: Context, fresh: List<RemoteStory>) {
        val old = stories
        val oldById = old.mapNotNull { s -> s.id?.let { it to s } }.toMap()
        val freshIds = fresh.mapNotNull { it.id }.toSet()
        if (old.isNotEmpty()) {
            val added = fresh.filter { it.id != null && it.id !in oldById }.mapNotNull { it.id }
            val changed = fresh.filter { s -> s.id != null && oldById[s.id]?.let { it != s } == true }.mapNotNull { it.id }
            val removed = oldById.keys.count { it !in freshIds }
            newIds = (newIds + added).filter { it in freshIds }.toSet()
            updatedIds = (updatedIds + changed).filter { it in freshIds && it !in newIds }.toSet()
            removedCount += removed
        }
        stories = fresh
        catalogOffline.value = false
        lastRefreshAt = System.currentTimeMillis()
        saveCatalogCache(app, fresh)
        // Limpa capas/arquivos do que saiu do online e de versões antigas; depois pré-carrega as capas que faltam.
        pruneCatalogCaches(app, fresh)
        scope.launch { fresh.forEach { runCatching { cacheCoverOnDisk(app, it) } } }
    }

    /** Remove uma história da lista na hora (depois de despublicar) e já grava no cache. */
    fun removeLocally(context: Context, id: String?) {
        if (id == null) return
        val next = stories.filter { it.id != id }
        stories = next
        newIds = newIds - id
        updatedIds = updatedIds - id
        val app = context.applicationContext
        scope.launch { saveCatalogCache(app, next) }
    }

    /** Força a próxima abertura de tela a buscar de novo (ex.: depois de publicar). */
    fun invalidate() {
        lastRefreshAt = 0L
    }

    fun dismissChanges() {
        newIds = emptySet()
        updatedIds = emptySet()
        removedCount = 0
    }

    fun setViewPrefs(context: Context, prefs: CatalogViewPrefs) {
        viewPrefs = prefs
        saveCatalogViewPrefs(context.applicationContext, prefs)
    }

    fun updateFilters(value: CatalogFilters) {
        filters = value
    }

    private const val MIN_REFRESH_INTERVAL_MS = 60_000L
}

// ---------------------------------------------------------------------------
// Ordenação e modo de visualização
// ---------------------------------------------------------------------------

internal enum class CatalogSort(val label: String) {
    DATE("Data"),
    NAME("Nome"),
    CHAPTERS("Capítulos")
}

internal data class CatalogViewPrefs(
    val sort: CatalogSort = CatalogSort.DATE,
    /** true = crescente (A→Z, menos capítulos, mais antiga primeiro). O padrão é decrescente: mais recentes primeiro. */
    val ascending: Boolean = false,
    val grid: Boolean = false
)

private const val VIEW_PREFS_FILE = "catalog_view_prefs"

internal fun loadCatalogViewPrefs(context: Context): CatalogViewPrefs {
    val raw = context.getSharedPreferences(VIEW_PREFS_FILE, Context.MODE_PRIVATE)
    val sort = runCatching { CatalogSort.valueOf(raw.getString("sort", "") ?: "") }.getOrDefault(CatalogSort.DATE)
    return CatalogViewPrefs(
        sort = sort,
        ascending = raw.getBoolean("ascending", false),
        grid = raw.getBoolean("grid", false)
    )
}

internal fun saveCatalogViewPrefs(context: Context, prefs: CatalogViewPrefs) {
    context.getSharedPreferences(VIEW_PREFS_FILE, Context.MODE_PRIVATE).edit()
        .putString("sort", prefs.sort.name)
        .putBoolean("ascending", prefs.ascending)
        .putBoolean("grid", prefs.grid)
        .apply()
}

/** Data usada na ordenação: atualização, ou criação quando não houver. Texto ISO-8601 ordena corretamente como texto. */
private fun RemoteStory.sortDate(): String = (updatedAt ?: createdAt).orEmpty()

internal fun sortCatalog(list: List<RemoteStory>, prefs: CatalogViewPrefs): List<RemoteStory> {
    // Collator PRIMARY ignora acentos e maiúsculas: “Árvore” fica junto de “arvore”.
    val collator = Collator.getInstance(Locale("pt", "BR")).apply { strength = Collator.PRIMARY }
    val byName = Comparator<RemoteStory> { a, b -> collator.compare(a.title, b.title) }
    val base: Comparator<RemoteStory> = when (prefs.sort) {
        CatalogSort.DATE -> Comparator<RemoteStory> { a, b -> a.sortDate().compareTo(b.sortDate()) }.then(byName)
        CatalogSort.NAME -> byName
        CatalogSort.CHAPTERS -> Comparator<RemoteStory> { a, b -> a.chapterCount.compareTo(b.chapterCount) }.then(byName)
    }
    return list.sortedWith(if (prefs.ascending) base else base.reversed())
}

// ---------------------------------------------------------------------------
// Filtros cumulativos
// ---------------------------------------------------------------------------

/**
 * Filtros que se somam: cada opção marcada reduz mais a lista.
 *  - Tipo, gêneros e tags: a história precisa ter TODAS as opções marcadas.
 *  - Estado: uma história só tem um estado, então marcar mais de um estado significa “qualquer um deles”.
 * A comparação ignora maiúsculas/minúsculas.
 */
internal data class CatalogFilters(
    val types: Set<String> = emptySet(),
    val statuses: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val tags: Set<String> = emptySet()
) {
    val count: Int get() = types.size + statuses.size + genres.size + tags.size
    val isEmpty: Boolean get() = count == 0

    fun matches(story: RemoteStory): Boolean =
        types.all { t -> story.classifications.any { it.equals(t, ignoreCase = true) } } &&
            (statuses.isEmpty() || statuses.any { it.equals(story.status, ignoreCase = true) }) &&
            genres.all { g -> story.genres.any { it.equals(g, ignoreCase = true) } } &&
            tags.all { t -> story.tags.any { it.equals(t, ignoreCase = true) } }
}

/** Opções de filtro que existem de fato no catálogo carregado. */
internal data class CatalogFilterOptions(
    val types: List<String>,
    val genres: List<String>,
    val tags: List<String>
) {
    companion object {
        private fun List<String>.distinctSorted(): List<String> =
            map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })

        fun from(stories: List<RemoteStory>): CatalogFilterOptions {
            val types = stories.flatMap { it.classifications }.distinctSorted()
            // Tipos conhecidos primeiro, na ordem do app; depois qualquer outro.
            val orderedTypes = STORY_CLASSIFICATIONS.filter { k -> types.any { it.equals(k, true) } } +
                types.filter { t -> STORY_CLASSIFICATIONS.none { it.equals(t, true) } }
            return CatalogFilterOptions(
                types = orderedTypes,
                genres = stories.flatMap { it.genres }.distinctSorted(),
                tags = stories.flatMap { it.tags }.distinctSorted()
            )
        }
    }
}
