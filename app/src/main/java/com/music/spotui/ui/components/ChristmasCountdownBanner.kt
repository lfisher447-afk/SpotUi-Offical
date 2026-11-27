package com.music.spotui.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.spotui.R
import com.music.spotui.audio.UiSoundEngine
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.Month
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure calendar calculator for Christmas Day (December 25).
 * Automatically counts down to the upcoming Christmas and rolls seamlessly to
 * next year once Christmas concludes.
 */
object ChristmasClockCalculator {
    data class CountdownSnapshot(
        val targetYear: Int,
        val targetDate: LocalDate,
        val formattedDate: String,
        val isChristmasEve: Boolean,
        val isChristmasDay: Boolean,
        val days: Long,
        val hours: Long,
        val minutes: Long,
        val seconds: Long,
        val holidayProgress: Float,
    )

    fun christmasDateForYear(year: Int): LocalDate =
        LocalDate.of(year, Month.DECEMBER, 25)

    fun calculate(now: ZonedDateTime = ZonedDateTime.now()): CountdownSnapshot {
        val today = now.toLocalDate()
        val currentYearDate = christmasDateForYear(today.year)
        val formatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

        if (today == currentYearDate) {
            return CountdownSnapshot(
                targetYear = today.year,
                targetDate = currentYearDate,
                formattedDate = currentYearDate.format(formatter),
                isChristmasEve = false,
                isChristmasDay = true,
                days = 0L,
                hours = 0L,
                minutes = 0L,
                seconds = 0L,
                holidayProgress = 1f,
            )
        }

        val targetDate = if (today.isBefore(currentYearDate)) {
            currentYearDate
        } else {
            christmasDateForYear(today.year + 1)
        }
        val isEve = today == targetDate.minusDays(1)
        val targetStart = targetDate.atStartOfDay(now.zone)
        val diff = Duration.between(now, targetStart).let { if (it.isNegative) Duration.ZERO else it }
        val totalSeconds = diff.seconds.coerceAtLeast(0L)
        val days = totalSeconds / 86_400L
        val hours = (totalSeconds % 86_400L) / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L

        // Holiday season progress across Nov 26 -> Dec 25 (Christmas Day = 1.0f)
        val seasonStart = LocalDate.of(targetDate.year, Month.NOVEMBER, 26).atStartOfDay(now.zone)
        val fullSeasonSeconds = Duration.between(seasonStart, targetStart).seconds.coerceAtLeast(1L)
        val elapsedSeasonSeconds = Duration.between(seasonStart, now).seconds.coerceIn(0L, fullSeasonSeconds)
        val progress = (elapsedSeasonSeconds.toFloat() / fullSeasonSeconds.toFloat()).coerceIn(0f, 1f)

        return CountdownSnapshot(
            targetYear = targetDate.year,
            targetDate = targetDate,
            formattedDate = targetDate.format(formatter),
            isChristmasEve = isEve,
            isChristmasDay = false,
            days = days,
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            holidayProgress = progress,
        )
    }
}

private val WinterPine = Color(0xFF2CE69B)
private val HollyRuby = Color(0xFFE11D48)
private val ChampagneGold = Color(0xFFFFD166)
private val FrostCream = Color(0xFFF3FAF7)

@Composable
fun ChristmasCountdownBanner(
    onHolidayMixClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(ChristmasClockCalculator.calculate()) }
    var expanded by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            snapshot = ChristmasClockCalculator.calculate()
            delay(1_000L)
        }
    }

    val cardGradient = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF0C2A24),
                Color(0xFF101D2E),
                Color(0xFF251220),
            ),
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(cardGradient)
            .border(
                width = 1.2.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        WinterPine.copy(alpha = 0.55f),
                        ChampagneGold.copy(alpha = 0.45f),
                        HollyRuby.copy(alpha = 0.55f),
                    ),
                ),
                shape = RoundedCornerShape(22.dp),
            )
            .testTag("christmas_countdown_banner")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(id = R.drawable.spotui_logo_santa_cookie),
                contentDescription = "SpotUI Festive Christmas Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .border(1.8.dp, ChampagneGold.copy(alpha = 0.78f), CircleShape),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AcUnit,
                        contentDescription = null,
                        tint = ChampagneGold,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = when {
                            snapshot.isChristmasDay -> "MERRY CHRISTMAS ${snapshot.targetYear}"
                            snapshot.isChristmasEve -> "CHRISTMAS EVE · ALMOST TIME"
                            else -> "CHRISTMAS ${snapshot.targetYear} · ${snapshot.formattedDate.uppercase()}"
                        },
                        color = ChampagneGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        snapshot.isChristmasDay -> "Winter classics & holiday warmth all day!"
                        snapshot.isChristmasEve -> "${snapshot.hours}h ${snapshot.minutes}m until Christmas morning"
                        else -> "${snapshot.days}d ${snapshot.hours}h until Christmas"
                    },
                    color = FrostCream,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.07f))
                    .clickable {
                        UiSoundEngine.playTapChime(context)
                        expanded = !expanded
                    }
                    .testTag("christmas_toggle_expand"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse Christmas countdown" else "Expand Christmas countdown",
                    tint = FrostCream,
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!snapshot.isChristmasDay) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ChristmasCountdownTile(
                            value = snapshot.days.toString().padStart(2, '0'),
                            label = "DAYS",
                            accent = WinterPine,
                            modifier = Modifier.weight(1f),
                        )
                        ChristmasCountdownTile(
                            value = snapshot.hours.toString().padStart(2, '0'),
                            label = "HRS",
                            accent = ChampagneGold,
                            modifier = Modifier.weight(1f),
                        )
                        ChristmasCountdownTile(
                            value = snapshot.minutes.toString().padStart(2, '0'),
                            label = "MIN",
                            accent = HollyRuby,
                            modifier = Modifier.weight(1f),
                        )
                        ChristmasCountdownTile(
                            value = snapshot.seconds.toString().padStart(2, '0'),
                            label = "SEC",
                            accent = WinterPine,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    LinearProgressIndicator(
                        progress = { snapshot.holidayProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = WinterPine,
                        trackColor = Color.White.copy(alpha = 0.12f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (snapshot.isChristmasDay) {
                            "Curate cozy fireside & holiday mixes"
                        } else {
                            "Winter wonderland countdown · Live clock"
                        },
                        color = FrostCream.copy(alpha = 0.78f),
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(HollyRuby, ChampagneGold)))
                            .clickable {
                                UiSoundEngine.playTapChime(context)
                                onHolidayMixClick()
                            }
                            .testTag("christmas_holiday_mix_button")
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Open Holiday Vibes",
                            tint = Color(0xFF160B0F),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Holiday Vibes",
                            color = Color(0xFF160B0F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChristmasCountdownTile(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.36f))
            .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            color = FrostCream,
            fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = label,
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp,
        )
    }
}
