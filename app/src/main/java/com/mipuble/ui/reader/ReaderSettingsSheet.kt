package com.mipuble.ui.reader

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mipuble.domain.model.ReaderPreferences
import com.mipuble.domain.model.ReaderSettingsBounds
import com.mipuble.domain.model.ReaderTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    preferences: ReaderPreferences,
    onEvent: (ReaderEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Display", style = MaterialTheme.typography.titleMedium)

            ThemeRow(selected = preferences.theme, onSelect = { onEvent(ReaderEvent.SetTheme(it)) })

            StepperRow(
                label = "Text size",
                value = "${preferences.fontScalePercent}%",
                onDecrease = { onEvent(ReaderEvent.DecreaseFont) },
                onIncrease = { onEvent(ReaderEvent.IncreaseFont) },
            )

            StepperRow(
                label = "Line spacing",
                value = "%.1f".format(preferences.lineSpacingPercent / 100f),
                onDecrease = { onEvent(ReaderEvent.DecreaseLineSpacing) },
                onIncrease = { onEvent(ReaderEvent.IncreaseLineSpacing) },
            )

            HorizontalDivider()

            BrightnessSection(preferences = preferences, onEvent = onEvent)
        }
    }
}

@Composable
private fun ThemeRow(selected: ReaderTheme, onSelect: (ReaderTheme) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ReaderTheme.entries.forEach { theme ->
            val colors = ReaderThemeColors.of(theme)
            val isSelected = theme == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.background)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable { onSelect(theme) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("A", color = colors.text, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = theme.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalIconButton(onClick = onDecrease) {
            Text("−", style = MaterialTheme.typography.titleLarge) // minus sign
        }
        Text(
            text = value,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .width(56.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        FilledTonalIconButton(onClick = onIncrease) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun BrightnessSection(
    preferences: ReaderPreferences,
    onEvent: (ReaderEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Brightness", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("Follow system", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = preferences.followSystemBrightness,
                onCheckedChange = { onEvent(ReaderEvent.SetFollowSystemBrightness(it)) },
            )
        }

        // Manual ±1% control — the precise brightness feature.
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { onEvent(ReaderEvent.DecreaseBrightness) },
                enabled = !preferences.followSystemBrightness,
            ) {
                Text("−", style = MaterialTheme.typography.titleLarge) // brightness down 1%
            }
            Slider(
                value = preferences.brightnessPercent.toFloat(),
                onValueChange = { onEvent(ReaderEvent.SetBrightness(it.toInt())) },
                valueRange = ReaderSettingsBounds.BRIGHTNESS_MIN.toFloat()..ReaderSettingsBounds.BRIGHTNESS_MAX.toFloat(),
                enabled = !preferences.followSystemBrightness,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            FilledTonalIconButton(
                onClick = { onEvent(ReaderEvent.IncreaseBrightness) },
                enabled = !preferences.followSystemBrightness,
            ) {
                Text("+", style = MaterialTheme.typography.titleLarge) // brightness up 1%
            }
        }

        Text(
            text = if (preferences.followSystemBrightness) {
                "Using system brightness"
            } else {
                "${preferences.brightnessPercent}%  ·  ±1% per tap"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
