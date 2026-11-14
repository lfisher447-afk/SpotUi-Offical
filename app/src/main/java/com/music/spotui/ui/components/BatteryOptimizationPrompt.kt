package com.music.spotui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.spotui.data.BatteryOptimizationHelper
import com.music.spotui.data.preferences.isBatteryPromptDismissed
import com.music.spotui.data.preferences.setBatteryPromptDismissed
import com.music.spotui.ui.theme.AppPalette

@Composable
fun BatteryOptimizationPrompt() {
    val context = LocalContext.current
    var visible by remember {
        mutableStateOf(
            !BatteryOptimizationHelper.isIgnoringBatteryOptimization(context) &&
                !isBatteryPromptDismissed(context)
        )
    }

    if (!visible) return

    val manufacturerTip = remember { BatteryOptimizationHelper.getManufacturerTips() }

    AlertDialog(
        onDismissRequest = { visible = false },
        containerColor = Color(0xFF141419),
        titleContentColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BatterySaver, contentDescription = null, tint = AppPalette)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Uninterrupted Background Audio",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "To prevent Samsung One UI or Android battery optimization from pausing music when the screen turns off, allow Spotui unrestricted background battery use.",
                    color = Color(0xFFD5DAE2),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                manufacturerTip?.let { (oem, tip) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E2028), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                    ) {
                        Text(
                            text = "$oem Tip",
                            color = AppPalette,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = tip,
                            color = Color(0xFFB3B8C4),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    visible = false
                    runCatching {
                        context.startActivity(BatteryOptimizationHelper.buildRequestOptimizationIntent(context))
                    }.onFailure {
                        runCatching {
                            context.startActivity(BatteryOptimizationHelper.buildAppSettingsIntent(context))
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppPalette, contentColor = Color.Black),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("battery_prompt_allow_button"),
            ) {
                Text("Allow Unrestricted", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        setBatteryPromptDismissed(context, true)
                        visible = false
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("battery_prompt_never_button"),
                ) {
                    Text("Don't Ask Again", color = Color(0xFF9FA9BC))
                }
                TextButton(
                    onClick = { visible = false },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("battery_prompt_later_button"),
                ) {
                    Text("Later", color = Color.White)
                }
            }
        },
    )
}
