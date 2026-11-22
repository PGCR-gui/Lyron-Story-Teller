package gui.pgcr.storyteller

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Selo de classificação indicativa com o aviso de conteúdo na mesma peça:
 * áreas pretas com os textos fixos (título e "Não recomendado para menores de…"),
 * área branca com o ícone da classificação e o aviso de conteúdo da história.
 * As cores são fixas (não seguem o tema) para lembrar o selo oficial.
 */
@Composable
internal fun ContentRatingCard(rating: String, warnings: String, modifier: Modifier = Modifier) {
    val isFree = rating.equals("Livre", ignoreCase = true)
    val footer = if (isFree) "LIVRE" else "Não recomendado para menores de ${rating.trim().uppercase()}"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "CLASSIFICAÇÃO INDICATIVA",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Os números do ícone são "vazados": sobre o branco aparecem em branco, como no selo oficial.
            Image(
                painter = painterResource(id = classifDrawableRes(rating)),
                contentDescription = "Classificação $rating",
                modifier = Modifier.size(64.dp)
            )
            Column(
                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    warnings,
                    color = Color.Black,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
        Text(
            footer,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
