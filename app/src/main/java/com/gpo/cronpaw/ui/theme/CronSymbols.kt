package com.gpo.cronpaw.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.gpo.yoin.symbols.YoinSymbolStrokeWidth

/**
 * The few glyphs a calendar needs that Yoin Symbols does not have, drawn the same way:
 * 24-unit viewport, 1.5 round strokes.
 */
object CronSymbols {
    private val Ink = SolidColor(Color.Black)

    private fun symbol(name: String, strokes: List<String>, fills: List<String> = emptyList()): ImageVector {
        val b = ImageVector.Builder("CronSymbols.$name", 24.dp, 24.dp, 24f, 24f)
        for (d in strokes) b.addPath(
            pathData = addPathNodes(d), fill = null, stroke = Ink, strokeLineWidth = YoinSymbolStrokeWidth,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
        for (d in fills) b.addPath(pathData = addPathNodes(d), fill = Ink)
        return b.build()
    }

    val Mic: ImageVector by lazy {
        symbol("Mic", listOf(
            "M12 4 C13.38 4 14.5 5.12 14.5 6.5 L14.5 11.5 C14.5 12.88 13.38 14 12 14 C10.62 14 9.5 12.88 9.5 11.5 L9.5 6.5 C9.5 5.12 10.62 4 12 4 Z",
            "M6.5 11 C6.5 14.04 8.96 16.5 12 16.5 C15.04 16.5 17.5 14.04 17.5 11",
            "M12 16.5 L12 20",
        ))
    }

    val Stop: ImageVector by lazy {
        symbol("Stop", emptyList(), listOf("M8.5 7 L15.5 7 C16.33 7 17 7.67 17 8.5 L17 15.5 C17 16.33 16.33 17 15.5 17 L8.5 17 C7.67 17 7 16.33 7 15.5 L7 8.5 C7 7.67 7.67 7 8.5 7 Z"))
    }

    val Pin: ImageVector by lazy {
        symbol("Pin", listOf(
            "M9 4.5 L15 4.5 M10 4.5 L10 10 L7 13.5 L17 13.5 L14 10 L14 4.5",
            "M12 13.5 L12 19.5",
        ))
    }

    val PinFilled: ImageVector by lazy {
        symbol(
            "PinFilled",
            listOf("M9 4.5 L15 4.5", "M12 13.5 L12 19.5"),
            listOf("M10 4.5 L14 4.5 L14 10 L17 13.5 L7 13.5 L10 10 Z"),
        )
    }

    val Calendar: ImageVector by lazy {
        symbol("Calendar", listOf(
            "M6.5 5.5 L17.5 5.5 C18.6 5.5 19.5 6.4 19.5 7.5 L19.5 17.5 C19.5 18.6 18.6 19.5 17.5 19.5 L6.5 19.5 C5.4 19.5 4.5 18.6 4.5 17.5 L4.5 7.5 C4.5 6.4 5.4 5.5 6.5 5.5 Z",
            "M4.5 9.5 L19.5 9.5", "M8.5 3.5 L8.5 6.5", "M15.5 3.5 L15.5 6.5",
        ))
    }

    val Menu: ImageVector by lazy {
        symbol("Menu", listOf("M5 7.5 L19 7.5", "M5 12 L19 12", "M5 16.5 L13 16.5"))
    }

    val Keyboard: ImageVector by lazy {
        symbol("Keyboard", listOf(
            "M5.5 6.5 L18.5 6.5 C19.6 6.5 20.5 7.4 20.5 8.5 L20.5 15.5 C20.5 16.6 19.6 17.5 18.5 17.5 L5.5 17.5 C4.4 17.5 3.5 16.6 3.5 15.5 L3.5 8.5 C3.5 7.4 4.4 6.5 5.5 6.5 Z",
            "M7 10 L7.01 10", "M10 10 L10.01 10", "M13 10 L13.01 10", "M16 10 L16.01 10",
            "M8.5 14 L15.5 14",
        ))
    }

    val Database: ImageVector by lazy {
        symbol("Database", listOf(
            "M5 6.5 C5 5.12 8.13 4 12 4 C15.87 4 19 5.12 19 6.5 C19 7.88 15.87 9 12 9 C8.13 9 5 7.88 5 6.5 Z",
            "M5 6.5 L5 17.5 C5 18.88 8.13 20 12 20 C15.87 20 19 18.88 19 17.5 L19 6.5",
            "M5 12 C5 13.38 8.13 14.5 12 14.5 C15.87 14.5 19 13.38 19 12",
        ))
    }

    val Bell: ImageVector by lazy {
        symbol("Bell", listOf(
            "M6.5 15.75 L6.5 11 C6.5 7.96 8.96 5.5 12 5.5 C15.04 5.5 17.5 7.96 17.5 11 L17.5 15.75 L19 17.5 L5 17.5 Z",
            "M10 20 C10.45 20.6 11.15 21 12 21 C12.85 21 13.55 20.6 14 20",
            "M12 3.5 L12 5.5",
        ))
    }

    val Place: ImageVector by lazy {
        symbol("Place", listOf(
            "M12 20.5 C12 20.5 18.5 14.8 18.5 10 C18.5 6.41 15.59 3.5 12 3.5 C8.41 3.5 5.5 6.41 5.5 10 C5.5 14.8 12 20.5 12 20.5 Z",
            "M12 7.75 C13.24 7.75 14.25 8.76 14.25 10 C14.25 11.24 13.24 12.25 12 12.25 C10.76 12.25 9.75 11.24 9.75 10 C9.75 8.76 10.76 7.75 12 7.75 Z",
        ))
    }
}
