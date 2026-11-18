package com.zeyneb.sudoku.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.zeyneb.sudoku.R

/** Palette validée sur la maquette : blanc pur, encre quasi noire, accent bleu, rouge réservé aux erreurs. */
object Palette {
    val paper = Color(0xFFFFFFFF)
    val paper2 = Color(0xFFF0F2F5)
    val ink = Color(0xFF16181C)
    val inkSoft = Color(0xFF565B63)
    val line = Color(0xFFBFC4CC)
    val cage = Color(0xFF666666)        // pointillés : 60 % de noir
    val accent = Color(0xFF2A55B8)
    val accentWash = Color(0xFFD6E3FB)
    val error = Color(0xFFC62F2F)
    val halo = Color(0xB3EBA028)
}

object Fonts {
    val ui = FontFamily(
        Font(R.font.figtree_regular, FontWeight.Normal),
        Font(R.font.figtree_medium, FontWeight.Medium),
    )
    /** Chiffres de réponse. */
    val digits = FontFamily(
        Font(R.font.nunito_sans_semibold, FontWeight.SemiBold),
        Font(R.font.nunito_sans_extrabold, FontWeight.ExtraBold),
    )
    /** Sommes des cages. */
    val sums = FontFamily(Font(R.font.roboto_semibold, FontWeight.SemiBold))
    val display = FontFamily(Font(R.font.fraunces_light_italic, FontWeight.Light, FontStyle.Italic))
}
