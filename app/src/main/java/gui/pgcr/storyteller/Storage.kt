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

internal const val PREFS_NAME = "lyron_story_data"
internal const val USER_STORIES_KEY = "user_stories"
internal const val STORY_STATS_KEY = "story_reading_stats"
internal const val USER_PROFILE_KEY = "user_profile"
internal const val FAVORITE_IDS_KEY = "favorite_story_ids"

internal fun loadStoryStats(context: Context): Map<String, StoryStats> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(STORY_STATS_KEY, null) ?: return emptyMap()
    return runCatching {
        val root = JSONObject(raw)
        buildMap {
            val keys = root.keys()
            while (keys.hasNext()) {
                val storyId = keys.next()
                val json = root.optJSONObject(storyId) ?: continue
                fun readCounts(name: String): Map<String, Int> {
                    val counts = json.optJSONObject(name) ?: JSONObject()
                    return buildMap {
                        val countKeys = counts.keys()
                        while (countKeys.hasNext()) {
                            val key = countKeys.next()
                            put(key, counts.optInt(key).coerceAtLeast(0))
                        }
                    }
                }
                put(storyId, StoryStats(
                    chapterVisits = readCounts("chapterVisits"),
                    pathTraversals = readCounts("pathTraversals"),
                    endingVisits = readCounts("endingVisits"),
                    completedReadings = json.optInt("completedReadings").coerceAtLeast(0)
                ))
            }
        }
    }.getOrDefault(emptyMap())
}

