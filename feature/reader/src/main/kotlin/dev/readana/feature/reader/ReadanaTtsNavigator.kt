package dev.readana.feature.reader

import org.readium.navigator.media.tts.TtsNavigator
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.navigator.media.tts.android.AndroidTtsSettings

/**
 * Readium's TTS navigator, with the Android engine's types filled in.
 *
 * The generic form carries five parameters that never vary in this app; naming it once keeps the
 * rest of the reader readable.
 */
internal typealias ReadanaTtsNavigator = TtsNavigator<
    AndroidTtsSettings,
    AndroidTtsPreferences,
    AndroidTtsEngine.Error,
    AndroidTtsEngine.Voice
    >
