package com.zeyneb.sudoku.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeyneb.sudoku.GameViewModel
import com.zeyneb.sudoku.Outcome
import com.zeyneb.sudoku.Screen
import com.zeyneb.sudoku.engine.Puzzle
import com.zeyneb.sudoku.formatTime

private fun ui(size: TextUnit, color: Color = Palette.ink, weight: FontWeight = FontWeight.Normal, align: TextAlign = TextAlign.Start) =
    TextStyle(fontFamily = Fonts.ui, fontSize = size, color = color, fontWeight = weight, textAlign = align)

private fun display(size: TextUnit, color: Color = Palette.ink) =
    TextStyle(fontFamily = Fonts.display, fontSize = size, color = color, textAlign = TextAlign.Center)

@Composable
fun App(vm: GameViewModel) {
    Box(Modifier.fillMaxSize().background(Palette.paper).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 440.dp).fillMaxSize().padding(horizontal = 16.dp)) {
            when (vm.screen) {
                Screen.Loading -> LoadingScreen()
                Screen.Home -> HomeScreen(vm)
                Screen.Game -> GameScreen(vm)
            }
        }
    }
}

// ---------------------------------------------------------------- chargement
@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Logo()
            Spacer(Modifier.height(22.dp))
            BasicText("Sudoku", style = display(34.sp))
        }
        BasicText(
            "Made for Zeyneb, with love",
            style = display(13.sp, Palette.inkSoft),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp),
        )
    }
}

@Composable
private fun Logo() {
    Canvas(Modifier.size(64.dp)) {
        val gap = 3.dp.toPx(); val cell = (size.width - 2 * gap) / 3
        for (r in 0..2) for (c in 0..2) {
            val o = Offset(c * (cell + gap), r * (cell + gap))
            if (r == 1 && c == 1) {
                drawRect(Palette.accentWash, o, Size(cell, cell))
                drawRect(Palette.accent, o, Size(cell, cell), style = Stroke(1.5.dp.toPx()))
            } else {
                drawRect(Palette.inkSoft, o, Size(cell, cell),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx()))))
            }
        }
    }
}

// ---------------------------------------------------------------- accueil
@Composable
private fun HomeScreen(vm: GameViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText("Sudoku", style = display(34.sp))
            Spacer(Modifier.height(6.dp))
            BasicText("Expert level", style = ui(13.sp, Palette.inkSoft))
        }
        Spacer(Modifier.height(36.dp))
        val saved = vm.savedSummary
        MenuButton("Continue", saved ?: "no game in progress", primary = true, enabled = saved != null) { vm.resume() }
        Spacer(Modifier.height(12.dp))
        MenuButton("New game", "Killer") { vm.newGame(Puzzle.KILLER) }
        Spacer(Modifier.height(12.dp))
        MenuButton("New game", "Classic") { vm.newGame(Puzzle.CLASSIC) }
    }
}

@Composable
private fun MenuButton(label: String, detail: String? = null, primary: Boolean = false, enabled: Boolean = true,
                       center: Boolean = false, onClick: () -> Unit) {
    val bg = if (primary) Palette.accent else Palette.paper2
    val fg = if (primary) Palette.paper else Palette.ink
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f).clip(RoundedCornerShape(14.dp)).background(bg)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = ui(16.sp, fg, FontWeight.Medium))
        if (detail != null) BasicText(detail, style = ui(13.sp, if (primary) Palette.accentWash else Palette.inkSoft))
    }
}

