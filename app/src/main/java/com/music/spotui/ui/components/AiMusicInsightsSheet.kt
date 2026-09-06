package com.music.spotui.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.spotui.data.ai.GeminiSearchService
import com.music.spotui.data.ai.GroundedInsight
import kotlinx.coroutines.launch

/**
 * Bottom sheet providing AI-powered music insights grounded in Google Search data.
 * Powered by gemini-3.5-flash with the googleSearch tool.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiMusicInsightsSheet(
    initialTrackTitle: String = "",
    initialArtistName: String = "",
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var userQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentInsight by remember { mutableStateOf<GroundedInsight?>(null) }

    fun executeQuery(query: String) {
        if (query.isBlank() || isLoading) return
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            val result = GeminiSearchService.queryWithSearchGrounding(query)
            result.onSuccess { insight ->
                currentInsight = insight
                isLoading = false
            }.onFailure { ex ->
                errorMessage = ex.message ?: "Failed to retrieve grounded insight."
                isLoading = false
            }
        }
    }

    // Auto-fetch song backstory if track title and artist are supplied
    LaunchedEffect(initialTrackTitle, initialArtistName) {
        if (initialTrackTitle.isNotBlank() && initialArtistName.isNotBlank() && currentInsight == null) {
            val defaultPrompt = "What is the story, meaning, and background behind the song '$initialTrackTitle' by $initialArtistName? Include recent news and tour details if any."
            userQuery = defaultPrompt
            executeQuery(defaultPrompt)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141419),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .heightIn(max = 680.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "SpotUI AI Insights",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = currentInsight?.engineBadge ?: "Gemini Music Intelligence • Key-Free AI",
                            color = Color(0xFF9E9EA8),
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Prompt Chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val song = initialTrackTitle.ifBlank { "this song" }
                val artist = initialArtistName.ifBlank { "this artist" }

                SuggestionChip(
                    onClick = {
                        val q = "What is the meaning and background story of '$song' by $artist?"
                        userQuery = q
                        executeQuery(q)
                    },
                    label = { Text("🎵 Song Meaning", fontSize = 12.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = Color(0xFF22222B),
                        labelColor = Color(0xFFD4D4DE)
                    ),
                    border = null
                )

                SuggestionChip(
                    onClick = {
                        val q = "What are the latest tour dates, concert schedule, and upcoming shows for $artist?"
                        userQuery = q
                        executeQuery(q)
                    },
                    label = { Text("🎫 Tour Dates", fontSize = 12.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = Color(0xFF22222B),
                        labelColor = Color(0xFFD4D4DE)
                    ),
                    border = null
                )

                SuggestionChip(
                    onClick = {
                        val q = "What are the latest news, awards, and album releases for $artist in 2025/2026?"
                        userQuery = q
                        executeQuery(q)
                    },
                    label = { Text("📰 Latest News", fontSize = 12.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = Color(0xFF22222B),
                        labelColor = Color(0xFFD4D4DE)
                    ),
                    border = null
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scrollable Content Body
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1B1B22))
                    .padding(16.dp)
            ) {
                when {
                    isLoading -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF1DB954),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Grounding query with Google Search...",
                                color = Color(0xFFAAAAAA),
                                fontSize = 13.sp
                            )
                        }
                    }

                    errorMessage != null -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = errorMessage ?: "Unknown error",
                                color = Color(0xFFE22134),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            IconButton(
                                onClick = { executeQuery(userQuery) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    currentInsight != null -> {
                        val insight = currentInsight!!
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = insight.answer,
                                color = Color(0xFFEEEEEE),
                                fontSize = 14.sp,
                                lineHeight = 21.sp
                            )

                            // Google Grounding Citations Section
                            if (insight.sources.isNotEmpty() || insight.searchQueries.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = Color(0xFF2D2D38))
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Color(0xFF4285F4),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (insight.isFreeMode) "Knowledge & Music Sources" else "Google Search Grounded Sources",
                                        color = Color(0xFF9E9EA8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    insight.sources.forEach { source ->
                                        Surface(
                                            color = Color(0xFF282834),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.clickable {
                                                runCatching {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(source.uri)).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                }
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = source.title,
                                                    color = Color(0xFF8AB4F8),
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.widthIn(max = 200.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                    contentDescription = null,
                                                    tint = Color(0xFF8AB4F8),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Ask anything about songs, artists, lyrics, or upcoming concerts!",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Query Input Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = userQuery,
                    onValueChange = { userQuery = it },
                    placeholder = {
                        Text(
                            "Ask music AI (e.g. tour dates, story)...",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF1B1B22),
                        unfocusedContainerColor = Color(0xFF1B1B22),
                        focusedBorderColor = Color(0xFF1DB954),
                        unfocusedBorderColor = Color(0xFF2E2E38)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { executeQuery(userQuery) },
                    enabled = userQuery.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (userQuery.isNotBlank() && !isLoading) Color(0xFF1DB954) else Color(0xFF282834))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Query",
                        tint = if (userQuery.isNotBlank() && !isLoading) Color.Black else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
