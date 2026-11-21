package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.games.GroupedGameEntry
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.PerformanceProfile
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private val SleeveCream = Color(0xFFF0DCBA)
private val StageAmber = Color(0xFFE7A252)
private val ScarfRed = Color(0xFF9C334D)
private val RecordBlack = Color(0xFF0B0B10)

private val GroupedGameEntry.vinylGame: Game
    get() =
        when (this) {
            is GroupedGameEntry.Single -> game
            is GroupedGameEntry.MultiDisc -> representative
        }

/** One selected label and at most four neighbours, regardless of library size. */
@Composable
fun HardRockVinylLayout(
    games: List<GroupedGameEntry>,
    selectedIndex: Int,
    systemTitle: String,
    description: String?,
    onSelectIndex: (Int) -> Unit,
    onConfirmGame: (GroupedGameEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val index = if (games.isEmpty()) 0 else selectedIndex.coerceIn(games.indices)
    val entry = games.getOrNull(index)
    BoxWithConstraints(modifier.background(RecordBlack)) {
        Image(
            painterResource(R.drawable.hard_rock_backstage),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 14.dp)) {
                SleeveNotes(entry, index, games.size, systemTitle, description, Modifier.weight(0.40f).fillMaxHeight())
                RecordSelector(games, index, onSelectIndex, onConfirmGame, Modifier.weight(0.60f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                RecordSelector(games, index, onSelectIndex, onConfirmGame, Modifier.weight(0.55f).fillMaxWidth())
                SleeveNotes(entry, index, games.size, systemTitle, description, Modifier.weight(0.45f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SleeveNotes(
    entry: GroupedGameEntry?,
    index: Int,
    count: Int,
    systemTitle: String,
    description: String?,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier.background(RecordBlack.copy(alpha = 0.88f))) {
        val compact = maxHeight < 310.dp
        Column(Modifier.fillMaxSize().padding(if (compact) 8.dp else 14.dp)) {
            Text(
                "LADO B",
                color = SleeveCream,
                fontSize = if (compact) 21.sp else 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp,
            )
            Text(
                "HARD ROCK • 80 / 90",
                color = StageAmber,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().background(ScarfRed).padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    systemTitle.uppercase(),
                    color = SleeveCream,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${if (count == 0) 0 else index + 1} / $count",
                    color = SleeveCream,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                entry?.vinylGame?.displayName() ?: "Sua coleção",
                color = SleeveCream,
                fontSize = if (compact) 18.sp else 24.sp,
                lineHeight = if (compact) 22.sp else 28.sp,
                fontWeight = FontWeight.Black,
                maxLines = if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry is GroupedGameEntry.MultiDisc) {
                Text(
                    "VERSÕES / DISCOS • A PARA ESCOLHER",
                    color = StageAmber,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (entry?.vinylGame?.isFavorite == true) {
                Text("♥ FAVORITO", color = StageAmber, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(StageAmber.copy(alpha = 0.5f)))
            Text(
                "NOTAS DO ENCARTE",
                color = StageAmber,
                fontSize = 9.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            key(entry?.vinylGame?.id) {
                Text(
                    description?.takeIf {
                        it.isNotBlank()
                    } ?: "Selecione um jogo no vinil para explorar a coleção. " +
                        "As informações disponíveis aparecem neste encarte.",
                    color = SleeveCream.copy(alpha = 0.88f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                )
            }
            Text(
                "← → TROCAR FAIXA    •    A ABRIR",
                color = SleeveCream,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** Read only in the layer: covers and metadata never recompose for record animation. */
@Composable
private fun recordRotation(): State<Float> {
    val motion = LocalFrontendMotion.current
    val angle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(motion.foreground, motion.profile) {
        if (!motion.foreground) return@LaunchedEffect
        val frameMillis =
            when (motion.profile) {
                PerformanceProfile.QUALITY -> 16L
                PerformanceProfile.BALANCED -> 33L
                PerformanceProfile.PERFORMANCE -> 50L
            }
        var previous = SystemClock.uptimeMillis()
        while (true) {
            delay(frameMillis)
            val now = SystemClock.uptimeMillis()
            angle.floatValue = (angle.floatValue + (now - previous).coerceAtMost(100L) * 0.015f) % 360f
            previous = now
        }
    }
    return angle
}

@Composable
private fun RecordSelector(
    games: List<GroupedGameEntry>,
    index: Int,
    onSelectIndex: (Int) -> Unit,
    onConfirmGame: (GroupedGameEntry) -> Unit,
    modifier: Modifier,
) {
    val rotation = recordRotation()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "ESCOLHA SUA PRÓXIMA FAIXA",
            color = SleeveCream,
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.background(RecordBlack.copy(alpha = 0.8f)).padding(8.dp),
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val diameter = minOf(maxWidth, maxHeight)
            Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation.value }) {
                    val r = size.minDimension * 0.48f
                    drawCircle(Color(0xFF020205), r)
                    drawCircle(Color(0xFF42414B), r, style = Stroke(2.dp.toPx()))
                    for (groove in 0..34) {
                        drawCircle(
                            if (groove % 4 == 0) Color(0xFF32313A) else Color(0xFF1C1B24),
                            r * (0.40f + groove * 0.0165f),
                            style = Stroke(0.65.dp.toPx()),
                        )
                    }
                    drawArc(
                        Color(0xFF8B8196).copy(alpha = 0.20f),
                        215f,
                        24f,
                        false,
                        Offset(center.x - r * 0.94f, center.y - r * 0.94f),
                        Size(r * 1.88f, r * 1.88f),
                        style = Stroke(r * 0.29f),
                    )
                    drawArc(
                        StageAmber.copy(alpha = 0.12f),
                        35f,
                        18f,
                        false,
                        Offset(center.x - r * 0.94f, center.y - r * 0.94f),
                        Size(r * 1.88f, r * 1.88f),
                        style = Stroke(r * 0.29f),
                    )
                    drawCircle(ScarfRed, r * 0.38f)
                }
                games.getOrNull(index)?.let { entry ->
                    Column(
                        Modifier.size(diameter * 0.34f).clip(CircleShape).background(SleeveCream).clickable {
                            onConfirmGame(entry)
                        }.padding(5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        LemuroidGameImage(
                            game = entry.vinylGame,
                            preserveAspect = true,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxWidth().clip(CircleShape),
                        )
                        Text(
                            "▶ A • ABRIR",
                            color = RecordBlack,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
                // Upright sleeves remain readable while the record spins underneath.
                val neighbours =
                    remember(games.size, index) {
                        listOf(-2, -1, 1, 2).map { it to index + it }.filter { it.second in games.indices }
                    }
                neighbours.forEach { (delta, neighbour) ->
                    val degrees =
                        when (delta) {
                            -2 -> 225.0
                            -1 -> 145.0
                            1 -> 35.0
                            else -> -45.0
                        }
                    val radians = Math.toRadians(degrees)
                    val x = diameter * (cos(radians).toFloat() * 0.345f)
                    val y = diameter * (sin(radians).toFloat() * 0.345f)
                    Column(
                        Modifier.offset(
                            x,
                            y,
                        ).width(
                            diameter * 0.24f,
                        ).background(
                            RecordBlack.copy(alpha = 0.94f),
                        ).border(1.dp, SleeveCream.copy(alpha = 0.4f)).clickable {
                            onSelectIndex(neighbour)
                        }.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        LemuroidGameImage(
                            game = games[neighbour].vinylGame,
                            modifier = Modifier.size(diameter * 0.15f),
                            preserveAspect = true,
                            contentScale = ContentScale.Fit,
                        )
                        Text(
                            "${neighbour + 1}. ${games[neighbour].vinylGame.displayName()}",
                            color = SleeveCream,
                            fontSize = 8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Row(
            Modifier.padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecordButton("‹ ANTERIOR", games.isNotEmpty()) { onSelectIndex((index - 1 + games.size) % games.size) }
            Text("33⅓", color = StageAmber, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            RecordButton("PRÓXIMA ›", games.isNotEmpty()) { onSelectIndex((index + 1) % games.size) }
        }
    }
}

@Composable
private fun RecordButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = RecordBlack,
        border = BorderStroke(1.dp, StageAmber.copy(alpha = 0.6f)),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            label,
            color = SleeveCream,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
