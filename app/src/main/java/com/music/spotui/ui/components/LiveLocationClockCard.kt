package com.music.spotui.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.spotui.data.preferences.ExperiencePreferences
import com.music.spotui.ui.theme.AppPalette
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private data class LocalClockSnapshot(
    val formattedTime: String,
    val formattedDate: String,
    val dayGreeting: String,
    val locationLabel: String,
    val timezoneShort: String,
)

private fun buildLocalClockSnapshot(
    is24Hour: Boolean,
    showSeconds: Boolean,
): LocalClockSnapshot {
    val now = ZonedDateTime.now()
    val locale = Locale.getDefault()
    val pattern = when {
        is24Hour && showSeconds -> "HH:mm:ss"
        is24Hour -> "HH:mm"
        showSeconds -> "hh:mm:ss a"
        else -> "hh:mm a"
    }
    val timeText = now.format(DateTimeFormatter.ofPattern(pattern, locale))
    val dateText = now.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
    val hour = now.hour
    val greeting = when {
        hour < 5 -> "Late Night Listening"
        hour < 12 -> "Good Morning"
        hour < 17 -> "Good Afternoon"
        hour < 21 -> "Good Evening"
        else -> "Night Owl Session"
    }

    val zone = now.zone
    val rawCity = zone.id.substringAfterLast('/', zone.id)
        .replace('_', ' ')
        .ifBlank { zone.id }
    val countryName = locale.displayCountry.takeIf { it.isNotBlank() }
    val locationLabel = if (countryName != null && !rawCity.equals(countryName, ignoreCase = true)) {
        "$rawCity · $countryName"
    } else {
        rawCity
    }
    val tzShort = zone.getDisplayName(TextStyle.SHORT, locale)

    return LocalClockSnapshot(
        formattedTime = timeText,
        formattedDate = dateText,
        dayGreeting = greeting,
        locationLabel = locationLabel,
        timezoneShort = tzShort,
    )
}

/**
 * Live auto-location Home Screen clock card using system timezone and locale (no runtime location
 * permission required) that updates every second.
 */
@Composable
fun LiveLocationClockCard(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val rev by ExperiencePreferences.experienceRevision.collectAsState()
    val showSeconds = remember(rev) { ExperiencePreferences.isHomeClockSecondsEnabled(context) }
    val is24Hour = remember(rev) { DateFormat.is24HourFormat(context) }
    var snapshot by remember(is24Hour, showSeconds) {
        mutableStateOf(buildLocalClockSnapshot(is24Hour, showSeconds))
    }

    LaunchedEffect(is24Hour, showSeconds) {
        while (true) {
            snapshot = buildLocalClockSnapshot(is24Hour, showSeconds)
            delay(1_000L)
        }
    }

    val primary = AppPalette
    val surface = com.music.spotui.ui.theme.GridBackground
    val bg = com.music.spotui.ui.theme.AppBackground
    val cardGradient = remember(primary, surface, bg) {
        Brush.horizontalGradient(
            colors = listOf(
                surface.copy(alpha = 0.94f),
                bg.copy(alpha = 0.96f),
                primary.copy(alpha = 0.14f),
            ),
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(cardGradient)
            .border(1.dp, primary.copy(alpha = 0.24f), RoundedCornerShape(20.dp))
            .testTag("home_live_location_clock")
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Local Time",
                    tint = AppPalette,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = snapshot.dayGreeting.uppercase(),
                    color = AppPalette,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.7.sp,
                )
            }
            Text(
                text = snapshot.formattedTime,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = "Auto Timezone Region",
                    tint = Color(0xFF9BA3B4),
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "${snapshot.locationLabel} (${snapshot.timezoneShort})",
                    color = Color(0xFFB3B8C4),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = snapshot.formattedDate,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "AUTO ZONE",
                color = AppPalette,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp,
            )
        }
    }
}
