package com.zeyneb.sudoku.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.zeyneb.sudoku.Game
import com.zeyneb.sudoku.engine.Puzzle
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * La grille entière (quadrillage, pointillés des cages, sommes, chiffres, notes) est dessinée en une seule passe,
 * en pixels entiers : tous les éléments partagent les mêmes coordonnées, comme sur la maquette validée.
 */
private class Geo(val w: Int, val lo: IntArray, val hi: IntArray, val cell: Float,
                  val wc: Int, val gap: Int, val dash: Int, val dgap: Int)

private fun geo(widthPx: Float, density: Float): Geo {
    val w = floor(widthPx).toInt()
    val thin = max(1, density.roundToInt())
    val thick = max(2, (2 * density).roundToInt())
    val outer = max(3, (2.6f * density).roundToInt())
    val lo = IntArray(10); val hi = IntArray(10)
    for (k in 0..9) {
        val lw = if (k == 0 || k == 9) outer else if (k % 3 == 0) thick else thin
        var p = (k * w / 9f).roundToInt() - lw / 2
        p = p.coerceIn(0, w - lw)
        lo[k] = p; hi[k] = p + lw
    }
    val cell = w / 9f
    return Geo(w, lo, hi, cell,
        wc = max(1, (1.1f * density).roundToInt()),
        gap = max(2, (cell * 0.07f).roundToInt()),
        dash = max(4, (cell * 0.085f).roundToInt()),
        dgap = max(3, (cell * 0.06f).roundToInt()))
}

private class Side(val dir: Char, val x1: Int, val y1: Int, var x2: Int, var y2: Int) {
    var t0 = 0; var t1 = 0; var s0 = 0; var s1 = 0
}

/** Contours d'une cage, parcourus avec l'intérieur à droite, regroupés en côtés droits. */
private fun cageLoops(cage: IntArray, g: Geo): List<List<Side>> {
    val inCage = cage.toHashSet()
    fun has(r: Int, c: Int) = r in 0..8 && c in 0..8 && (r * 9 + c) in inCage
    val out = HashMap<Int, MutableList<IntArray>>()
    fun add(x1: Int, y1: Int, x2: Int, y2: Int) { out.getOrPut(x1 * 16 + y1) { ArrayList() }.add(intArrayOf(x1, y1, x2, y2)) }
    for (i in cage) {
        val r = i / 9; val c = i % 9
        if (!has(r - 1, c)) add(c, r, c + 1, r)
        if (!has(r, c + 1)) add(c + 1, r, c + 1, r + 1)
        if (!has(r + 1, c)) add(c + 1, r + 1, c, r + 1)
        if (!has(r, c - 1)) add(c, r + 1, c, r)
    }
    val loops = ArrayList<List<Side>>()
    while (true) {
        val firstList = out.values.firstOrNull { it.isNotEmpty() } ?: break
        val loop = arrayListOf(firstList.removeAt(firstList.size - 1))
        while (true) {
            val last = loop.last()
            val l = out[last[2] * 16 + last[3]]
            if (l == null || l.isEmpty()) break
            var pick = 0
            if (l.size > 1) {
                val dx = last[2] - last[0]; val dy = last[3] - last[1]
                pick = l.indexOfFirst { (it[2] - it[0]) == -dy && (it[3] - it[1]) == dx }.coerceAtLeast(0)
            }
            loop.add(l.removeAt(pick))
        }
        val sides = ArrayList<Side>()
        for (e in loop) {
            val dir = when { e[2] > e[0] -> 'R'; e[2] < e[0] -> 'L'; e[3] > e[1] -> 'D'; else -> 'U' }
            val s = sides.lastOrNull()
            if (s != null && s.dir == dir) { s.x2 = e[2]; s.y2 = e[3] } else sides.add(Side(dir, e[0], e[1], e[2], e[3]))
        }
        if (sides.size > 1 && sides.first().dir == sides.last().dir) {
            val f = sides.removeAt(0); sides.last().x2 = f.x2; sides.last().y2 = f.y2
        }
        for (s in sides) when (s.dir) {
            'R' -> { s.t0 = g.hi[s.y1] + g.gap; s.t1 = s.t0 + g.wc }
            'L' -> { s.t1 = g.lo[s.y1] - g.gap; s.t0 = s.t1 - g.wc }
            'D' -> { s.t1 = g.lo[s.x1] - g.gap; s.t0 = s.t1 - g.wc }
            'U' -> { s.t0 = g.hi[s.x1] + g.gap; s.t1 = s.t0 + g.wc }
        }
        for (k in sides.indices) {
            val p = sides[(k - 1 + sides.size) % sides.size]; val n = sides[(k + 1) % sides.size]; val s = sides[k]
            s.s0 = min(p.t0, n.t0); s.s1 = max(p.t1, n.t1)
        }
        loops.add(sides)
    }
    return loops
}

/** Tirets réguliers, avec un vide à chaque extrémité : les coins restent ouverts. */
private fun DrawScope.dashes(horiz: Boolean, t0: Int, t1: Int, a: Int, b: Int, g: Geo, color: Color) {
    val len = b - a
    if (len <= 0) return
    val n = max(1, (len - g.dgap) / (g.dash + g.dgap))
    val d = if (n == 1) max(1, min(g.dash, len - 2 * g.dgap)) else g.dash
    val gp = (len - n * d) / (n + 1f)
    for (i in 0 until n) {
        val s = a + (gp + i * (d + gp)).roundToInt()
        if (horiz) drawRect(color, Offset(s.toFloat(), t0.toFloat()), Size(d.toFloat(), (t1 - t0).toFloat()))
        else drawRect(color, Offset(t0.toFloat(), s.toFloat()), Size((t1 - t0).toFloat(), d.toFloat()))
    }
}