internal fun saveStoryStats(context: Context, stats: Map<String, StoryStats>) {
    val root = JSONObject()
    stats.forEach { (storyId, value) ->
        fun toJson(counts: Map<String, Int>) = JSONObject().apply {
            counts.forEach { (key, count) -> put(key, count.coerceAtLeast(0)) }
        }
        root.put(storyId, JSONObject()
            .put("chapterVisits", toJson(value.chapterVisits))
            .put("pathTraversals", toJson(value.pathTraversals))
            .put("endingVisits", toJson(value.endingVisits))
            .put("completedReadings", value.completedReadings.coerceAtLeast(0)))
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(STORY_STATS_KEY, root.toString()).apply()
}

internal fun loadUserStories(context: Context): List<Story> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(USER_STORIES_KEY, null) ?: return emptyList()
    return try {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val chaptersJson = item.optJSONArray("chapters") ?: JSONArray()
                val chapters = buildList {
                    for (j in 0 until chaptersJson.length()) {
                        val chapter = chaptersJson.getJSONObject(j)
                        val spansJson = chapter.optJSONArray("spans") ?: JSONArray()
                        val spans = buildList {
                            for (k in 0 until spansJson.length()) {
                                val span = spansJson.getJSONObject(k)
                                add(RichSpan(span.optInt("start"), span.optInt("end"), span.optBoolean("bold"), span.optBoolean("italic"), span.optBoolean("underline"), span.optBoolean("strikethrough"), span.optInt("heading")))
                            }
                        }
                        val paragraphsJson = chapter.optJSONArray("paragraphs") ?: JSONArray()
                        val paragraphs = buildList {
                            for (k in 0 until paragraphsJson.length()) {
                                val paragraph = paragraphsJson.getJSONObject(k)
                                add(RichParagraph(paragraph.optInt("start"), paragraph.optInt("end"), paragraph.optString("alignment", "left")))
                            }
                        }
                        val choicesJson = chapter.optJSONArray("choices") ?: JSONArray()
                        val choices = buildList {
                            for (c in 0 until choicesJson.length()) {
                                val choice = choicesJson.getJSONObject(c)
                                add(StoryChoice(
                                    text = choice.optString("text"),
                                    nextChapterId = choice.optString("nextChapterId").takeIf { it.isNotBlank() && it != "null" },
                                    endsStory = choice.optBoolean("endsStory"),
                                    id = choice.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: run {
                                        UUID.nameUUIDFromBytes("$j|${chapter.optString("title")}|${chapter.optString("body")}|$c|${choice.optString("text")}|${choice.optString("nextChapterId")}".toByteArray()).toString()
                                    }
                                ))
                            }
                        }
                        val imagesJson = chapter.optJSONArray("images") ?: JSONArray()
                        val images = buildList {
                            for (imageIndex in 0 until imagesJson.length()) {
                                val image = imagesJson.getJSONObject(imageIndex)
                                val uri = image.optString("uri").takeIf { it.isNotBlank() && it != "null" }
                                if (uri != null) add(ChapterImage(
                                    uri = uri,
                                    position = image.optInt("position").coerceAtLeast(0),
                                    caption = image.optString("caption"),
                                    id = image.optString("id").ifBlank { UUID.randomUUID().toString() }
                                ))
                            }
                        }
                        add(Chapter(chapter.optString("title"), chapter.optString("body"), spans, paragraphs,
                            chapter.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: run {
                                UUID.nameUUIDFromBytes("$j|${chapter.optString("title").trim()}|${chapter.optString("body")}".toByteArray()).toString()
                            }, choices,
                            chapter.optBoolean("isEnding", false),
                            chapter.optString("endingMessage", "Você chegou a um desfecho desta história."), images,
                            chapter.optBoolean("isDraft", false)))
                    }
                }
                val tagsJson = item.optJSONArray("tags") ?: JSONArray()
                val tags = buildList {
                    for (t in 0 until tagsJson.length()) {
                        val tag = tagsJson.optString(t).trim()
                        if (tag.isNotBlank()) add(tag)
                    }
                }.distinct()
                val classificationsJson = item.optJSONArray("classifications") ?: JSONArray()
                val classifications = buildList {
                    for (c in 0 until classificationsJson.length()) {
                        val value = classificationsJson.optString(c).trim()
                        if (value in STORY_CLASSIFICATIONS) add(value)
                    }
                }.distinct()
                val genresJson = item.optJSONArray("genres")
                val genres = if (genresJson != null) {
                    buildList {
                        for (g in 0 until genresJson.length()) {
                            val value = genresJson.optString(g).trim()
                            if (value.isNotBlank()) add(value)
                        }
                    }.distinct().take(MAX_STORY_GENRES)
                } else {
                    item.optString("genre").trim().takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty()
                }
                add(Story(
                    title = item.optString("title"),
                    author = item.optString("author"),
                    genres = genres,
                    description = item.optString("description"),
                    rating = item.optString("rating", "Livre"),
                    chapters = chapters,
                    coverImageUri = item.optString("coverImageUri").takeIf { it.isNotBlank() && it != "null" },
                    tags = tags,
                    classifications = classifications,
                    contentWarnings = item.optString("contentWarnings").ifBlank {
                        defaultContentWarningFor(item.optString("rating", "Livre"))
                    },
                    status = item.optString("status", STATUS_IN_PROGRESS).takeIf { it in STORY_STATUSES } ?: STATUS_IN_PROGRESS,
                    editable = item.optBoolean("editable", true),
                    lastUpdated = item.optLong("lastUpdated", System.currentTimeMillis()),
                    remoteId = item.optString("remoteId").takeIf { it.isNotBlank() && it != "null" },
                    remoteUpdatedAt = item.optString("remoteUpdatedAt").takeIf { it.isNotBlank() && it != "null" },
                    remoteOwnerId = item.optString("remoteOwnerId").takeIf { it.isNotBlank() && it != "null" },
                    id = item.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: run {
                        UUID.nameUUIDFromBytes("$i|${item.toString()}".toByteArray()).toString()
                    }
                ))
            }
        }
    } catch (_: Exception) { emptyList() }
}

