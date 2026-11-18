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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.spotui.R
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.Month
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Pure calendar calculator for U.S. Thanksgiving (4th Thursday of November).
 * Automatically counts down to the upcoming Thanksgiving and rolls seamlessly to
 * next year once the holiday concludes.
 */
object ThanksgivingClockCalculator {
    data class CountdownSnapshot(
        val targetYear: Int,
        val targetDate: LocalDate,
        val formattedDate: String,
        val isThanksgivingDay: Boolean,
        val days: Long,
        val hours: Long,
        val minutes: Long,
        val seconds: Long,
        val harvestProgress: Float,
    )

    fun thanksgivingDateForYear(year: Int): LocalDate =
        LocalDate.of(year, Month.NOVEMBER, 1)
            .with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY))

    fun calculate(now: ZonedDateTime = ZonedDateTime.now()): CountdownSnapshot {
        val today = now.toLocalDate()
        val currentYearDate = thanksgivingDateForYear(today.year)
        val formatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

        if (today == currentYearDate) {
            return CountdownSnapshot(
                targetYear = today.year,
                targetDate = currentYearDate,
                formattedDate = currentYearDate.format(formatter),
                isThanksgivingDay = true,
                days = 0L,
                hours = 0L,
                minutes = 0L,
                seconds = 0L,
                harvestProgress = 1f,
            )
        }

        val targetDate = if (today.isBefore(currentYearDate)) {
            currentYearDate
        } else {
            thanksgivingDateForYear(today.year + 1)
        }
        val targetStart = targetDate.atStartOfDay(now.zone)
        val diff = Duration.between(now, targetStart).let { if (it.isNegative) Duration.ZERO else it }
        val totalSeconds = diff.seconds.coerceAtLeast(0L)
        val days = totalSeconds / 86_400L
        val hours = (totalSeconds % 86_400L) / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L

        // Season progress across November (Nov 1 -> Thanksgiving Day = 1.0f)
        val seasonStart = LocalDate.of(targetDate.year, Month.NOVEMBER, 1).atStartOfDay(now.zone)
        val fullSeasonSeconds = Duration.between(seasonStart, targetStart).seconds.coerceAtLeast(1L)
        val elapsedSeasonSeconds = Duration.between(seasonStart, now).seconds.coerceIn(0L, fullSeasonSeconds)
        val progress = (elapsedSeasonSeconds.toFloat() / fullSeasonSeconds.toFloat()).coerceIn(0f, 1f)

        return CountdownSnapshot(
            targetYear = targetDate.year,
            targetDate = targetDate,
            formattedDate = targetDate.format(formatter),
            isThanksgivingDay = false,
            days = days,
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            harvestProgress = progress,
        )
    }
}

private val AutumnAmber = Color(0xFFF58B2B)
private val AutumnCopper = Color(0xFFE8622C)
private val AutumnCream = Color(0xFFFFEED9)

@Composable
fun ThanksgivingCountdownBanner(
    onAutumnMixClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var snapshot by remember { mutableStateOf(ThanksgivingClockCalculator.calculate()) }
    var expanded by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            snapshot = ThanksgivingClockCalculator.calculate()
            delay(1_000L)
        }
    }

    val cardGradient = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF3A1E0A),
                Color(0xFF281509),
                Color(0xFF191210),
            ),
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(cardGradient)
            .border(1.dp, AutumnAmber.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
            .testTag("thanksgiving_countdown_banner")
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(id = R.drawable.spotui_thanksgiving_themed_badge),
                contentDescription = "Thanksgiving Harvest Badge",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, AutumnAmber.copy(alpha = 0.7f), CircleShape),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (snapshot.isThanksgivingDay) {
                        "HAPPY THANKSGIVING ${snapshot.targetYear}"
                    } else {
                        "THANKSGIVING ${snapshot.targetYear} · ${snapshot.formattedDate.uppercase()}"
                    },
                    color = AutumnAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (snapshot.isThanksgivingDay) {
                        "Warm harvest beats are playing all day!"
                    } else {
                        "${snapshot.days}d ${snapshot.hours}h until Turkey Day"
                    },
                    color = AutumnCream,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { expanded = !expanded }
                    .testTag("thanksgiving_toggle_expand"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse countdown" else "Expand countdown",
                    tint = AutumnCream,
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!snapshot.isThanksgivingDay) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CountdownTile(
                            value = snapshot.days.toString().padStart(2, '0'),
                            label = "DAYS",
                            modifier = Modifier.weight(1f),
                        )
                        CountdownTile(
                            value = snapshot.hours.toString().padStart(2, '0'),
                            label = "HRS",
                            modifier = Modifier.weight(1f),
                        )
                        CountdownTile(
                            value = snapshot.minutes.toString().padStart(2, '0'),
                            label = "MIN",
                            modifier = Modifier.weight(1f),
                        )
                        CountdownTile(
                            value = snapshot.seconds.toString().padStart(2, '0'),
                            label = "SEC",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    LinearProgressIndicator(
                        progress = { snapshot.harvestProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = AutumnAmber,
                        trackColor = Color.White.copy(alpha = 0.12f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (snapshot.isThanksgivingDay) {
                            "Curate acoustic & dinner table mixes"
                        } else {
                            "November harvest countdown · Live clock"
                        },
                        color = AutumnCream.copy(alpha = 0.72f),
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
                            .background(Brush.horizontalGradient(listOf(AutumnCopper, AutumnAmber)))
                            .clickable(onClick = onAutumnMixClick)
                            .testTag("thanksgiving_autumn_mix_button")
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Open Autumn Vibes",
                            tint = Color(0xFF1B0E05),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Autumn Vibes",
                            color = Color(0xFF1B0E05),
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
private fun CountdownTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.34f))
            .border(1.dp, AutumnAmber.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            color = AutumnCream,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = label,
            color = AutumnAmber,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
    }
}
