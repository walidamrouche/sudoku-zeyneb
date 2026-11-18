package com.zeyneb.sudoku.ui

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Icônes au trait reprises de la maquette (grille 24 × 24). */
object Icons {
    private fun icon(name: String, width: Float, vararg paths: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        for (d in paths) addPath(
            pathData = addPathNodes(d), fill = null, stroke = SolidColor(Palette.ink),
            strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

    val back = icon("back", 1.8f, "M15 5l-7 7 7 7")
    val pause = icon("pause", 1.8f, "M9 6v12M15 6v12")
    val undo = icon("undo", 1.6f, "M9 14L4 9l5-5", "M4 9h10a6 6 0 010 12h-3")
    val erase = icon("erase", 1.6f, "M20 20H9L4 15a1.5 1.5 0 010-2l8.5-8.5a1.5 1.5 0 012 0l5 5a1.5 1.5 0 010 2L12 19")
    val notes = icon("notes", 1.6f, "M4 20h4L19 9l-4-4L4 16v4z", "M13.5 6.5l4 4")
}
