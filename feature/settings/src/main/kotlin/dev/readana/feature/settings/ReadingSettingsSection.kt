package dev.readana.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.model.PageTurnMode
import dev.readana.core.model.PenMode
import dev.readana.core.model.ReaderColumns
import dev.readana.core.model.ReaderFont
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.ReaderTheme

/**
 * How books read by default: the handful of choices worth changing often.
 *
 * Adjusting a setting while a book is open keeps the change with that book, which is what makes a
 * comic and a novel able to disagree. This is where the starting point for every new book is set.
 */
@Composable
internal fun ReadingSettingsSection(
    settings: ReaderSettings,
    onChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSection(
        title = stringResource(R.string.feature_settings_section_reading),
        icon = ReadanaIcons.Library,
        modifier = modifier
    ) {
        SettingsNote(stringResource(R.string.feature_settings_reading_help))

        ChoiceRow(
            label = stringResource(R.string.feature_settings_reading_theme),
            options = ReaderTheme.entries,
            selected = settings.theme,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = { theme -> onChange { it.copy(theme = theme) } }
        )
        ChoiceRow(
            label = stringResource(R.string.feature_settings_reading_font),
            options = ReaderFont.entries,
            selected = settings.fontFamily,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = { font -> onChange { it.copy(fontFamily = font) } }
        )
        ChoiceRow(
            label = stringResource(R.string.feature_settings_reading_page_turn),
            options = PageTurnMode.entries,
            selected = settings.pageTurnMode,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = { mode -> onChange { it.copy(pageTurnMode = mode) } }
        )
        SliderRow(
            label = stringResource(R.string.feature_settings_reading_font_size),
            value = settings.fontScale,
            range = ReaderSettings.FONT_SCALE_RANGE,
            onChange = { scale -> onChange { it.copy(fontScale = scale) } }
        )

        SettingsGroup {
            item { shapes ->
                SwitchItem(
                    shapes = shapes,
                    label = stringResource(R.string.feature_settings_reading_tap_to_turn),
                    checked = settings.tapToTurnPages,
                    onCheckedChange = { enabled -> onChange { it.copy(tapToTurnPages = enabled) } }
                )
            }
            item { shapes ->
                SwitchItem(
                    shapes = shapes,
                    label = stringResource(R.string.feature_settings_reading_volume_keys),
                    checked = settings.volumeKeysTurnPages,
                    onCheckedChange = { enabled -> onChange { it.copy(volumeKeysTurnPages = enabled) } }
                )
            }
            item { shapes ->
                SwitchItem(
                    shapes = shapes,
                    label = stringResource(R.string.feature_settings_reading_keep_screen_on),
                    checked = settings.keepScreenOn,
                    onCheckedChange = { enabled -> onChange { it.copy(keepScreenOn = enabled) } }
                )
            }
            item { shapes ->
                SwitchItem(
                    shapes = shapes,
                    label = stringResource(R.string.feature_settings_reading_palm_rejection),
                    help = stringResource(R.string.feature_settings_reading_palm_rejection_help),
                    checked = settings.palmRejection,
                    onCheckedChange = { enabled -> onChange { it.copy(palmRejection = enabled) } }
                )
            }
        }
        ChoiceRow(
            label = stringResource(R.string.feature_settings_reading_pen_mode),
            options = PenMode.entries,
            selected = settings.penMode,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = { mode -> onChange { it.copy(penMode = mode) } },
            help = stringResource(R.string.feature_settings_reading_pen_mode_help)
        )
    }
}

private fun PenMode.labelRes(): Int = when (this) {
    PenMode.Off -> R.string.feature_settings_reading_pen_off
    PenMode.Highlight -> R.string.feature_settings_reading_pen_highlight
    PenMode.Ink -> R.string.feature_settings_reading_pen_ink
}

/**
 * The typographic fine tuning.
 *
 * Set once to taste and then left alone, so it sits out of the way of the settings that get
 * changed from one book to the next.
 */
@Composable
internal fun ReadingLayoutSection(
    settings: ReaderSettings,
    onChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSection(
        title = stringResource(R.string.feature_settings_section_layout),
        icon = ReadanaIcons.Convert,
        modifier = modifier
    ) {
        ChoiceRow(
            label = stringResource(R.string.feature_settings_reading_columns),
            options = ReaderColumns.entries,
            selected = settings.columns,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = { columns -> onChange { it.copy(columns = columns) } }
        )
        SliderRow(
            label = stringResource(R.string.feature_settings_reading_line_height),
            value = settings.lineHeight,
            range = ReaderSettings.LINE_HEIGHT_RANGE,
            onChange = { height -> onChange { it.copy(lineHeight = height) } }
        )
        SliderRow(
            label = stringResource(R.string.feature_settings_reading_paragraph_spacing),
            value = settings.paragraphSpacing,
            range = ReaderSettings.PARAGRAPH_SPACING_RANGE,
            onChange = { spacing -> onChange { it.copy(paragraphSpacing = spacing) } }
        )
        SliderRow(
            label = stringResource(R.string.feature_settings_reading_margins),
            value = settings.pageMargins,
            range = ReaderSettings.PAGE_MARGIN_RANGE,
            onChange = { margins -> onChange { it.copy(pageMargins = margins) } }
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

private fun ReaderTheme.labelRes(): Int = when (this) {
    ReaderTheme.System -> R.string.feature_settings_reading_theme_system
    ReaderTheme.Light -> R.string.feature_settings_reading_theme_light
    ReaderTheme.Sepia -> R.string.feature_settings_reading_theme_sepia
    ReaderTheme.Dark -> R.string.feature_settings_reading_theme_dark
    ReaderTheme.Black -> R.string.feature_settings_reading_theme_black
}

private fun ReaderFont.labelRes(): Int = when (this) {
    ReaderFont.Publisher -> R.string.feature_settings_reading_font_publisher
    ReaderFont.Serif -> R.string.feature_settings_reading_font_serif
    ReaderFont.SansSerif -> R.string.feature_settings_reading_font_sans
    ReaderFont.Monospace -> R.string.feature_settings_reading_font_mono
}

private fun PageTurnMode.labelRes(): Int = when (this) {
    PageTurnMode.Paginated -> R.string.feature_settings_reading_page_turn_paginated
    PageTurnMode.Scrolled -> R.string.feature_settings_reading_page_turn_scrolled
}

private fun ReaderColumns.labelRes(): Int = when (this) {
    ReaderColumns.Auto -> R.string.feature_settings_reading_columns_auto
    ReaderColumns.One -> R.string.feature_settings_reading_columns_one
    ReaderColumns.Two -> R.string.feature_settings_reading_columns_two
}
