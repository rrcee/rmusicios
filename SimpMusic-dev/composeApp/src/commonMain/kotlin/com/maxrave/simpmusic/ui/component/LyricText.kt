package com.maxrave.simpmusic.ui.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection

internal fun isRtlWord(text: String, fallbackRtl: Boolean): Boolean {
    for (char in text) {
        val code = char.code
        if (code in 0x0590..0x08FF || code in 0xFB1D..0xFEFC) return true
        if (code in 0x0041..0x005A || code in 0x0061..0x007A || code in 0x00C0..0x024F || code in 0x0400..0x052F || code in 0x4E00..0x9FFF || code in 0x3040..0x30FF || code in 0xAC00..0xD7AF) return false
    }
    return fallbackRtl
}

@Composable
internal fun rememberLyricLayoutDirection(text: String): LayoutDirection =
    remember(text) {
        if (isRtlWord(text, false)) LayoutDirection.Rtl else LayoutDirection.Ltr
    }

/**
 * A lyric and its subtitles share an edge, while each text keeps its own reading direction.
 * [alignmentText] is the original lyric when rendering a translation or romanization.
 */
@Composable
internal fun LyricText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    alignmentText: String = text,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
) {
    val alignmentDirection = rememberLyricLayoutDirection(alignmentText)
    val readingDirection = rememberLyricLayoutDirection(text)
    // Scope the marquee's direction to the text, without mirroring the surrounding player.
    CompositionLocalProvider(LocalLayoutDirection provides readingDirection) {
        Text(
            text = text,
            style = style.copy(textDirection = TextDirection.ContentOrLtr),
            modifier = modifier,
            textAlign = if (alignmentDirection == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left,
            color = color,
            maxLines = maxLines,
            softWrap = softWrap,
        )
    }
}