internal fun loadUserProfile(context: Context): UserProfile {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(USER_PROFILE_KEY, null) ?: return UserProfile()
    return runCatching {
        val json = JSONObject(raw)
        UserProfile(
            fullName = json.optString("fullName"),
            nickname = json.optString("nickname"),
            useFullNameAsAuthor = json.optBoolean("useFullNameAsAuthor", false),
            photoUri = json.optString("photoUri").takeIf { it.isNotBlank() && it != "null" },
            username = json.optString("username"),
            avatarPath = json.optString("avatarPath").takeIf { it.isNotBlank() && it != "null" },
            bio = json.optString("bio"),
            publicBio = json.optBoolean("publicBio", true),
            publicWritten = json.optBoolean("publicWritten", true),
            publicFavorites = json.optBoolean("publicFavorites", false)
        )
    }.getOrDefault(UserProfile())
}

internal fun saveUserProfile(context: Context, profile: UserProfile) {
    val json = JSONObject()
        .put("fullName", profile.fullName)
        .put("nickname", profile.nickname)
        .put("useFullNameAsAuthor", profile.useFullNameAsAuthor)
        .put("photoUri", profile.photoUri ?: JSONObject.NULL)
        .put("username", profile.username)
        .put("avatarPath", profile.avatarPath ?: JSONObject.NULL)
        .put("bio", profile.bio)
        .put("publicBio", profile.publicBio)
        .put("publicWritten", profile.publicWritten)
        .put("publicFavorites", profile.publicFavorites)
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(USER_PROFILE_KEY, json.toString()).apply()
}

internal const val APP_SETTINGS_KEY = "app_settings"


internal const val READING_PROGRESS_KEY = "reading_progress_map"

internal fun loadReadingProgressMap(context: Context): Map<String, ReadingProgress> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(READING_PROGRESS_KEY, null) ?: return emptyMap()
    return runCatching {
        val json = JSONObject(raw)
        buildMap {
            json.keys().forEach { key ->
                val obj = json.optJSONObject(key) ?: return@forEach
                put(
                    key,
                    ReadingProgress(
                        chapterIndex = obj.optInt("chapterIndex", 0),
                        pageIndex = obj.optInt("pageIndex", 0),
                        updatedAt = obj.optLong("updatedAt", 0L)
                    )
                )
            }
        }
    }.getOrDefault(emptyMap())
}

internal fun saveReadingProgress(context: Context, storyId: String, chapterIndex: Int, pageIndex: Int) {
    if (storyId.isBlank()) return
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val map = loadReadingProgressMap(context).toMutableMap()
    map[storyId] = ReadingProgress(chapterIndex, pageIndex, System.currentTimeMillis())
    val json = JSONObject()
    map.forEach { (id, p) ->
        json.put(
            id,
            JSONObject()
                .put("chapterIndex", p.chapterIndex)
                .put("pageIndex", p.pageIndex)
                .put("updatedAt", p.updatedAt)
        )
    }
    prefs.edit().putString(READING_PROGRESS_KEY, json.toString()).apply()
}

internal fun clearReadingProgress(context: Context, storyId: String) {
    if (storyId.isBlank()) return
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val map = loadReadingProgressMap(context).toMutableMap()
    if (map.remove(storyId) != null) {
        val json = JSONObject()
        map.forEach { (id, p) ->
            json.put(
                id,
                JSONObject()
                    .put("chapterIndex", p.chapterIndex)
                    .put("pageIndex", p.pageIndex)
                    .put("updatedAt", p.updatedAt)
            )
        }
        prefs.edit().putString(READING_PROGRESS_KEY, json.toString()).apply()
    }
}

internal fun loadAppSettings(context: Context): AppSettings {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(APP_SETTINGS_KEY, null) ?: return AppSettings()
    return runCatching {
        val json = JSONObject(raw)
        AppSettings(
            themeId = json.optString("themeId", "dark"),
            defaultReaderFontSize = json.optDouble("defaultReaderFontSize", 18.0).toFloat().coerceIn(14f, 28f),
            showSampleStories = json.optBoolean("showSampleStories", false),
            confirmBeforeExport = json.optBoolean("confirmBeforeExport", true),
            readerThemeId = json.optString("readerThemeId", "amethyst_night"),
            readerMode = json.optString("readerMode", "continuous"),
            readerFontFamily = json.optString("readerFontFamily", "system"),
            appFontFamily = json.optString("appFontFamily", "system"),
            loadCustomFonts = json.optBoolean("loadCustomFonts", true),
            readerLineHeight = json.optDouble("readerLineHeight", 1.55).toFloat().coerceIn(1.2f, 2.2f),
            readerMarginDp = json.optDouble("readerMarginDp", 20.0).toFloat().coerceIn(8f, 48f)
        )
    }.getOrDefault(AppSettings())
}

