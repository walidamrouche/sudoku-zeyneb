package com.zeyneb.sudoku

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeyneb.sudoku.engine.Generator
import com.zeyneb.sudoku.engine.Puzzle
import com.zeyneb.sudoku.engine.Solver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Random
import java.util.concurrent.Executors

enum class Screen { Loading, Home, Game }
enum class Outcome { None, Won, Lost }

/** Une partie : la grille, ce qu'elle a saisi, ses erreurs et son temps. */
class Game(val puzzle: Puzzle) {
    val values = IntArray(81) { puzzle.givens[it] }
    val given = BooleanArray(81) { puzzle.givens[it] != 0 }
    val notes = IntArray(81)               // bit d = note d
    val wrong = BooleanArray(81)
    val pending = BooleanArray(81)         // chiffre faux encore dans son délai de 5 s
    var errors = 0
    var seconds = 0
    var outcome = Outcome.None
    val owner = IntArray(81) { -1 }.also { o -> puzzle.cages.forEachIndexed { c, cg -> cg.forEach { o[it] = c } } }
    val mode get() = puzzle.mode
}

private class Snap(val i: Int, val v: Int, val n: Int, val w: Boolean, val p: Boolean)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        const val PENALTY_DELAY_MS = 5_000L
        const val MAX_ERRORS = 3
    }

    private val prefs = app.getSharedPreferences("sudoku", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "generator").apply { priority = Thread.MIN_PRIORITY } }

    var screen by mutableStateOf(Screen.Loading); private set
    var game: Game? by mutableStateOf(null); private set
    /** Incrémenté à chaque changement de la grille, pour redessiner. */
    var version by mutableIntStateOf(0); private set
    var selected by mutableIntStateOf(-1); private set
    var notesMode by mutableStateOf(false); private set
    var paused by mutableStateOf(false); private set
    var preparing by mutableStateOf<Int?>(null); private set   // mode en attente de génération
    var savedSummary by mutableStateOf<String?>(null); private set

    private val history = ArrayList<List<Snap>>()
    private val penalties = HashMap<Int, Job>()
    private var foreground = true
    private val ready = arrayOfNulls<Puzzle>(2)
    private val generating = BooleanArray(2)

    init {
        for (m in 0..1) prefs.getString("ready_$m", null)?.let { ready[m] = runCatching { Puzzle.decode(it) }.getOrNull() }
        refreshSummary()
        for (m in 0..1) ensureReady(m)
        viewModelScope.launch {
            delay(2_200)
            if (screen == Screen.Loading) screen = Screen.Home
        }
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                val g = game ?: continue
                if (screen == Screen.Game && g.outcome == Outcome.None && !paused && foreground && preparing == null) {
                    g.seconds++
                    version++
                    if (g.seconds % 5 == 0) save()
                }
            }
        }
    }

    // ---------- génération en arrière-plan : une grille d'avance par mode ----------
    private fun ensureReady(mode: Int) {
        if (ready[mode] != null || generating[mode]) return
        generating[mode] = true
        worker.execute {
            val p = if (mode == Puzzle.KILLER) Generator.killer(Random()) else Generator.classic(Random())
            main.post {
                generating[mode] = false
                if (preparing == mode) {
                    preparing = null
                    start(p)
                    ensureReady(mode)
                } else {
                    ready[mode] = p
                    prefs.edit().putString("ready_$mode", p.encode()).apply()
                }
            }
        }
    }

    fun newGame(mode: Int) {
        val p = ready[mode]
        if (p != null) {
            ready[mode] = null
            prefs.edit().remove("ready_$mode").apply()
            start(p)
            ensureReady(mode)
        } else {
            clearPenalties()
            game = null
            preparing = mode
            screen = Screen.Game
            ensureReady(mode)
        }
    }

    private fun start(p: Puzzle) {
        clearPenalties()
        history.clear()
        game = Game(p)
        selected = -1
        notesMode = false
        paused = false
        screen = Screen.Game
        version++
        save()
    }

    fun retry() { game?.let { start(it.puzzle) } }

    fun resume() {
        val g = load() ?: return
        clearPenalties()
        history.clear()
        game = g
        selected = -1
        notesMode = false
        paused = false
        preparing = null
        screen = Screen.Game
        // un chiffre faux qui était dans son délai repart pour 5 s
        for (i in 0 until 81) if (g.pending[i]) schedulePenalty(g, i, g.values[i])
        version++
    }

    fun goHome() {
        save()
        clearPenalties(keepPending = true)
        preparing = null
        paused = false
        refreshSummary()
        screen = Screen.Home
    }

    // ---------- saisie ----------
    fun select(i: Int) {
        val g = game ?: return
        if (g.outcome != Outcome.None || paused) return
        selected = i
    }

    fun toggleNotes() { notesMode = !notesMode }

    private fun locked(g: Game, i: Int) = g.given[i] || (g.values[i] != 0 && g.values[i] == g.puzzle.solution[i])
    private fun snap(g: Game, i: Int) = Snap(i, g.values[i], g.notes[i], g.wrong[i], g.pending[i])

    fun input(d: Int) {
        val g = game ?: return
        val i = selected
        if (i < 0 || g.outcome != Outcome.None || paused || locked(g, i)) return
        if (notesMode) {
            if (g.values[i] != 0) return
            history.add(listOf(snap(g, i)))
            g.notes[i] = g.notes[i] xor (1 shl d)
        } else {
            if (g.values[i] == d) return
            val changes = arrayListOf(snap(g, i))
            cancelPenalty(g, i)
            g.values[i] = d; g.notes[i] = 0; g.wrong[i] = false
            if (d == g.puzzle.solution[i]) {
                for (j in peers(g, i)) if (g.notes[j] shr d and 1 == 1) {
                    changes.add(snap(g, j)); g.notes[j] = g.notes[j] and (1 shl d).inv()
                }
            } else {
                schedulePenalty(g, i, d)
            }
            history.add(changes)
        }
        version++
        save()
        checkWin(g)
    }

    fun erase() {
        val g = game ?: return
        val i = selected
        if (i < 0 || g.outcome != Outcome.None || paused || locked(g, i)) return
        if (g.values[i] == 0 && g.notes[i] == 0) return
        history.add(listOf(snap(g, i)))
        cancelPenalty(g, i)
        g.values[i] = 0; g.notes[i] = 0; g.wrong[i] = false
        version++
        save()
    }

    fun undo() {
        val g = game ?: return
        if (g.outcome != Outcome.None || paused || history.isEmpty()) return
        for (s in history.removeAt(history.size - 1)) {
            cancelPenalty(g, s.i)
            g.values[s.i] = s.v; g.notes[s.i] = s.n; g.wrong[s.i] = s.w
            if (s.p && s.v != 0) schedulePenalty(g, s.i, s.v)
        }
        version++
        save()
    }

    private fun peers(g: Game, i: Int): Set<Int> {
        val out = HashSet<Int>()
        for (j in 0 until 81) if (j != i && (Solver.ROW[j] == Solver.ROW[i] || Solver.COL[j] == Solver.COL[i] || Solver.BOX[j] == Solver.BOX[i])) out.add(j)
        val c = g.owner[i]
        if (c >= 0) for (j in g.puzzle.cages[c]) if (j != i) out.add(j)
        return out
    }

    // ---------- erreurs avec délai de 5 secondes ----------
    private fun schedulePenalty(g: Game, i: Int, d: Int) {
        g.pending[i] = true
        penalties[i] = viewModelScope.launch {
            delay(PENALTY_DELAY_MS)
            penalties.remove(i)
            if (game !== g || g.values[i] != d || !g.pending[i] || g.outcome != Outcome.None) return@launch
            g.pending[i] = false
            g.wrong[i] = true
            g.errors++
            version++
            save()
            if (g.errors >= MAX_ERRORS) endGame(g, Outcome.Lost)
        }
    }

    private fun cancelPenalty(g: Game, i: Int) {
        penalties.remove(i)?.cancel()
        g.pending[i] = false
    }

    private fun clearPenalties(keepPending: Boolean = false) {
        penalties.values.forEach { it.cancel() }
        penalties.clear()
        if (!keepPending) game?.pending?.fill(false)
    }

    // ---------- pause et fin ----------
    fun pause() { if (game?.outcome == Outcome.None && preparing == null) paused = true }
    fun unpause() { paused = false }

    fun onForeground(isForeground: Boolean) {
        foreground = isForeground
        if (!isForeground) {
            if (screen == Screen.Game) pause()
            save()
        }
    }

    private fun checkWin(g: Game) {
        if ((0 until 81).all { g.values[it] == g.puzzle.solution[it] }) endGame(g, Outcome.Won)
    }

    private fun endGame(g: Game, outcome: Outcome) {
        clearPenalties()
        g.outcome = outcome
        selected = -1
        paused = false
        prefs.edit().remove("game").apply()
        refreshSummary()
        version++
    }

    // ---------- sauvegarde ----------
    private fun save() {
        val g = game ?: return
        if (g.outcome != Outcome.None) return
        val o = JSONObject()
        o.put("puzzle", g.puzzle.encode())
        o.put("values", g.values.joinToString(""))
        o.put("notes", JSONArray(g.notes.toList()))
        o.put("wrong", g.wrong.joinToString("") { if (it) "1" else "0" })
        o.put("pending", g.pending.joinToString("") { if (it) "1" else "0" })
        o.put("errors", g.errors)
        o.put("seconds", g.seconds)
        prefs.edit().putString("game", o.toString()).apply()
        refreshSummary()
    }

    private fun load(): Game? = runCatching {
        val o = JSONObject(prefs.getString("game", null) ?: return null)
        val g = Game(Puzzle.decode(o.getString("puzzle")))
        val v = o.getString("values"); val w = o.getString("wrong"); val p = o.getString("pending")
        val n = o.getJSONArray("notes")
        for (i in 0 until 81) {
            g.values[i] = v[i] - '0'
            g.wrong[i] = w[i] == '1'
            g.pending[i] = p[i] == '1'
            g.notes[i] = n.getInt(i)
        }
        g.errors = o.getInt("errors")
        g.seconds = o.getInt("seconds")
        g
    }.getOrNull()

    private fun refreshSummary() {
        savedSummary = runCatching {
            val o = JSONObject(prefs.getString("game", null) ?: return@runCatching null)
            val mode = o.getString("puzzle").substringBefore(';').toInt()
            "${if (mode == Puzzle.KILLER) "Killer" else "Classic"} · ${formatTime(o.getInt("seconds"))}"
        }.getOrNull()
    }

    override fun onCleared() {
        save()
        worker.shutdownNow()
    }
}

fun formatTime(s: Int) = "%02d:%02d".format(s / 60, s % 60)
