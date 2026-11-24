package gui.pgcr.storyteller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ícones PNG guardados em app/src/main/assets/icons/:
 *  - icons/3d/ (favorite, default_cover, grade, lista)
 *  - icons/navigation_buttons/ (<aba>_on.png e <aba>_off.png)
 */
internal object AssetIcons {
    const val FAVORITE = "icons/3d/favorite.png"
    const val DEFAULT_COVER = "icons/3d/default_cover.png"
    const val GRID = "icons/3d/grade.png"
    const val LIST = "icons/3d/lista.png"

    fun nav(tab: String, selected: Boolean) = "icons/navigation_buttons/${tab}_${if (selected) "on" else "off"}.png"

    private val cache = HashMap<String, Bitmap>()

    /** Decodifica a 256 px (os PNGs têm 512) e guarda em memória: são poucos ícones e pequenos. */
    fun load(context: Context, path: String): Bitmap? {
        synchronized(cache) { cache[path]?.let { return it } }
        val bitmap = runCatching {
            context.applicationContext.assets.open(path).use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })
            }
        }.getOrNull() ?: return null
        synchronized(cache) { cache[path] = bitmap }
        return bitmap
    }
}

@Composable
internal fun AssetIcon(
    path: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    val bitmap = remember(path) { AssetIcons.load(context, path) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(modifier)
    }
}

/** Fundo colorido da capa com o ícone padrão no centro (história sem capa ou capa ainda carregando). */
@Composable
internal fun DefaultCoverPlaceholder(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp
) {
    Box(
        modifier = modifier.background(SurfaceAlt, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        AssetIcon(
            AssetIcons.DEFAULT_COVER,
            modifier = Modifier.widthIn(max = 140.dp).heightIn(max = 140.dp).fillMaxSize(0.7f)
        )
    }
}
