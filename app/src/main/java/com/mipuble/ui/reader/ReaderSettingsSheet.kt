package com.mipuble.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mipuble.domain.model.PageTurnMode
import com.mipuble.domain.model.ReaderFont
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Display", style = MaterialTheme.typography.titleLarge)

            ThemeRow(selected = preferences.theme, onSelect = { onEvent(ReaderEvent.SetTheme(it)) })

            FontPickerRow(
                selected = preferences.font,
                onSelect = { onEvent(ReaderEvent.SetFont(it)) },
            )

            PageTurnModeRow(
                selected = preferences.pageTurnMode,
                onSelect = { onEvent(ReaderEvent.SetPageTurnMode(it)) },
            )

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

/** Brand-facing names for the reader page themes. */
private val ReaderTheme.displayLabel: String
    get() = when (this) {
        ReaderTheme.LIGHT -> "Paper"
        ReaderTheme.SEPIA -> "Sepia"
        ReaderTheme.DARK -> "Ink"
        ReaderTheme.BLACK -> "Black"
    }

@Composable
internal fun ThemeRow(selected: ReaderTheme, onSelect: (ReaderTheme) -> Unit) {
    // Scrollable so the 4x52dp swatches survive narrow/split-screen widths
    // (inside Settings' card the row would otherwise hard-clip "Black").
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        ReaderTheme.entries.forEach { theme ->
            val colors = ReaderThemeColors.of(theme)
            val isSelected = theme == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(colors.background)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable { onSelect(theme) },
                    contentAlignment = Alignment.Center,
                ) {
                    // A serif "A" in the theme's own text color previews the page.
                    Text(
                        "A",
                        color = colors.text,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = theme.displayLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun StepperRow(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalIconButton(
            onClick = onDecrease,
            shape = RoundedCornerShape(11.dp),
            modifier = Modifier.size(36.dp),
        ) {
            Text("−", style = MaterialTheme.typography.titleMedium) // minus sign
        }
        Text(
            text = value,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .width(56.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        FilledTonalIconButton(
            onClick = onIncrease,
            shape = RoundedCornerShape(11.dp),
            modifier = Modifier.size(36.dp),
        ) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
internal fun FontPickerRow(selected: ReaderFont, onSelect: (ReaderFont) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedFamily = rememberReaderFontFamily(selected)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Font", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = { expanded = true }, shape = CircleShape) {
            // Show the choice IN its own typeface, not just its name.
            Text(selected.displayName, fontFamily = selectedFamily)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReaderFont.entries.forEach { font ->
                val family = rememberReaderFontFamily(font)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = font.displayName,
                            fontFamily = family,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    trailingIcon = {
                        if (font == selected) {
                            Text("✓", style = MaterialTheme.typography.titleMedium)
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(font)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PageTurnModeRow(selected: PageTurnMode, onSelect: (PageTurnMode) -> Unit) {
    Column {
        Text("Page turn", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            PageTurnMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, PageTurnMode.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Text(if (mode == PageTurnMode.SCROLL) "Scroll" else "Swipe pages")
                }
            }
        }
    }
}

@Composable
private fun BrightnessSection(
    preferences: ReaderPreferences,
    onEvent: (ReaderEvent) -> Unit,
) {
    val enabled = !preferences.followSystemBrightness

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Brightness", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
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
                enabled = enabled,
                shape = RoundedCornerShape(11.dp),
                modifier = Modifier.size(36.dp),
            ) {
                Text("−", style = MaterialTheme.typography.titleMedium) // brightness down 1%
            }
            Slider(
                value = preferences.brightnessPercent.toFloat(),
                onValueChange = { onEvent(ReaderEvent.SetBrightness(it.toInt())) },
                valueRange = ReaderSettingsBounds.BRIGHTNESS_MIN.toFloat()..ReaderSettingsBounds.BRIGHTNESS_MAX.toFloat(),
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            FilledTonalIconButton(
                onClick = { onEvent(ReaderEvent.IncreaseBrightness) },
                enabled = enabled,
                shape = RoundedCornerShape(11.dp),
                modifier = Modifier.size(36.dp),
            ) {
                Text("+", style = MaterialTheme.typography.titleMedium) // brightness up 1%
            }
        }

        if (preferences.followSystemBrightness) {
            Text(
                text = "Using system brightness",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${preferences.brightnessPercent}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                // A pill that signals the signature 1%-precision feature.
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "±1%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "per tap",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
