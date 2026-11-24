package gui.pgcr.storyteller

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun statusBadgeColor(status: String): Color = when (status) {
    STATUS_COMPLETE -> Color(0xFF1B5E3B)
    STATUS_CANCELLED -> Color(0xFF5E1B2A)
    else -> Color(0xFF1B3A5E)
}

private fun plural(n: Int, one: String, many: String) = if (n == 1) "$n $one" else "$n $many"

private fun Set<String>.toggled(value: String): Set<String> =
    if (any { it.equals(value, ignoreCase = true) }) filterNot { it.equals(value, ignoreCase = true) }.toSet() else this + value

private fun formatIsoDate(iso: String?): String? =
    iso?.take(10)?.split("-")?.takeIf { it.size == 3 }?.let { "${it[2]}/${it[1]}/${it[0]}" }

// ---------------------------------------------------------------------------
// Tela Explorar (início)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun OnlineExploreScreen(
    isLoggedIn: Boolean,
    busy: Boolean,
    onImportRemote: (RemoteStory) -> Unit,
    onNeedLogin: () -> Unit,
    onToast: (String) -> Unit,
    onMessage: (String) -> Unit,
    onAuthorClick: (ownerId: String?, authorName: String) -> Unit = { _, _ -> },
    onOpenDetails: (RemoteStory) -> Unit = {},
    /** Ids online das histórias que a pessoa favoritou (cópia na biblioteca). */
    favoriteRemoteIds: Set<String> = emptySet()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pulling by remember { mutableStateOf(false) }
    var pendingUnpublish by remember { mutableStateOf<RemoteStory?>(null) }
    var unpublishing by remember { mutableStateOf(false) }
    var filtersOpen by remember { mutableStateOf(false) }

    // Cache aparece na hora; a rede carrega em segundo plano (veja CatalogStore).
    LaunchedEffect(Unit) { CatalogStore.refresh(context) }

    val all = CatalogStore.stories
    val prefs = CatalogStore.viewPrefs
    val filters = CatalogStore.filters
    val options = remember(all) { CatalogFilterOptions.from(all) }
    val visible = remember(all, prefs, filters) { sortCatalog(all.filter { filters.matches(it) }, prefs) }
    val rows = remember(visible) { visible.chunked(2) }

    fun badgeFor(remote: RemoteStory): String? = when {
        remote.id != null && remote.id in CatalogStore.newIds -> "NOVA"
        remote.id != null && remote.id in CatalogStore.updatedIds -> "ATUALIZADA"
        else -> null
    }

    pendingUnpublish?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!unpublishing) pendingUnpublish = null },
            title = { Text("Remover história?") },
            text = {
                Text(
                    "“${target.title}” sai do catálogo online e o arquivo é apagado do servidor. " +
                        "Cópias já baixadas por outras pessoas continuam com elas. Sua cópia local não é afetada."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !unpublishing,
                    onClick = {
                        unpublishing = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { unpublishStory(target) }
                                pendingUnpublish = null
                                CatalogStore.removeLocally(context, target.id)
                                CatalogStore.refresh(context, force = true)
                                onToast("História removida")
                            } catch (e: Exception) {
                                pendingUnpublish = null
                                onMessage("Não foi possível remover: ${e.message ?: "erro"}")
                            } finally {
                                unpublishing = false
                            }
                        }
                    }
                ) { Text(if (unpublishing) "Aguarde…" else "Remover", color = DangerColor) }
            },
            dismissButton = {
                TextButton(enabled = !unpublishing, onClick = { pendingUnpublish = null }) {
                    Text("Cancelar", color = SecondaryText)
                }
            },
            containerColor = CardColor,
            titleContentColor = PrimaryText,
            textContentColor = SecondaryText
        )
    }

    PullToRefreshBox(
        isRefreshing = pulling,
        onRefresh = {
            scope.launch {
                pulling = true
                CatalogStore.refresh(context, force = true)
                delay(400) // dá tempo de a atualização começar
                snapshotFlow { CatalogStore.refreshing }.first { !it }
                pulling = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Explorar", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Explorar no Story Teller", color = PrimaryText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Histórias públicas hospedadas no Supabase. Toque numa história para ver os detalhes. Arraste a tela para baixo para atualizar.",
                    color = SecondaryText,
                    fontSize = 14.sp
                )
                if (catalogOffline.value) {
                    Text(
                        "Sem conexão: mostrando a última versão salva no aparelho.",
                        color = WarnColor,
                        fontSize = 12.sp
                    )
                }
            }
        }
        item {
            if (!isLoggedIn) {
                TextButton(onClick = onNeedLogin, contentPadding = PaddingValues(0.dp)) {
                    Text("Entrar para publicar", color = Accent)
                }
            }
            if (CatalogStore.refreshing && !pulling && all.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent)
                Text("Atualizando em segundo plano…", color = SecondaryText, fontSize = 11.sp)
            }
        }
        if (CatalogStore.hasChanges) {
            item(key = "changes") {
                val parts = buildList {
                    if (CatalogStore.newIds.isNotEmpty()) add(plural(CatalogStore.newIds.size, "nova", "novas"))
                    if (CatalogStore.updatedIds.isNotEmpty()) add(plural(CatalogStore.updatedIds.size, "atualizada", "atualizadas"))
                    if (CatalogStore.removedCount > 0) add(plural(CatalogStore.removedCount, "removida", "removidas"))
                }
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceAlt), shape = RoundedCornerShape(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Novidades: ${parts.joinToString(" · ")}",
                            color = PrimaryText,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { CatalogStore.dismissChanges() }) { Text("OK", color = Accent) }
                    }
                }
            }
        }
        if (all.isNotEmpty()) {
            item(key = "controls") {
                CatalogControls(
                    prefs = prefs,
                    onPrefs = { CatalogStore.setViewPrefs(context, it) },
                    filterCount = filters.count,
                    filtersOpen = filtersOpen,
                    onToggleFilters = { filtersOpen = !filtersOpen }
                )
            }
            if (filtersOpen) {
                item(key = "filters") {
                    CatalogFilterPanel(options = options, filters = filters, onChange = { CatalogStore.updateFilters(it) })
                }
            }
            item(key = "count") {
                Text(
                    if (filters.isEmpty) plural(all.size, "história", "histórias")
                    else "${visible.size} de ${plural(all.size, "história", "histórias")}",
                    color = SecondaryText,
                    fontSize = 12.sp
                )
            }
        }
        when {
            all.isEmpty() && CatalogStore.initialLoading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            }
            all.isEmpty() && CatalogStore.error != null -> item(key = "error") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(CatalogStore.error ?: "", color = DangerColor, fontSize = 14.sp)
                    TextButton(onClick = { CatalogStore.refresh(context, force = true) }) {
                        Text("Tentar novamente", color = Accent)
                    }
                }
            }
            all.isEmpty() -> item(key = "empty") {
                Text(
                    "Nenhuma história pública ainda. Faça login no Perfil e publique a sua pela tela de detalhes.",
                    color = SecondaryText,
                    fontSize = 14.sp
                )
            }
            visible.isEmpty() -> item(key = "no_match") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Nenhuma história com esses filtros.", color = SecondaryText, fontSize = 14.sp)
                    TextButton(onClick = { CatalogStore.updateFilters(CatalogFilters()) }) {
                        Text("Limpar filtros", color = Accent)
                    }
                }
            }
            prefs.grid -> items(rows, key = { row -> "row:${row.first().id ?: row.first().title}" }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { remote ->
                        RemoteGridCard(
                            remote = remote,
                            badge = badgeFor(remote),
                            isFavorite = remote.id != null && remote.id in favoriteRemoteIds,
                            onClick = { onOpenDetails(remote) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            else -> items(visible, key = { it.id ?: it.title }) { remote ->
                RemoteStoryCard(
                    remote = remote,
                    isLoggedIn = isLoggedIn,
                    busy = busy,
                    onImportRemote = onImportRemote,
                    onAuthorClick = onAuthorClick,
                    onRequestUnpublish = { pendingUnpublish = it },
                    unpublishEnabled = !unpublishing,
                    onOpenDetails = onOpenDetails,
                    badge = badgeFor(remote),
                    isFavorite = remote.id != null && remote.id in favoriteRemoteIds
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
    }
}

// ---------------------------------------------------------------------------
// Controles: ordenar, direção, grade/lista, filtros
// ---------------------------------------------------------------------------

@Composable
private fun CatalogControls(
    prefs: CatalogViewPrefs,
    onPrefs: (CatalogViewPrefs) -> Unit,
    filterCount: Int,
    filtersOpen: Boolean,
    onToggleFilters: () -> Unit
) {
    var sortMenu by remember { mutableStateOf(false) }
    // Uma linha só: Ordenar (com crescente/decrescente dentro) · Filtros · [alternar grade/lista à direita]
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            OutlinedButton(onClick = { sortMenu = true }, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Text(
                    "Ordenar: ${prefs.sort.label} ${if (prefs.ascending) "↑" else "↓"}",
                    color = Accent,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                CatalogSort.values().forEach { option ->
                    DropdownMenuItem(
                        text = { Text(if (option == prefs.sort) "✓ ${option.label}" else option.label) },
                        onClick = {
                            onPrefs(prefs.copy(sort = option))
                            sortMenu = false
                        }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(if (prefs.ascending) "✓ ↑ Crescente" else "↑ Crescente") },
                    onClick = {
                        onPrefs(prefs.copy(ascending = true))
                        sortMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (!prefs.ascending) "✓ ↓ Decrescente" else "↓ Decrescente") },
                    onClick = {
                        onPrefs(prefs.copy(ascending = false))
                        sortMenu = false
                    }
                )
            }
        }
        OutlinedButton(onClick = onToggleFilters, contentPadding = PaddingValues(horizontal = 14.dp)) {
            Text(
                buildString {
                    append(if (filtersOpen) "▴ Filtros" else "▾ Filtros")
                    if (filterCount > 0) append(" ($filterCount)")
                },
                color = Accent,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false
            )
        }
        Spacer(Modifier.weight(1f))
        // Botão único que alterna: mostra o ícone do modo para o qual vai mudar.
        IconButton(onClick = { onPrefs(prefs.copy(grid = !prefs.grid)) }) {
            AssetIcon(
                if (prefs.grid) AssetIcons.LIST else AssetIcons.GRID,
                Modifier.size(34.dp),
                contentDescription = if (prefs.grid) "Mudar para lista" else "Mudar para grade"
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(
    title: String,
    options: List<String>,
    selected: Set<String>,
    prefix: String = "",
    collapsedLimit: Int = Int.MAX_VALUE,
    onChange: (Set<String>) -> Unit
) {
    if (options.isEmpty()) return
    var showAll by remember(title) { mutableStateOf(false) }
    val shown = if (showAll || options.size <= collapsedLimit) options
    else options.take(collapsedLimit) + options.drop(collapsedLimit).filter { o -> selected.any { it.equals(o, true) } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            shown.forEach { option ->
                FilterChip(
                    selected = selected.any { it.equals(option, ignoreCase = true) },
                    onClick = { onChange(selected.toggled(option)) },
                    label = { Text("$prefix$option", fontSize = 11.sp) }
                )
            }
        }
        if (options.size > collapsedLimit) {
            Text(
                if (showAll) "Mostrar menos" else "Ver todas (${options.size})",
                color = Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { showAll = !showAll }
            )
        }
    }
}

@Composable
private fun CatalogFilterPanel(
    options: CatalogFilterOptions,
    filters: CatalogFilters,
    onChange: (CatalogFilters) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Os filtros se somam: cada opção marcada reduz mais a lista.",
                color = SecondaryText,
                fontSize = 12.sp
            )
            FilterGroup("Tipo", options.types, filters.types) { onChange(filters.copy(types = it)) }
            FilterGroup("Estado", STORY_STATUSES, filters.statuses) { onChange(filters.copy(statuses = it)) }
            FilterGroup("Gêneros", options.genres, filters.genres) { onChange(filters.copy(genres = it)) }
            FilterGroup("Tags", options.tags, filters.tags, prefix = "#", collapsedLimit = 20) {
                onChange(filters.copy(tags = it))
            }
            if (!filters.isEmpty) {
                TextButton(onClick = { onChange(CatalogFilters()) }, contentPadding = PaddingValues(0.dp)) {
                    Text("Limpar filtros", color = Accent)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Cartão da grade
// ---------------------------------------------------------------------------

@Composable
private fun RemoteGridCard(
    remote: RemoteStory,
    badge: String?,
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(0.72f)) {
                RemoteCoverImage(
                    remote.coverPath,
                    Modifier.fillMaxSize(),
                    version = remote.updatedAt,
                    maxDimension = 512
                )
                Image(
                    painter = painterResource(id = classifDrawableRes(remote.rating)),
                    contentDescription = "Classificação ${remote.rating}",
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).height(24.dp).wrapContentWidth()
                )
                Surface(
                    color = statusBadgeColor(remote.status),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                ) {
                    Text(
                        remote.status,
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        maxLines = 1
                    )
                }
                if (isFavorite) {
                    AssetIcon(
                        AssetIcons.FAVORITE,
                        Modifier.align(Alignment.BottomStart).padding(5.dp).size(30.dp),
                        contentDescription = "Favorita"
                    )
                }
                if (badge != null) {
                    Surface(
                        color = Accent,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                    ) {
                        Text(
                            badge,
                            color = OnAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            maxLines = 1
                        )
                    }
                }
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    remote.title,
                    color = PrimaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(remote.author, color = SecondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${remote.chapterCount} cap.", color = SecondaryText, fontSize = 11.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Detalhes de uma história online (antes de baixar)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RemoteStoryDetailsScreen(
    remote: RemoteStory,
    isLoggedIn: Boolean,
    busy: Boolean,
    /** Cópia desta história já baixada na biblioteca, se houver. */
    localCopy: Story?,
    /** Versão editável da própria pessoa (quando a história é dela). Nunca é substituída pelo download. */
    editableCopy: Story?,
    onOpenEditable: () -> Unit,
    isFavorite: Boolean,
    onDownload: () -> Unit,
    onOpenLocal: () -> Unit,
    onAuthorClick: () -> Unit,
    onClose: () -> Unit,
    onToast: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var synopsisExpanded by remember(remote.id) { mutableStateOf(false) }
    var confirmUnpublish by remember { mutableStateOf(false) }
    var unpublishing by remember { mutableStateOf(false) }
    val statusColor = when (remote.status) {
        STATUS_COMPLETE -> SuccessColor
        STATUS_CANCELLED -> DangerColor
        else -> InfoColor
    }
    val publishedByMe = isLoggedIn && remote.ownerId != null && remote.ownerId == currentUserId()
    val hasNewVersion = localCopy != null && localCopy.remoteUpdatedAt != remote.updatedAt
    val canDownload = !busy && !remote.filePath.isNullOrBlank()

    if (confirmUnpublish) {
        AlertDialog(
            onDismissRequest = { if (!unpublishing) confirmUnpublish = false },
            title = { Text("Remover história?") },
            text = {
                Text(
                    "“${remote.title}” sai do catálogo online e o arquivo é apagado do servidor. " +
                        "Cópias já baixadas por outras pessoas continuam com elas. Sua cópia local não é afetada."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !unpublishing,
                    onClick = {
                        unpublishing = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { unpublishStory(remote) }
                                confirmUnpublish = false
                                CatalogStore.removeLocally(context, remote.id)
                                onToast("História removida")
                                onClose()
                            } catch (e: Exception) {
                                confirmUnpublish = false
                                onMessage("Não foi possível remover: ${e.message ?: "erro"}")
                            } finally {
                                unpublishing = false
                            }
                        }
                    }
                ) { Text(if (unpublishing) "Aguarde…" else "Remover", color = DangerColor) }
            },
            dismissButton = {
                TextButton(enabled = !unpublishing, onClick = { confirmUnpublish = false }) {
                    Text("Cancelar", color = SecondaryText)
                }
            },
            containerColor = CardColor,
            titleContentColor = PrimaryText,
            textContentColor = SecondaryText
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                RemoteCoverImage(
                    remote.coverPath,
                    Modifier.fillMaxWidth().heightIn(min = 170.dp, max = 320.dp),
                    contentScale = ContentScale.Fit,
                    version = remote.updatedAt
                )
                Image(
                    painter = painterResource(id = classifDrawableRes(remote.rating)),
                    contentDescription = "Classificação ${remote.rating}",
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).height(48.dp).wrapContentWidth()
                )
            }
        }
        item {
            if (remote.genres.isNotEmpty()) {
                Text(
                    remote.genres.joinToString(" · ").uppercase(),
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    remote.title,
                    color = PrimaryText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isFavorite) AssetIcon(AssetIcons.FAVORITE, Modifier.size(34.dp), contentDescription = "Favorita")
            }
            Text(
                "por ${remote.author}",
                color = Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onAuthorClick)
            )
            if (publishedByMe) Text("Publicada por você", color = Accent, fontSize = 12.sp)
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                InfoChip(remote.rating)
                InfoChip("${remote.chapterCount} capítulos")
                Surface(color = statusColor.copy(alpha = 0.2f), shape = RoundedCornerShape(50)) {
                    Text(
                        remote.status,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            val published = formatIsoDate(remote.createdAt)
            val updated = formatIsoDate(remote.updatedAt)
            if (published != null || updated != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    listOfNotNull(
                        published?.let { "Publicada em $it" },
                        updated?.takeIf { it != published }?.let { "atualizada em $it" }
                    ).joinToString(" · "),
                    color = SecondaryText,
                    fontSize = 12.sp
                )
            }
        }
        if (remote.classifications.isNotEmpty() || remote.tags.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (remote.classifications.isNotEmpty()) {
                        Text("Tipo", color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { remote.classifications.forEach { InfoChip(it) } }
                    }
                    if (remote.tags.isNotEmpty()) {
                        if (remote.classifications.isNotEmpty()) Spacer(Modifier.height(12.dp))
                        Text("Tags", color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            remote.tags.forEach { InfoChip(text = "#$it", modifier = Modifier.widthIn(max = 260.dp)) }
                        }
                    }
                }
            }
        }
        item {
            Text("Sinopse", color = PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (remote.description.isBlank()) {
                Text("Sem sinopse.", color = SecondaryText, fontSize = 15.sp)
            } else {
                val longSynopsis = remote.description.length > 280 || remote.description.count { it == '\n' } >= 3
                Text(
                    remote.description,
                    color = SecondaryText,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                    maxLines = if (synopsisExpanded || !longSynopsis) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis
                )
                if (longSynopsis) {
                    TextButton(onClick = { synopsisExpanded = !synopsisExpanded }, contentPadding = PaddingValues(0.dp)) {
                        Text(
                            if (synopsisExpanded) "Mostrar menos" else "Ler sinopse completa",
                            color = Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        item { ContentRatingCard(rating = remote.rating, warnings = defaultContentWarningFor(remote.rating)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasNewVersion) {
                    Text("Há uma versão mais nova online do que a da sua biblioteca.", color = WarnColor, fontSize = 12.sp)
                }
                if (localCopy == null || hasNewVersion) {
                    Button(
                        onClick = onDownload,
                        enabled = canDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            when {
                                busy -> "Baixando…"
                                hasNewVersion -> "Atualizar para a nova versão"
                                else -> "Baixar para a biblioteca"
                            }
                        )
                    }
                }
                if (localCopy != null) {
                    if (hasNewVersion) {
                        OutlinedButton(onClick = onOpenLocal, modifier = Modifier.fillMaxWidth()) {
                            Text("Abrir versão da biblioteca", color = Accent)
                        }
                    } else {
                        Button(
                            onClick = onOpenLocal,
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Abrir na biblioteca") }
                    }
                }
                if (editableCopy != null) {
                    OutlinedButton(onClick = onOpenEditable, modifier = Modifier.fillMaxWidth()) {
                        Text("Abrir minha versão editável", color = Accent)
                    }
                    Text(
                        "Baixar cria uma cópia só para leitura; sua versão editável não é substituída.",
                        color = SecondaryText,
                        fontSize = 11.sp
                    )
                }
                if (publishedByMe) {
                    OutlinedButton(
                        onClick = { confirmUnpublish = true },
                        enabled = !busy && !unpublishing,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Remover do Lyron Story Teller", color = DangerColor) }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