@Composable
fun Board(game: Game, version: Int, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(game) {
                detectTapGestures { pos ->
                    val g = geo(size.width.toFloat(), density)
                    fun idx(v: Float): Int { for (k in 0 until 9) if (v < g.lo[k + 1]) return k; return 8 }
                    onSelect(idx(pos.y) * 9 + idx(pos.x))
                }
            }
    ) {
        if (version < 0) return@Canvas // la lecture de version redessine la grille à chaque changement
        val g = geo(size.width, density)
        drawBoard(game, selected, g, tm)
    }
}

private fun DrawScope.drawBoard(game: Game, selected: Int, g: Geo, tm: TextMeasurer) {
    val w = g.w.toFloat()
    drawRect(Palette.paper, Offset.Zero, Size(w, w))
    if (selected >= 0) {
        val r = selected / 9; val c = selected % 9
        drawRect(Palette.accentWash, Offset(g.hi[c].toFloat(), g.hi[r].toFloat()),
            Size((g.lo[c + 1] - g.hi[c]).toFloat(), (g.lo[r + 1] - g.hi[r]).toFloat()))
    }
    // quadrillage : lignes fines puis épaisses par-dessus
    for (heavy in listOf(false, true)) for (k in 0..9) {
        if ((k % 3 == 0) != heavy) continue
        val color = if (heavy) Palette.ink else Palette.line
        val lw = (g.hi[k] - g.lo[k]).toFloat()
        drawRect(color, Offset(g.lo[k].toFloat(), 0f), Size(lw, w))
        drawRect(color, Offset(0f, g.lo[k].toFloat()), Size(w, lw))
    }
    val p = game.puzzle
    if (p.mode == Puzzle.KILLER) {
        val fs = (g.cell * 0.255f)
        val sumStyle = TextStyle(fontFamily = Fonts.sums, fontWeight = FontWeight.SemiBold, fontSize = fs.toSp(), color = Palette.ink)
        p.cages.forEachIndexed { ci, cage ->
            val lab = cage.min()
            for (sides in cageLoops(cage, g)) {
                val top = sides.firstOrNull { it.dir == 'R' && it.x1 == lab % 9 && it.y1 == lab / 9 }
                if (top != null) {
                    val left = sides[(sides.indexOf(top) - 1 + sides.size) % sides.size]
                    val layout = tm.measure(p.sums[ci].toString(), sumStyle)
                    val x = left.t0; val y = top.t0
                    val pad = (fs * 0.22f).roundToInt()
                    // haut des chiffres aligné sur la piste des pointillés
                    val capTop = layout.firstBaseline - fs * 0.711f
                    drawText(layout, topLeft = Offset(x - fs * 0.02f - 1f, y - capTop - fs * 0.04f))
                    top.s0 = x + layout.size.width + pad
                    left.s0 = y + (fs * 0.86f).roundToInt() + pad
                }
                for (s in sides) dashes(s.dir == 'R' || s.dir == 'L', s.t0, s.t1, s.s0, s.s1, g, Palette.cage)
            }
        }
    }
    // chiffres et notes
    val digitSize = g.cell * 0.6f
    val noteStyle = TextStyle(fontFamily = Fonts.ui, fontWeight = FontWeight.Medium, fontSize = (g.cell * 0.17f).toSp(), color = Palette.inkSoft)
    for (i in 0 until 81) {
        val r = i / 9; val c = i % 9
        val x0 = g.hi[c].toFloat(); val y0 = g.hi[r].toFloat()
        val cw = (g.lo[c + 1] - g.hi[c]).toFloat(); val ch = (g.lo[r + 1] - g.hi[r]).toFloat()
        val v = game.values[i]
        if (v != 0) {
            val color = when { game.wrong[i] -> Palette.error; game.given[i] -> Palette.ink; else -> Palette.accent }
            val style = TextStyle(
                fontFamily = Fonts.digits,
                fontWeight = if (game.given[i]) FontWeight.ExtraBold else FontWeight.SemiBold,
                fontSize = digitSize.toSp(), color = color,
                shadow = if (game.pending[i]) Shadow(Palette.halo, Offset.Zero, g.cell * 0.22f) else null,
            )
            centered(tm, v.toString(), style, x0 + cw / 2, y0 + ch * 0.52f, digitSize * 0.705f, twice = game.pending[i])
        } else if (game.notes[i] != 0) {
            for (d in 1..9) if (game.notes[i] shr d and 1 == 1) {
                val nx = x0 + cw * 0.27f + ((d - 1) % 3) * cw * 0.23f
                val ny = y0 + ch * 0.36f + ((d - 1) / 3) * ch * 0.2f
                centered(tm, d.toString(), noteStyle, nx, ny, g.cell * 0.17f * 0.7f)
            }
        }
    }
}

private fun DrawScope.centered(tm: TextMeasurer, text: String, style: TextStyle, cx: Float, cy: Float, capHeight: Float, twice: Boolean = false) {
    val layout = tm.measure(text, style)
    val top = cy + capHeight / 2 - layout.firstBaseline
    val o = Offset(cx - layout.size.width / 2f, top)
    drawText(layout, topLeft = o)
    if (twice) drawText(layout, topLeft = o)
}