// ---------------------------------------------------------------- jeu
@Composable
private fun GameScreen(vm: GameViewModel) {
    BackHandler { vm.goHome() }
    val g = vm.game
    @Suppress("UNUSED_VARIABLE") val v = vm.version
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        // en-tête
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(Icons.back, "Back") { vm.goHome() }
            Row(Modifier.weight(1f)) {
                Stat("Mode", if (g == null) "…" else if (g.mode == Puzzle.KILLER) "Killer" else "Classic", Modifier.weight(1f))
                Stat("Mistakes", "${g?.errors ?: 0}/${GameViewModel.MAX_ERRORS}", Modifier.weight(1f), bad = (g?.errors ?: 0) > 0)
                Stat("Time", formatTime(g?.seconds ?: 0), Modifier.weight(1f))
            }
            IconButton(Icons.pause, "Pause") { vm.pause() }
        }
        Spacer(Modifier.weight(0.35f)) // espace au-dessus de la grille
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            if (g == null) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText("Preparing a new puzzle…", style = ui(15.sp, Palette.inkSoft))
                    Spacer(Modifier.height(6.dp))
                    BasicText("Every puzzle is checked to have a single solution.", style = ui(12.sp, Palette.inkSoft, align = TextAlign.Center))
                }
            } else {
                Board(g, vm.version, vm.selected, onSelect = { vm.select(it) })
                if (vm.paused) Sheet {
                    BasicText("Paused", style = display(28.sp))
                    BasicText("Time: ${formatTime(g.seconds)}", style = ui(15.sp, Palette.inkSoft))
                    Box(Modifier.widthIn(max = 260.dp)) { MenuButton("Resume", primary = true, center = true) { vm.unpause() } }
                }
                if (g.outcome != Outcome.None) Sheet {
                    val won = g.outcome == Outcome.Won
                    BasicText(if (won) "Well done 💌" else "Game over", style = display(28.sp))
                    BasicText(
                        if (won) "Solved in ${formatTime(g.seconds)}" else "3 mistakes. Retry this puzzle or start a new one.",
                        style = ui(15.sp, Palette.inkSoft, align = TextAlign.Center),
                    )
                    Column(Modifier.widthIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MenuButton("New puzzle", primary = true, center = true) { vm.newGame(g.mode) }
                        if (!won) MenuButton("Retry this puzzle", center = true) { vm.retry() }
                        MenuButton("Home", center = true) { vm.goHome() }
                    }
                }
            }
        }
        Spacer(Modifier.weight(0.35f)) // entre la grille et les outils
        // outils
        Row(Modifier.fillMaxWidth()) {
            Tool(Icons.undo, "Undo", Modifier.weight(1f)) { vm.undo() }
            Tool(Icons.erase, "Erase", Modifier.weight(1f)) { vm.erase() }
            Tool(Icons.notes, "Notes", Modifier.weight(1f), state = if (vm.notesMode) "on" else "off", on = vm.notesMode) { vm.toggleNotes() }
        }
        Spacer(Modifier.height(8.dp))
        // pavé numérique ; un chiffre placé 9 fois correctement disparaît
        Row(Modifier.fillMaxWidth()) {
            for (d in 1..9) {
                val done = g != null && (0 until 81).count { g.values[it] == d && g.values[it] == g.puzzle.solution[it] } == 9
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).alpha(if (done) 0f else 1f)
                        .clickable(enabled = !done) { vm.input(d) }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        d.toString(),
                        style = TextStyle(
                            fontFamily = Fonts.digits, fontWeight = FontWeight.SemiBold,
                            fontSize = if (vm.notesMode) 26.sp else 32.sp,
                            color = if (vm.notesMode) Palette.inkSoft else Palette.accent,
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.weight(0.3f)) // sous le pavé numérique
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, bad: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(label.uppercase(), style = ui(11.sp, Palette.inkSoft).copy(letterSpacing = 0.6.sp))
        BasicText(value, style = ui(16.sp, if (bad) Palette.error else Palette.ink, FontWeight.Medium))
    }
}

@Composable
private fun IconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Image(icon, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun Tool(icon: ImageVector, label: String, modifier: Modifier, state: String? = null, on: Boolean = false, onClick: () -> Unit) {
    val color = if (on) Palette.accent else Palette.inkSoft
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(icon, contentDescription = null, modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(if (on) Palette.accent else Palette.ink))
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(label, style = ui(12.5.sp, color))
            if (state != null) BasicText(" ${state.uppercase()}", style = ui(10.sp, color).copy(letterSpacing = 0.6.sp))
        }
    }
}

@Composable
private fun Sheet(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Palette.paper).clickable(enabled = true, indication = null,
        interactionSource = null) {}, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) { content() }
    }
}