internal fun saveAppSettings(context: Context, settings: AppSettings) {
    val json = JSONObject()
        .put("themeId", settings.themeId)
        .put("defaultReaderFontSize", settings.defaultReaderFontSize.toDouble())
        .put("showSampleStories", settings.showSampleStories)
        .put("confirmBeforeExport", settings.confirmBeforeExport)
        .put("readerThemeId", settings.readerThemeId)
        .put("readerMode", settings.readerMode)
        .put("readerFontFamily", settings.readerFontFamily)
        .put("appFontFamily", settings.appFontFamily)
        .put("loadCustomFonts", settings.loadCustomFonts)
        .put("readerLineHeight", settings.readerLineHeight.toDouble())
        .put("readerMarginDp", settings.readerMarginDp.toDouble())
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(APP_SETTINGS_KEY, json.toString()).apply()
}

internal fun loadFavoriteIds(context: Context): Set<String> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(FAVORITE_IDS_KEY, null) ?: return emptySet()
    return runCatching {
        val array = JSONArray(raw)
        buildSet {
            for (i in 0 until array.length()) {
                val id = array.optString(i)
                if (id.isNotBlank()) add(id)
            }
        }
    }.getOrDefault(emptySet())
}

internal fun saveFavoriteIds(context: Context, ids: Set<String>) {
    val array = JSONArray()
    ids.forEach { array.put(it) }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(FAVORITE_IDS_KEY, array.toString()).apply()
}

internal fun saveUserStories(context: Context, stories: List<Story>) {
    val array = JSONArray()
    stories.forEach { story ->
        val chapters = JSONArray()
        story.chapters.forEach { chapter ->
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
                    .put("nextChapterId", choice.nextChapterId ?: JSONObject.NULL)
                    .put("endsStory", choice.endsStory).put("id", choice.id))
            }
            val images = JSONArray()
            chapter.images.forEach { image ->
                images.put(JSONObject().put("uri", image.uri).put("position", image.position).put("caption", image.caption).put("id", image.id))
            }
            chapters.put(JSONObject().put("title", chapter.title).put("body", chapter.body)
                .put("spans", spans).put("paragraphs", paragraphs).put("id", chapter.id)
                .put("choices", choices).put("isEnding", chapter.isEnding)
                .put("endingMessage", chapter.endingMessage).put("images", images).put("isDraft", chapter.isDraft))
        }
        val tagsJson = JSONArray()
        story.tags.forEach { tagsJson.put(it) }
        val classificationsJson = JSONArray()
        story.classifications.forEach { classificationsJson.put(it) }
        val genresJson = JSONArray()
        story.genres.forEach { genresJson.put(it) }
        array.put(JSONObject()
            .put("title", story.title)
            .put("author", story.author)
            .put("genre", story.genre)
            .put("genres", genresJson)
            .put("description", story.description)
            .put("rating", story.rating)
            .put("chapters", chapters)
            .put("coverImageUri", story.coverImageUri ?: JSONObject.NULL)
            .put("tags", tagsJson)
            .put("classifications", classificationsJson)
            .put("contentWarnings", story.contentWarnings)
            .put("status", story.status)
            .put("editable", story.editable)
            .put("lastUpdated", story.lastUpdated)
            .put("remoteId", story.remoteId ?: JSONObject.NULL)
            .put("remoteUpdatedAt", story.remoteUpdatedAt ?: JSONObject.NULL)
            .put("remoteOwnerId", story.remoteOwnerId ?: JSONObject.NULL)
            .put("id", story.id))
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(USER_STORIES_KEY, array.toString()).apply()
}
