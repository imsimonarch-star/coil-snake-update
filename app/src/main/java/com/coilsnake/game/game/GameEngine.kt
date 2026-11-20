package com.coilsnake.game.game

import com.coilsnake.game.audio.SoundEngine
import com.coilsnake.game.data.GamePrefs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

class CameraState {
    val pitch = 0.92f
    val dist = 30f
    var focal = 1f
    var cx = 0f
    var cy = 0f
    var px = 0f
    var py = 0f
    var pz = 0f
    var fy = 0f
    var fz = 0f
    var uy = 0f
    var uz = 0f
    var sinP = 0.8f
    var cosP = 0.6f

    fun pose(lx: Float, lz: Float) {
        val sp = sin(pitch)
        val cp = cos(pitch)
        sinP = sp
        cosP = cp
        px = lx
        py = dist * sp
        pz = lz + dist * cp
        fy = -sp
        fz = -cp
        uy = cp
        uz = -sp
    }
}

class ProjPt(var x: Float = 0f, var y: Float = 0f, var s: Float = 1f, var d: Float = 1f)

class GameEngine(
    private val prefs: GamePrefs,
    private val sound: SoundEngine,
    private val onNetSend: (String) -> Unit,
    private val onToast: (String) -> Unit,
    private val onGoToMenuAfterDisconnect: (keepNet: Boolean) -> Unit
) {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var state: GameState? = null
    var deco: BoardDeco? = null

    var screen: ScreenMode = ScreenMode.TITLE
    var titleView: TitleSubView = TitleSubView.MODE
    var modeSel: Int = 0
    var playMode: PlayMode = PlayMode.SOLO
    var meIndex: Int = prefs.meIndex
    var skinIndices = intArrayOf(prefs.skinP1.coerceIn(SKIN_LIST.indices), prefs.skinP2.coerceIn(SKIN_LIST.indices)).also {
        if (it[0] == it[1]) it[1] = (it[0] + 1) % SKIN_LIST.size
    }

    var bestScore: Int = prefs.bestScore
    var wins = intArrayOf(prefs.winsP1, prefs.winsP2)

    var demoTheme = 0
    var clock = 0f
    var vw = 1920f
    var vh = 1080f
    var density = 3f
    var insetLeftPx = 0f
    var insetTopPx = 0f
    var insetRightPx = 0f
    var insetBottomPx = 0f

    /* Theme crossfade */
    var themeFrom = 0
    var themeTo = 0
    var themeT = 1f
    val blendedTheme = BlendedTheme()

    /* Camera & FX */
    val cam = CameraState()
    var fitFocal = 0f
    var fitCx = 0f
    var fitCy = 0f
    var fitTargetFocal = 1f
    var fitTargetCx = 0f
    var fitTargetCy = 0f
    var lookX = 0f
    var lookZ = 0f
    var shakeAmt = 0f
    var shakeX = 0f
    var shakeY = 0f
    var zoomKick = 0f
    var flash = 0f
    var flashColorHex = "#ffffff"

    /* Visual FX lists */
    val particles = mutableListOf<Particle>()
    val rings = mutableListOf<RingFx>()
    val glows = mutableListOf<GlowFx>()
    val floatTexts = mutableListOf<FloatTextFx>()
    val flyItems = mutableListOf<FlyFx>()
    val bulges = mutableListOf<BulgeFx>()
    val heads = arrayOf(HeadPos(), HeadPos())
    val bodyScreen = arrayOf(mutableListOf<Float>(), mutableListOf<Float>())
    private var blazeAcc = floatArrayOf(0f, 0f)
    private var frostAcc = 0f

    /* Overlays */
    var bannerTitle = ""
    var bannerSub = ""
    var bannerBlurb = ""
    var bannerTimer = 0f

    var countText = ""
    var countTimer = 0f
    var countKey = 0

    var hintText = ""
    var hintTimer = 0f
    var hintKind = ""

    /* Net sync */
    var netRole = NetRole.NONE
    var netUp = false
    var netBusy = false
    var netMe = 0
    var netFresh = false
    var netTheme = 0
    private var netSnapAcc = 0f
    private var netFxDepth = 0
    private var netReplay = false
    private val netEvents = mutableListOf<NetFxEvent>()

    init {
        setTheme(0, instant = true)
        newGame(demo = true, solo = false)
    }

    fun playerColorHex(pid: Int): String = SKIN_LIST[skinIndices[pid]].bodyHex

    fun skinOf(p: PlayerState): SnakeSkin {
        if (p.power.ghost > 0f) return POWER_SKINS.getValue("ghost")
        if (p.power.blaze > 0f) return POWER_SKINS.getValue("blaze")
        val otherP = state?.players?.getOrNull(1 - p.id)
        if (otherP != null && otherP.power.frost > 0f) return POWER_SKINS.getValue("frozen")
        return SKIN_LIST[skinIndices[p.id]]
    }

    fun setViewport(
        widthPx: Float,
        heightPx: Float,
        dens: Float,
        safeLeft: Float = insetLeftPx,
        safeTop: Float = insetTopPx,
        safeRight: Float = insetRightPx,
        safeBottom: Float = insetBottomPx
    ) {
        if (widthPx <= 1f || heightPx <= 1f) return
        val oldPortrait = vh > vw * 1.05f
        val newPortrait = heightPx > widthPx * 1.05f
        vw = widthPx
        vh = heightPx
        density = dens
        insetLeftPx = safeLeft
        insetTopPx = safeTop
        insetRightPx = safeRight
        insetBottomPx = safeBottom
        if (oldPortrait != newPortrait && screen == ScreenMode.TITLE) {
            newGame(demo = true, solo = false)
        }
        computeFit()
    }

    fun narrowLayout(): Boolean {
        val safeW = max(1f, vw - insetLeftPx - insetRightPx)
        return (safeW / density) < 500f || vh > vw
    }

    private fun boardDims(): Pair<Int, Int> = if (vh > vw * 1.05f) 11 to 18 else 17 to 13

    fun setTheme(i: Int, instant: Boolean) {
        val len = ISLAND_THEMES.size
        val idx = ((i % len) + len) % len
        if (instant) {
            themeFrom = idx
            themeTo = idx
            themeT = 1f
        } else {
            themeFrom = themeTo
            themeTo = idx
            themeT = 0f
        }
        computeBlendedTheme()
    }

    fun computeBlendedTheme() {
        val a = ISLAND_THEMES[themeFrom]
        val b = ISLAND_THEMES[themeTo]
        val k = smooth(themeT)
        fun mix3(srcA: FloatArray, srcB: FloatArray, dst: FloatArray) {
            dst[0] = srcA[0] + (srcB[0] - srcA[0]) * k
            dst[1] = srcA[1] + (srcB[1] - srcA[1]) * k
            dst[2] = srcA[2] + (srcB[2] - srcA[2]) * k
        }
        mix3(a.skyTop, b.skyTop, blendedTheme.skyTop)
        mix3(a.skyBot, b.skyBot, blendedTheme.skyBot)
        mix3(a.tileA, b.tileA, blendedTheme.tileA)
        mix3(a.tileB, b.tileB, blendedTheme.tileB)
        mix3(a.grout, b.grout, blendedTheme.grout)
        mix3(a.lip, b.lip, blendedTheme.lip)
        mix3(a.dirtTop, b.dirtTop, blendedTheme.dirtTop)
        mix3(a.dirtBot, b.dirtBot, blendedTheme.dirtBot)
        mix3(a.rock, b.rock, blendedTheme.rock)
        mix3(a.fog, b.fog, blendedTheme.fog)
        mix3(a.hole, b.hole, blendedTheme.hole)
        mix3(a.cloud, b.cloud, blendedTheme.cloud)
        for (i in 0..2) {
            blendedTheme.tileMid[i] = (blendedTheme.tileA[i] + blendedTheme.tileB[i]) * 0.5f
        }
        blendedTheme.shadow = lerp(a.shadow, b.shadow, k)
        blendedTheme.glow = lerp(a.glow, b.glow, k)
        blendedTheme.dark = lerp(a.dark, b.dark, k)
        blendedTheme.decal = if (k < 0.5f) a.decal else b.decal
        blendedTheme.glowHex = if (blendedTheme.glow > 0.5f) b.groutHex else a.groutHex
    }

    fun newGame(demo: Boolean, solo: Boolean) {
        val (w, h) = boardDims()
        val p1 = makePlayer(0)
        val p2 = makePlayer(1)
        val midZ = h / 2
        val off = if (h > 14) 3 else 2
        val cx = w / 2
        val hx0 = if (w > 12) cx - 1 else cx
        val hx1 = if (w > 12) cx + 1 else cx
        for (i in 0 until 5) {
            p1.snake += Segment(hx0 - i, midZ - off, 0f)
            p2.snake += Segment(hx1 + i, midZ + off, 0f)
        }
        p1.prev = p1.snake.map { it.copy() }.toMutableList()
        p2.prev = p2.snake.map { it.copy() }.toMutableList()

        val actualSolo = solo && !demo
        if (actualSolo) {
            p2.st = "dead"
            p2.snake.clear()
            p2.prev.clear()
            p2.diedAt = 0f
        }

        val seed = Random.nextInt(1_000_000_000)
        val g = GameState(
            demo = demo,
            solo = actualSolo,
            W = w,
            H = h,
            seed = seed,
            players = listOf(p1, p2),
            started = demo,
            tiles = IntArray(w * h) { GameConst.T_OK },
            tileT = FloatArray(w * h),
            timers = WorldTimers(
                orb = 9f,
                crystal = 14f,
                berry = if (demo) 6f else 16f,
                beetle = if (demo) 8f else 3f,
                crumble = 5f
            ),
            theme = if (demo) demoTheme else 0
        )
        state = g
        placeRocks(if (w > 12) 4 else 3, initial = true)
        repeat(GameConst.APPLE_COUNT) { spawnItem("apple") }
        setTheme(g.theme, instant = true)
        buildDeco(g)
        lookX = w / 2f
        lookZ = h / 2f
        resetFx()
        computeFit()
    }

    private fun makePlayer(id: Int) = PlayerState(
        id = id,
        name = if (id == 0) "P1" else "P2"
    )

    fun buildDeco(g: GameState) {
        val r = Mulberry32(g.seed)
        val w = g.W
        val h = g.H
        val n = w * h
        val variant = IntArray(n) {
            val rv = r.next()
            if (rv < 0.62f) 0 else if (rv < 0.82f) 1 else 2
        }
        val under = mutableListOf<FloatArray>()
        var maxDepth = 0f
        val cnt = (w * 2.2f).roundToInt()
        for (i in 1 until cnt) {
            val u = i.toFloat() / cnt
            val x = u * w
            val bell = sin(PI.toFloat() * u).coerceAtLeast(0f).pow(0.8f)
            var depth = 0.4f + bell * (5.2f + r.next() * 1.4f)
            if (i % 3 == 0) depth *= 1.08f + r.next() * 0.12f
            else if (i % 2 == 0) depth *= 0.72f + r.next() * 0.14f
            under += floatArrayOf(x, -GameConst.SLAB_T - depth, h - 0.1f - bell * 0.7f)
            maxDepth = max(maxDepth, depth)
        }
        val pebbles = List(w * 3) {
            DecoPebble(r.next() * w, r.next(), 0.03f + r.next() * 0.05f)
        }
        val decals = List((n / 6f).roundToInt()) {
            DecoDecal(
                x = (r.next() * w).toInt().coerceIn(0, w - 1),
                z = (r.next() * h).toInt().coerceIn(0, h - 1),
                u = 0.2f + r.next() * 0.6f,
                v = 0.2f + r.next() * 0.6f,
                rot = r.next() * GameConst.TAU,
                k = r.next()
            )
        }
        deco = BoardDeco(variant, under, maxDepth, pebbles, decals)
    }

    fun resetFx() {
        particles.clear()
        rings.clear()
        glows.clear()
        floatTexts.clear()
        flyItems.clear()
        bulges.clear()
        blazeAcc = floatArrayOf(0f, 0f)
        frostAcc = 0f
    }

    /* ---------- Camera Fit ---------- */
    private data class RectF(val x: Float, val y: Float, val w: Float, val h: Float)

    private fun fitRegion(): RectF {
        val safeLeft = insetLeftPx
        val safeTop = insetTopPx
        val safeW = max(120f * density, vw - insetLeftPx - insetRightPx)
        val safeH = max(120f * density, vh - insetTopPx - insetBottomPx)
        val safeWdp = safeW / density
        val safeHdp = safeH / density
        val narrow = narrowLayout()
        val isCompactH = safeHdp < 480f

        if (screen == ScreenMode.TITLE || screen == ScreenMode.OVER) {
            if (!narrow) {
                val panelFrac = if (safeWdp >= 860f) 0.41f else 0.44f
                val dockReserve = (if (isCompactH) 58f else 76f) * density
                val topMargin = (if (isCompactH) 10f else 22f) * density
                val boardLeft = safeLeft + safeW * panelFrac
                val boardW = max(140f * density, safeW * (1f - panelFrac) - 14f * density)
                val boardH = max(120f * density, safeH - topMargin - dockReserve)
                return RectF(boardLeft, safeTop + topMargin, boardW, boardH)
            }
            val topPad = safeTop + 28f * density
            val h = if (screen == ScreenMode.TITLE && titleView == TitleSubView.MODE) safeH * 0.37f
            else if (screen == ScreenMode.TITLE) safeH * 0.34f
            else safeH * 0.36f
            return RectF(safeLeft + 10f * density, topPad, safeW - 20f * density, max(120f * density, h))
        }

        if (!narrow && isCompactH) {
            // Landscape phone: HUD sits at top (~56dp), controls sit in bottom-left/right corners
            val top = safeTop + 56f * density
            val bottomPad = 14f * density
            val centerClearSide = if (safeWdp < 700f) 108f * density else 96f * density
            val side = max(12f * density, centerClearSide)
            val regionW = max(200f * density, safeW - side * 2f)
            val regionH = max(140f * density, safeH - (top - safeTop) - bottomPad)
            return RectF(safeLeft + side, top, regionW, regionH)
        }

        val top = safeTop + (if (narrow) 112f else 74f) * density
        val bottomPad = (if (narrow) 178f else 148f) * density
        val side = 14f * density
        val regionW = max(160f * density, safeW - side * 2f)
        val regionH = max(140f * density, safeH - (top - safeTop) - bottomPad)
        return RectF(safeLeft + side, top, regionW, regionH)
    }

    fun computeFit() {
        val g = state ?: return
        val r = fitRegion()
        cam.pose(g.W / 2f, g.H / 2f)
        cam.focal = 1f
        cam.cx = 0f
        cam.cy = 0f
        var x0 = Float.POSITIVE_INFINITY
        var x1 = Float.NEGATIVE_INFINITY
        var y0 = Float.POSITIVE_INFINITY
        var y1 = Float.NEGATIVE_INFINITY
        val pts = arrayOf(
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(g.W.toFloat(), 0f, 0f),
            floatArrayOf(0f, 0f, g.H.toFloat()),
            floatArrayOf(g.W.toFloat(), 0f, g.H.toFloat()),
            floatArrayOf(0f, -GameConst.SLAB_T, g.H.toFloat()),
            floatArrayOf(g.W.toFloat(), -GameConst.SLAB_T, g.H.toFloat()),
            floatArrayOf(0f, 1.3f, 0f),
            floatArrayOf(g.W.toFloat(), 1.3f, 0f)
        )
        val tmp = ProjPt()
        for (p in pts) {
            proj(p[0], p[1], p[2], tmp)
            x0 = min(x0, tmp.x)
            x1 = max(x1, tmp.x)
            y0 = min(y0, tmp.y)
            y1 = max(y1, tmp.y)
        }
        val f = min(r.w / max(1e-3f, x1 - x0), r.h / max(1e-3f, y1 - y0))
        fitTargetFocal = f
        fitTargetCx = r.x + r.w * 0.5f - f * (x0 + x1) * 0.5f
        fitTargetCy = r.y + r.h * 0.5f - f * (y0 + y1) * 0.5f
        if (fitFocal == 0f) {
            fitFocal = fitTargetFocal
            fitCx = fitTargetCx
            fitCy = fitTargetCy
        }
    }

    fun proj(x: Float, y: Float, z: Float, o: ProjPt) {
        val dy = y - cam.py
        val dz = z - cam.pz
        val d = dy * cam.fy + dz * cam.fz
        val s = cam.focal / d
        o.x = cam.cx + (x - cam.px) * s
        o.y = cam.cy - (dy * cam.uy + dz * cam.uz) * s
        o.s = s
        o.d = d
    }

    private fun updateCamera(dt: Float) {
        val g = state ?: return
        var tx = g.W / 2f
        var tz = g.H / 2f
        tx += sin(clock * 0.31f) * 0.22f
        tz += cos(clock * 0.23f) * 0.16f
        var hn = 0
        var hx = 0f
        var hz = 0f
        for (h in heads) {
            if (h.ok) {
                hn++
                hx += h.x
                hz += h.z
            }
        }
        if (hn > 0) {
            tx += (hx / hn - g.W / 2f) * 0.07f
            tz += (hz / hn - g.H / 2f) * 0.05f
        }
        val k = 1f - exp(-dt * 2.5f)
        lookX = lerp(lookX, tx, k)
        lookZ = lerp(lookZ, tz, k)
        shakeAmt *= exp(-dt * 9f)
        if (shakeAmt < 0.05f) shakeAmt = 0f
        shakeX = (Random.nextFloat() * 2f - 1f) * shakeAmt * density * 0.45f
        shakeY = (Random.nextFloat() * 2f - 1f) * shakeAmt * density * 0.45f
        zoomKick *= exp(-dt * 5f)
        flash = max(0f, flash - dt * 2.2f)
    }

    /* ---------- Board Queries ---------- */
    private fun inBounds(g: GameState, x: Int, z: Int) = x >= 0 && z >= 0 && x < g.W && z < g.H
    private fun liveSnakes(g: GameState) = g.players.filter { it.st == "run" }
    private fun other(g: GameState, p: PlayerState) = g.players[1 - p.id]

    private fun anySnakeAt(g: GameState, x: Int, z: Int): Boolean {
        for (p in g.players) {
            if (p.st == "dead") continue
            for (s in p.snake) if (s.x == x && s.z == z) return true
        }
        return false
    }

    private fun groundSnakeAt(g: GameState, x: Int, z: Int): Boolean {
        for (p in g.players) {
            if (p.st != "run") continue
            for (s in p.snake) if (s.x == x && s.z == z && s.h == 0f) return true
        }
        return false
    }

    private fun rockAt(g: GameState, x: Int, z: Int) = g.rocks.firstOrNull { it.x == x && it.z == z }
    private fun itemAt(g: GameState, x: Int, z: Int) = g.items.firstOrNull { it.x == x && it.z == z }
    private fun beetleAt(g: GameState, x: Int, z: Int) = g.beetles.firstOrNull { it.x == x && it.z == z }

    private fun buildOcc(g: GameState): IntArray {
        val occ = IntArray(g.W * g.H)
        for (p in g.players) {
            if (p.st == "dead") continue
            for (s in p.snake) occ[s.z * g.W + s.x] = 1
        }
        for (r in g.rocks) occ[r.z * g.W + r.x] = 2
        for (it in g.items) occ[it.z * g.W + it.x] = 3
        for (b in g.beetles) occ[b.z * g.W + b.x] = 4
        return occ
    }

    private fun outsideMap(g: GameState, p: PlayerState?): Pair<BooleanArray, BooleanArray> {
        val w = g.W
        val h = g.H
        val n = w * h
        val blocked = BooleanArray(n)
        val out = BooleanArray(n)
        val q = IntArray(n)
        if (p != null && p.st == "run") {
            for (s in p.snake) blocked[s.z * w + s.x] = true
        }
        var qh = 0
        var qt = 0
        fun seed(c: Int) {
            if (!blocked[c] && !out[c]) {
                out[c] = true
                q[qt++] = c
            }
        }
        for (x in 0 until w) {
            seed(x)
            seed((h - 1) * w + x)
        }
        for (z in 0 until h) {
            seed(z * w)
            seed(z * w + w - 1)
        }
        while (qh < qt) {
            val c = q[qh++]
            val x = c % w
            val z = c / w
            if (x > 0) seed(c - 1)
            if (x < w - 1) seed(c + 1)
            if (z > 0) seed(c - w)
            if (z < h - 1) seed(c + w)
        }
        return out to blocked
    }

    private fun openCells(g: GameState): BooleanArray {
        val n = g.W * g.H
        val all = BooleanArray(n) { true }
        for (p in liveSnakes(g)) {
            val out = outsideMap(g, p).first
            for (c in 0 until n) if (!out[c]) all[c] = false
        }
        return all
    }

    private fun inFrontOfHead(p: PlayerState, x: Int, z: Int, range: Int): Boolean {
        val h = p.snake.firstOrNull() ?: return false
        val dx = x - h.x
        val dz = z - h.z
        return if (p.dir.x != 0) dz == 0 && dx.sign == p.dir.x && abs(dx) <= range
        else dx == 0 && dz.sign == p.dir.z && abs(dz) <= range
    }

    private fun nearAnyHead(g: GameState, x: Int, z: Int, d: Int): Boolean {
        for (p in liveSnakes(g)) {
            val h = p.snake.firstOrNull() ?: continue
            if (abs(h.x - x) + abs(h.z - z) < d) return true
        }
        return false
    }

    private fun findFreeCell(g: GameState, minDist: Int, interior: Boolean = false, notAhead: Boolean = false): Int {
        val occ = buildOcc(g)
        val out = openCells(g)
        val w = g.W
        val h = g.H
        val live = liveSnakes(g)
        val cand = mutableListOf<Int>()
        for (z in 0 until h) {
            for (x in 0 until w) {
                val c = z * w + x
                if (occ[c] != 0 || g.tiles[c] != GameConst.T_OK || !out[c]) continue
                if (interior && (x == 0 || z == 0 || x == w - 1 || z == h - 1)) continue
                if (nearAnyHead(g, x, z, minDist)) continue
                if (notAhead && live.any { inFrontOfHead(it, x, z, 5) }) continue
                cand += c
            }
        }
        return if (cand.isEmpty()) -1 else cand[Random.nextInt(cand.size)]
    }

    private fun boardConnected(g: GameState): Boolean {
        val w = g.W
        val h = g.H
        val n = w * h
        val bl = BooleanArray(n)
        var free = 0
        for (r in g.rocks) bl[r.z * w + r.x] = true
        for (c in 0 until n) {
            if (g.tiles[c] != GameConst.T_OK) bl[c] = true
            if (!bl[c]) free++
        }
        val live = liveSnakes(g)
        if (live.isEmpty()) return true
        val head = live[0].snake.firstOrNull() ?: return true
        val start = head.z * w + head.x
        if (bl[start]) return false
        val q = ArrayDeque<Int>()
        q.add(start)
        bl[start] = true
        var seen = 0
        while (q.isNotEmpty()) {
            val c = q.removeLast()
            seen++
            val x = c % w
            val z = c / w
            if (x > 0 && !bl[c - 1]) { bl[c - 1] = true; q.add(c - 1) }
            if (x < w - 1 && !bl[c + 1]) { bl[c + 1] = true; q.add(c + 1) }
            if (z > 0 && !bl[c - w]) { bl[c - w] = true; q.add(c - w) }
            if (z < h - 1 && !bl[c + w]) { bl[c + w] = true; q.add(c + w) }
        }
        return seen == free
    }

    /* ---------- Spawning ---------- */
    private fun cellFreeForRock(g: GameState, x: Int, z: Int): Boolean {
        if (g.tiles[z * g.W + x] != GameConst.T_OK) return false
        for (p in g.players) {
            val h = p.snake.firstOrNull()
            if (h != null && (abs(h.x - x) + abs(h.z - z) < 4 || z == h.z)) return false
        }
        if (anySnakeAt(g, x, z) || rockAt(g, x, z) != null || itemAt(g, x, z) != null || beetleAt(g, x, z) != null) return false
        for (r in g.rocks) if (abs(r.x - x) <= 1 && abs(r.z - z) <= 1) return false
        return true
    }

    private fun placeRocks(n: Int, initial: Boolean) {
        val g = state ?: return
        for (k in 0 until n) {
            for (tries in 0 until 60) {
                val x = 1 + Random.nextInt(max(1, g.W - 2))
                val z = 1 + Random.nextInt(max(1, g.H - 2))
                if (!cellFreeForRock(g, x, z)) continue
                val rk = Rock(
                    id = g.nextId++,
                    x = x,
                    z = z,
                    seed = Random.nextInt(1_000_000_000),
                    st = if (initial) "rise" else "warn",
                    t = if (initial) -0.25f - k * 0.12f else 1.5f
                )
                g.rocks += rk
                if (!boardConnected(g)) {
                    g.rocks.removeAt(g.rocks.lastIndex)
                    continue
                }
                break
            }
        }
    }

    private fun spawnItem(type: String, sub: String = "", cell: Int? = null): GameItem? {
        val g = state ?: return null
        val c = cell ?: findFreeCell(g, if (type == "apple") 3 else 4)
        if (c < 0) return null
        val life = when (type) {
            "crystal" -> 8f
            "orb" -> 10f
            "berry" -> 12f
            else -> 0f
        }
        val it = GameItem(
            id = g.nextId++,
            type = type,
            sub = sub,
            x = c % g.W,
            z = c / g.W,
            age = 0f,
            life = life
        )
        g.items += it
        if (type != "apple" && type != "berry") {
            fxRing(it.x + 0.5f, it.z + 0.5f, 0.1f, 0.9f, 0.5f, itemColorHex(it), 0.9f, 0.06f)
        }
        return it
    }

    private fun ensureApples() {
        val g = state ?: return
        var n = g.items.count { it.type == "apple" }
        while (n < GameConst.APPLE_COUNT) {
            if (spawnItem("apple") == null) break
            n++
        }
    }

    private fun spawnBerries() {
        val g = state ?: return
        val c0 = findFreeCell(g, 4, interior = true)
        if (c0 < 0) return
        val cx = c0 % g.W
        val cz = c0 / g.W
        val occ = buildOcc(g)
        val out = openCells(g)
        val offs = arrayOf(
            0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1,
            1 to 1, -1 to -1, 1 to -1, -1 to 1
        )
        var placed = 0
        for ((ox, oz) in offs) {
            if (placed >= 5) break
            val x = cx + ox
            val z = cz + oz
            if (!inBounds(g, x, z)) continue
            val c = z * g.W + x
            if (occ[c] != 0 || g.tiles[c] != GameConst.T_OK || !out[c]) continue
            if (nearAnyHead(g, x, z, 2)) continue
            val it = spawnItem("berry", "", c)
            if (it != null) {
                it.age = -placed * 0.07f
                placed++
                occ[c] = 3
            }
        }
        if (placed > 0) fxRing(cx + 0.5f, cz + 0.5f, 0.2f, 1.7f, 0.6f, "#8c6eff", 0.85f, 0.07f)
    }

    private fun spawnBeetle() {
        val g = state ?: return
        val c = findFreeCell(g, 6)
        if (c < 0) return
        val x = c % g.W
        val z = c / g.W
        val dirs = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        val d = dirs[Random.nextInt(dirs.size)]
        g.beetles += Beetle(
            id = g.nextId++,
            x = x,
            z = z,
            fx = x,
            fz = z,
            hx = d.first,
            hz = d.second,
            iv = rand(0.36f, 0.44f),
            phase = Random.nextFloat() * 6f
        )
        fxBurst(x + 0.5f, 0.05f, z + 0.5f, 10, "chunk", 1.6f, 3f, 0.9f, 0.07f, "#7a5233", 12f)
        emitSfx("rumble")
    }

    private fun startCrumble() {
        val g = state ?: return
        val holes = g.tiles.count { it != GameConst.T_OK }
        if (holes >= min(2 + g.level, 10)) return
        val c = findFreeCell(g, 4, interior = true, notAhead = true)
        if (c < 0) return
        g.tiles[c] = GameConst.T_SHAKE
        g.tileT[c] = GameConst.SHAKE_TIME
    }

    /* ---------- Snakes & Controls ---------- */
    fun stepInterval(p: PlayerState): Float {
        val g = state ?: return 0.15f
        if (g.demo) return 0.13f
        var iv = max(0.082f, 0.15f - (g.level - 1) * 0.0075f)
        if (p.power.blaze > 0f) iv *= 0.82f
        if (other(g, p).power.frost > 0f) iv /= GameConst.FROST_SLOW
        return iv
    }

    fun snakeT(p: PlayerState): Float {
        val g = state ?: return 1f
        return if (g.phase == "run" && g.started && p.st == "run") {
            (p.stepAcc / stepInterval(p)).coerceIn(0f, 1f)
        } else 1f
    }

    fun steer(pi: Int, dx: Int, dz: Int) {
        val g = state ?: return
        if (screen != ScreenMode.PLAY || g.phase != "run") return
        if (netRole == NetRole.GUEST) {
            if (netUp) sendNetMsg(NetWireMsg(t = "i", k = 0, x = dx, z = dz))
            return
        }
        val p = g.players.getOrNull(pi) ?: return
        if (p.st != "run") return
        if (!g.started) {
            if (dx == -p.dir.x && dz == -p.dir.z) return
            p.dir = GridDir(dx, dz)
            return
        }
        val last = p.queue.lastOrNull() ?: p.dir
        if ((dx == last.x && dz == last.z) || (dx == -last.x && dz == -last.z)) return
        if (p.queue.size < 3) p.queue += GridDir(dx, dz)
    }

    fun tryHop(pi: Int) {
        val g = state ?: return
        if (screen != ScreenMode.PLAY || g.phase != "run" || !g.started) return
        if (netRole == NetRole.GUEST) {
            if (netUp) sendNetMsg(NetWireMsg(t = "i", k = 1))
            return
        }
        val p = g.players.getOrNull(pi) ?: return
        if (p.st != "run" || p.airSteps > 0) return
        val h = p.snake.firstOrNull() ?: return
        if (p.hops <= 0) {
            fxFloatText(h.x + 0.5f, 1.2f, h.z + 0.5f, "No hops", "#ff8a96", 17f, 0.8f)
            emitSfx("fizzle")
            return
        }
        p.hops--
        p.airSteps = GameConst.JUMP_STEPS
        emitSfx("hop")
        fxRing(h.x + 0.5f, h.z + 0.5f, 0.2f, 1.1f, 0.45f, "#ffffff", 0.8f, 0.06f)
        fxBurst(h.x + 0.5f, 0.08f, h.z + 0.5f, 6, "dust", 1.2f, 0.6f, 0.5f, 0.14f, rgbToHex(shadeRgb(blendedTheme.tileMid, 0.35f)), 0f)
    }

    private data class MovePlan(
        val p: PlayerState,
        val nx: Int,
        val nz: Int,
        val air: Boolean,
        val h: Float,
        var cause: String = "",
        var rock: Rock? = null,
        var bt: Beetle? = null
    )

    private fun stepPlayers(g: GameState, movers: List<PlayerState>) {
        g.tick++
        val plans = mutableListOf<MovePlan>()
        for (p in movers) {
            p.prev = p.snake.map { it.copy() }.toMutableList()
            if (g.demo) aiSteer(g, p)
            if (p.queue.isNotEmpty()) {
                val d = p.queue.removeAt(0)
                if (!(d.x == -p.dir.x && d.z == -p.dir.z)) p.dir = d
            }
            val head = p.snake[0]
            val air = p.airSteps > 0
            val hh = if (air) GameConst.JUMP_ARC[GameConst.JUMP_STEPS - p.airSteps] else 0f
            plans += MovePlan(p, head.x + p.dir.x, head.z + p.dir.z, air, hh)
        }

        /* 1. Decide who crashes */
        for (pl in plans) {
            val p = pl.p
            val sn = p.snake
            val nx = pl.nx
            val nz = pl.nz
            val air = pl.air
            if (!inBounds(g, nx, nz)) { pl.cause = "edge"; continue }
            val c = nz * g.W + nx
            if (!air && g.tiles[c] >= GameConst.T_FALL) { pl.cause = "hole"; continue }
            val rock = rockAt(g, nx, nz)
            if (rock != null && rock.st != "warn" && !air) {
                if (p.power.blaze > 0f) pl.rock = rock else { pl.cause = "rock"; continue }
            }
            val grow = p.grow > 0
            if (p.power.ghost <= 0f) {
                val lim = if (grow) sn.size else sn.size - 1
                for (i in 0 until lim) {
                    val s = sn[i]
                    if (s.x == nx && s.z == nz && (s.h > 0f) == air) {
                        pl.cause = "self"
                        break
                    }
                }
                if (pl.cause.isNotEmpty()) continue
            }
            val o = other(g, p)
            if (o.st == "run" && p.power.ghost <= 0f && o.power.ghost <= 0f && p.shield <= 0f && o.shield <= 0f) {
                val oMoves = plans.any { it.p === o }
                val lim = if (oMoves && o.grow <= 0) o.snake.size - 1 else o.snake.size
                for (i in 0 until lim) {
                    val s = o.snake[i]
                    if (s.x == nx && s.z == nz && (s.h > 0f) == air) {
                        pl.cause = "rival"
                        break
                    }
                }
                if (pl.cause.isNotEmpty()) continue
            }
            val bt = beetleAt(g, nx, nz)
            if (bt != null && !air) {
                if (p.power.blaze > 0f) pl.bt = bt else if (p.shield <= 0f) { pl.cause = "beetle"; continue }
            }
        }
        if (plans.size == 2) {
            val a = plans[0]
            val b = plans[1]
            if (a.nx == b.nx && a.nz == b.nz && a.air == b.air &&
                a.p.power.ghost <= 0f && b.p.power.ghost <= 0f &&
                a.p.shield <= 0f && b.p.shield <= 0f
            ) {
                a.cause = "head"
                b.cause = "head"
            }
        }

        /* 2. Move survivors */
        val order = if ((g.tick and 1) != 0) plans.asReversed() else plans
        for (pl in order) {
            val p = pl.p
            if (pl.cause.isNotEmpty()) continue
            if (pl.rock != null && g.rocks.contains(pl.rock)) smashRock(g, pl.rock!!, p)
            if (pl.bt != null && g.beetles.contains(pl.bt)) bustBeetle(g, pl.bt!!, p)
            val sn = p.snake
            val wasAir = sn[0].h > 0f
            val grow = p.grow > 0
            sn.add(0, Segment(pl.nx, pl.nz, pl.h))
            if (grow) p.grow-- else sn.removeAt(sn.lastIndex)
            if (p.airSteps > 0) p.airSteps--
            if (wasAir && !pl.air) {
                emitSfx("land")
                fxRing(pl.nx + 0.5f, pl.nz + 0.5f, 0.15f, 0.9f, 0.4f, "#ffffff", 0.6f, 0.05f)
                fxBurst(pl.nx + 0.5f, 0.06f, pl.nz + 0.5f, 7, "dust", 1.4f, 0.5f, 0.5f, 0.15f, rgbToHex(shadeRgb(blendedTheme.tileMid, 0.3f)), 0f)
            }
            if (sn.size > p.stats.maxLen) p.stats.maxLen = sn.size
            eatAt(g, p, pl.nx, pl.nz)
            lassoCheck(g, p)
            if (!g.demo && !g.tipLasso && sn.size >= 9 && p.stats.lassos == 0) {
                g.tipLasso = true
                fxShowHint("Long enough to lasso: circle around fruit and close the loop.", 6f)
            }
        }
        ensureApples()
        /* 3. Crashes */
        for (pl in plans) if (pl.cause.isNotEmpty()) die(g, pl.p, pl.cause, pl.nx, pl.nz)
        checkEnd(g)
    }

    private data class RespawnSpot(val seg: MutableList<Segment>, val dir: GridDir)

    private fun findRespawn(g: GameState, p: PlayerState): RespawnSpot? {
        val w = g.W
        val h = g.H
        val o = other(g, p)
        val len = 5
        val occ = BooleanArray(w * h)
        if (o.st == "run" || o.st == "dying") {
            for (s in o.snake) occ[s.z * w + s.x] = true
        }
        fun ok(x: Int, z: Int) = inBounds(g, x, z) && !occ[z * w + x] && g.tiles[z * w + x] == GameConst.T_OK && rockAt(g, x, z) == null && beetleAt(g, x, z) == null
        val dirs = arrayOf(GridDir(1, 0), GridDir(-1, 0), GridDir(0, 1), GridDir(0, -1))
        for (lead in intArrayOf(4, 2, 1)) {
            var bestX = 0
            var bestZ = 0
            var bestDir: GridDir? = null
            var bestScore = Float.NEGATIVE_INFINITY
            for (z in 0 until h) {
                for (x in 0 until w) {
                    for (d in dirs) {
                        var good = true
                        for (i in -lead until len) {
                            if (!ok(x - d.x * i, z - d.z * i)) {
                                good = false
                                break
                            }
                        }
                        if (!good) continue
                        var sc = Random.nextFloat() * 3f
                        if (o.st == "run" && o.snake.isNotEmpty()) {
                            val oh = o.snake[0]
                            sc += min(12, abs(oh.x - x) + abs(oh.z - z))
                        }
                        if (sc > bestScore) {
                            bestScore = sc
                            bestX = x
                            bestZ = z
                            bestDir = d
                        }
                    }
                }
            }
            if (bestDir != null) {
                val seg = MutableList(len) { i ->
                    Segment(bestX - bestDir.x * i, bestZ - bestDir.z * i, 0f)
                }
                return RespawnSpot(seg, bestDir)
            }
        }
        return null
    }

    private fun loseHeart(p: PlayerState, spot: RespawnSpot) {
        val h = p.snake[0]
        p.snake.forEachIndexed { idx, seg -> fxPop(p, seg, idx, false) }
        fxFloatText(h.x + 0.5f, 1.1f, h.z + 0.5f, "-1 ♥", "#ff4d6d", 26f, 1.3f)
        fxShake(10f)
        emitSfx("death")
        flash = 0.35f
        flashColorHex = "#ff4d6d"
        p.hearts--
        p.snake.clear()
        p.snake.addAll(spot.seg)
        p.prev = p.snake.map { it.copy() }.toMutableList()
        p.dir = spot.dir.copy()
        p.queue.clear()
        p.grow = 0
        p.airSteps = 0
        p.stepAcc = 0f
        p.hops = GameConst.HOP_MAX
        p.hopCharge = 0f
        p.combo = 0
        p.comboT = 0f
        p.enclosed = null
        p.pulse = 0f
        p.shield = GameConst.SHIELD_TIME
    }

    private fun die(g: GameState, p: PlayerState, cause: String, nx: Int, nz: Int) {
        if (!g.demo && p.hearts > 1) {
            val spot = findRespawn(g, p)
            if (spot != null) {
                loseHeart(p, spot)
                return
            }
        }
        if (!g.demo) p.hearts = 0
        val h = p.snake[0]
        p.st = "dying"
        p.deathT = 0f
        p.popped = 0
        p.cause = cause
        p.diedAt = g.time
        p.bonk = BonkVec(nx - h.x, nz - h.z)
        p.prev = p.snake.map { it.copy() }.toMutableList()
        p.queue.clear()
        p.enclosed = null
        fxShake(12f)
        emitSfx("death")
    }

    private fun updateDying(dt: Float) {
        val g = state ?: return
        for (p in g.players) {
            if (p.st != "dying") continue
            p.deathT += dt
            val start = 0.45f
            val gap = 0.04f
            val want = floor((p.deathT - start) / gap + 1f).toInt().coerceIn(0, p.snake.size)
            while (p.popped < want) {
                fxPop(p, p.snake[p.popped], p.popped, false)
                p.popped++
            }
            if (p.deathT > start + p.snake.size * gap + 0.75f) {
                p.st = "dead"
                p.snake.clear()
                p.prev.clear()
            }
        }
        if (g.demo) {
            if (g.players.any { it.st == "dead" }) {
                demoTheme = (demoTheme + 1) % ISLAND_THEMES.size
                newGame(demo = true, solo = false)
                flash = 0.5f
                flashColorHex = "#fff5e3"
            }
            return
        }
        if (g.phase == "run" && g.players.all { it.st == "dead" }) {
            if (g.solo) {
                finishMatch(-1)
                return
            }
            val a = g.players[0]
            val b = g.players[1]
            val w = when {
                a.score != b.score -> if (a.score > b.score) 0 else 1
                a.diedAt != b.diedAt -> if (a.diedAt > b.diedAt) 0 else 1
                else -> -1
            }
            finishMatch(w)
        }
    }

    private fun checkEnd(g: GameState) {
        if (g.demo || g.solo || g.phase != "run") return
        val live = liveSnakes(g)
        val out = g.players.filter { it.st != "run" }
        if (live.size == 1 && out.size == 1 && !g.outHint) {
            val w = live[0]
            val l = out[0]
            g.outHint = true
            val msg = if (w.score <= l.score) {
                "${l.name} is out. ${w.name} keeps going! Needs ${fmtInt(l.score - w.score + 1)} more pts to win."
            } else {
                "${l.name} is out. ${w.name} keeps going! Keep eating to stay ahead."
            }
            fxShowHint(msg, 6f)
        }
    }

    private fun finishMatch(winner: Int) {
        val g = state ?: return
        if (g.phase != "run") return
        if (g.solo) {
            g.phase = "ending"
            g.winner = -1
            g.endT = 0.6f
            for (p in g.players) {
                if (p.score > bestScore) {
                    bestScore = p.score
                    prefs.bestScore = bestScore
                }
            }
            emitSfx("draw")
            fxShowBanner("Game over", "Score ${fmtInt(g.players[0].score)}", "")
            return
        }
        g.phase = "ending"
        g.winner = winner
        g.endT = if (winner >= 0 && g.players[winner].st == "run") 1.8f else 0.6f
        if (winner >= 0) {
            wins[winner]++
            if (winner == 0) prefs.winsP1 = wins[0] else prefs.winsP2 = wins[1]
        }
        for (p in g.players) {
            if (p.score > bestScore) {
                bestScore = p.score
                prefs.bestScore = bestScore
            }
        }
        emitSfx(if (winner >= 0) "win" else "draw")
        val title = if (winner >= 0) "${g.players[winner].name} wins!" else "Draw!"
        fxShowBanner(title, "${fmtInt(g.players[0].score)}  :  ${fmtInt(g.players[1].score)}", "")
        if (winner >= 0 && g.players[winner].st == "run") {
            val h = g.players[winner].snake[0]
            fxBurst(h.x + 0.5f, 0.8f, h.z + 0.5f, 26, "spark", 3.2f, 4f, 1.1f, 0.22f, playerColorHex(winner), 3f)
            flash = 0.3f
            flashColorHex = "#fff5e3"
        }
    }

    /* ---------- Eating, Lasso, Scoring ---------- */
    fun itemColorHex(it: GameItem): String = when (it.type) {
        "apple" -> "#ff4a4a"
        "berry" -> "#7457ff"
        "crystal" -> "#7ff3ff"
        "orb" -> GameConst.ORB_COLORS[it.sub] ?: "#ffffff"
        else -> "#78f0ff"
    }

    fun itemRadius(it: GameItem): Float = when (it.type) {
        "apple" -> 0.27f
        "berry" -> 0.17f
        "orb" -> 0.27f
        else -> 0.26f
    }

    fun itemHeight(it: GameItem): Float = when (it.type) {
        "apple" -> 0.32f
        "berry" -> 0.2f
        "orb" -> 0.56f
        else -> 0.62f
    }

    private fun eatAt(g: GameState, p: PlayerState, x: Int, z: Int) {
        val got = mutableListOf<GameItem>()
        for (i in g.items.lastIndex downTo 0) {
            val it = g.items[i]
            if (it.x == x && it.z == z) {
                got += it
                g.items.removeAt(i)
            }
        }
        if (got.isNotEmpty()) collect(g, p, got, lasso = false)
    }

    private fun collect(g: GameState, p: PlayerState, list: List<GameItem>, lasso: Boolean) {
        p.combo = if (p.comboT > 0f) p.combo + 1 else 1
        p.comboT = GameConst.COMBO_WINDOW
        val mult = min(p.combo, GameConst.MAX_MULT)
        val lm = if (lasso) list.size else 1
        var total = 0
        var hasApple = false
        var hasBerry = false
        var hasCrystal = false
        for (it in list) {
            val pts = (GameConst.BASE_PTS[it.type] ?: 10) * mult * lm
            total += pts
            when (it.type) {
                "apple" -> { p.grow += 1; g.applesLv++; hasApple = true }
                "berry" -> { p.grow += 1; hasBerry = true }
                "crystal" -> hasCrystal = true
                "orb" -> {
                    when (it.sub) {
                        "blaze" -> p.power.blaze = GameConst.POWER_BLAZE_DUR
                        "frost" -> p.power.frost = GameConst.POWER_FROST_DUR
                        "ghost" -> p.power.ghost = GameConst.POWER_GHOST_DUR
                    }
                    emitSfx("power")
                    powerBanner(p, it.sub)
                }
                "beetle" -> p.stats.beetles++
            }
            fxCollect(p, it, pts, lasso)
        }
        p.score += total
        p.pulse = 1f
        if (lasso) {
            p.stats.lassos++
            p.stats.bestLasso = max(p.stats.bestLasso, list.size)
            emitSfx("lasso", list.size)
        } else {
            if (hasApple || list.any { it.type == "beetle" }) emitSfx("eat", p.combo + p.id * 2)
            else if (hasBerry) emitSfx("berry")
            if (hasCrystal) emitSfx("crystal")
            val h = heads[p.id]
            if (mult > 1 && h.ok) fxFloatText(h.x, h.y + 1.0f, h.z, "COMBO ×$mult", "#ffc23a", 15f, 0.9f)
        }
        if (hasApple) bulges += BulgeFx(0f, p.id)
        while (g.applesLv >= GameConst.APPLES_PER_LEVEL) levelUp(g)
        checkEnd(g)
    }

    private fun lassoCheck(g: GameState, p: PlayerState) {
        val w = g.W
        val n = w * g.H
        val (out, blocked) = outsideMap(g, p)
        val encPrev = p.enclosed ?: IntArray(n).also { p.enclosed = it }
        val fresh = mutableListOf<Int>()
        for (c in 0 until n) {
            val enc = if (!blocked[c] && !out[c]) 1 else 0
            if (enc == 1 && encPrev[c] == 0) fresh += c
            encPrev[c] = enc
        }
        if (fresh.isEmpty()) return
        val set = BooleanArray(n)
        for (c in fresh) set[c] = true
        val caught = mutableListOf<GameItem>()
        for (i in g.items.lastIndex downTo 0) {
            val it = g.items[i]
            if (set[it.z * w + it.x]) {
                caught += it
                g.items.removeAt(i)
            }
        }
        for (i in g.beetles.lastIndex downTo 0) {
            val b = g.beetles[i]
            if (set[b.z * w + b.x]) {
                caught += GameItem(id = b.id, type = "beetle", x = b.x, z = b.z)
                g.beetles.removeAt(i)
                beetlePoof(b)
            }
        }
        if (caught.isEmpty()) return
        fxLasso(p, fresh.toIntArray(), caught.size)
        collect(g, p, caught, lasso = true)
    }

    private fun levelUp(g: GameState) {
        g.applesLv -= GameConst.APPLES_PER_LEVEL
        if (g.demo) return
        g.level++
        g.theme = (g.level - 1) % ISLAND_THEMES.size
        setTheme(g.theme, instant = false)
        if (g.rocks.size < min(3 + g.level, 11)) placeRocks(1, initial = false)
        fxShowBanner("Level ${g.level}", ISLAND_THEMES[g.theme].name, levelBlurb(g.level))
        emitSfx("level")
        flash = 0.35f
        flashColorHex = "#fff5e3"
        if (g.level == 2) g.timers.beetle = 2.5f
        if (g.level == 3) g.timers.crumble = 3f
    }

    private fun levelBlurb(n: Int): String = when (n) {
        2 -> "Beetles are out. They nibble tails, so lasso them for 100 each."
        3 -> "The ground is crumbling. Hop over the holes."
        4 -> "Faster now, with more rocks."
        else -> "Faster again. Keep looping."
    }

    private fun powerBanner(p: PlayerState, kind: String) {
        val text = when (kind) {
            "blaze" -> "BLAZE! Smash rocks & beetles"
            "frost" -> "FROST! Rival slowed"
            else -> "GHOST! Slip through snakes"
        }
        val h = heads[p.id]
        if (h.ok) fxFloatText(h.x, h.y + 1.4f, h.z, text, GameConst.ORB_COLORS[kind] ?: "#ffffff", 17f, 1.5f)
    }

    private fun smashRock(g: GameState, r: Rock, p: PlayerState) {
        g.rocks.remove(r)
        val pts = 15 * max(1, min(p.combo, GameConst.MAX_MULT))
        p.score += pts
        fxBurst(r.x + 0.5f, 0.4f, r.z + 0.5f, 14, "shard", 3f, 4f, 1.1f, 0.13f, rgbToHex(blendedTheme.rock), 12f)
        fxFloatText(r.x + 0.5f, 1.0f, r.z + 0.5f, "+$pts", "#ff9a4d", 18f, 1.1f)
        emitSfx("smash")
        fxShake(7f)
    }

    private fun bustBeetle(g: GameState, b: Beetle, p: PlayerState) {
        g.beetles.remove(b)
        beetlePoof(b)
        collect(g, p, listOf(GameItem(id = b.id, type = "beetle", x = b.x, z = b.z)), lasso = false)
        emitSfx("smash")
        fxShake(6f)
    }

    private fun beetlePoof(b: Beetle) {
        fxBurst(b.x + 0.5f, 0.3f, b.z + 0.5f, 12, "chunk", 2.4f, 3.5f, 0.9f, 0.08f, "#5a3d9e", 12f)
        fxBurst(b.x + 0.5f, 0.3f, b.z + 0.5f, 8, "spark", 1.8f, 2f, 0.5f, 0.2f, "#78f0ff", 0f)
    }

    /* ---------- Simulation ---------- */
    private fun simulate(dt: Float) {
        val g = state ?: return
        g.time += dt
        if (!g.started) {
            updateRocks(g, dt)
            for (it in g.items) it.age += dt
            if (!g.demo) updateCountdown(g, dt)
            return
        }
        for (p in g.players) {
            if (p.st != "run") continue
            if (p.shield > 0f) p.shield = max(0f, p.shield - dt)
            if (p.comboT > 0f) {
                p.comboT -= dt
                if (p.comboT <= 0f) { p.comboT = 0f; p.combo = 0 }
            }
            if (p.power.blaze > 0f) {
                p.power.blaze -= dt
                if (p.power.blaze <= 0f) { p.power.blaze = 0f; emitSfx("powerEnd") }
            }
            if (p.power.frost > 0f) {
                p.power.frost -= dt
                if (p.power.frost <= 0f) { p.power.frost = 0f; emitSfx("powerEnd") }
            }
            if (p.power.ghost > 0f) {
                p.power.ghost -= dt
                if (p.power.ghost <= 0f) { p.power.ghost = 0f; emitSfx("powerEnd") }
            }
            if (p.hops < GameConst.HOP_MAX) {
                p.hopCharge += dt / GameConst.HOP_RECHARGE
                if (p.hopCharge >= 1f) {
                    p.hopCharge = 0f
                    p.hops++
                    emitSfx("ready")
                }
            } else {
                p.hopCharge = 0f
            }
        }
        for (i in g.items.lastIndex downTo 0) {
            val it = g.items[i]
            it.age += dt
            if (it.life > 0f && it.age >= it.life) {
                g.items.removeAt(i)
                fxBurst(it.x + 0.5f, itemHeight(it), it.z + 0.5f, 6, "dust", 0.8f, 0.8f, 0.5f, 0.12f, "#ffffff", 0f)
            }
        }
        val t = g.timers
        t.orb -= dt
        if (t.orb <= 0f) {
            t.orb = rand(11f, 16f)
            if (g.items.none { it.type == "orb" }) {
                val choices = if (g.solo) arrayOf("blaze", "ghost") else arrayOf("blaze", "frost", "ghost")
                spawnItem("orb", choices[Random.nextInt(choices.size)])
            }
        }
        t.crystal -= dt
        if (t.crystal <= 0f) {
            t.crystal = rand(14f, 22f)
            if (g.items.none { it.type == "crystal" }) spawnItem("crystal")
        }
        if (g.demo || g.level >= 2) {
            t.berry -= dt
            if (t.berry <= 0f) {
                t.berry = rand(18f, 26f)
                spawnBerries()
            }
        }
        val maxB = if (g.demo) 1 else if (g.level >= 2) min(1 + (g.level - 2) / 2, 4) else 0
        if (g.beetles.size < maxB) {
            t.beetle -= dt
            if (t.beetle <= 0f) {
                t.beetle = rand(5f, 9f)
                spawnBeetle()
            }
        }
        if (!g.demo && g.level >= 3) {
            t.crumble -= dt
            if (t.crumble <= 0f) {
                t.crumble = rand(max(2.5f, 7f - g.level * 0.5f), max(4f, 10f - g.level * 0.5f))
                startCrumble()
            }
        }
        updateTiles(g, dt)
        updateRocks(g, dt)
        updateBeetles(g, dt)

        for (p in g.players) if (p.st == "run") p.stepAcc += dt
        var guard = 0
        while (g.phase == "run" && guard++ < 4) {
            val movers = mutableListOf<PlayerState>()
            for (p in g.players) if (p.st == "run" && p.stepAcc >= stepInterval(p)) movers += p
            if (movers.isEmpty()) break
            for (p in movers) p.stepAcc -= stepInterval(p)
            stepPlayers(g, movers)
        }
        for (p in g.players) {
            if (p.st == "run") {
                val iv = stepInterval(p)
                if (p.stepAcc > iv) p.stepAcc = iv
            }
        }
    }

    private fun updateCountdown(g: GameState, dt: Float) {
        g.cdT += dt
        val idx = floor(g.cdT / GameConst.COUNT_STEP).toInt()
        if (idx != g.cdIdx) {
            g.cdIdx = idx
            if (idx < 3) {
                fxShowCount((3 - idx).toString())
                emitSfx("count")
            } else {
                fxShowCount("GO!")
                emitSfx("go")
                g.started = true
                for (p in g.players) p.stepAcc = stepInterval(p) * 0.65f
            }
        }
    }

    private fun updateTiles(g: GameState, dt: Float) {
        val n = g.W * g.H
        for (c in 0 until n) {
            val st = g.tiles[c]
            if (st == GameConst.T_OK) continue
            g.tileT[c] -= dt
            if (g.tileT[c] > 0f) continue
            val x = c % g.W
            val z = c / g.W
            when (st) {
                GameConst.T_SHAKE -> {
                    if (groundSnakeAt(g, x, z) || beetleAt(g, x, z) != null || itemAt(g, x, z) != null || rockAt(g, x, z) != null) {
                        g.tileT[c] = 0.25f
                        continue
                    }
                    g.tiles[c] = GameConst.T_FALL
                    g.tileT[c] = GameConst.FALL_TIME
                    emitSfx("crumble")
                    fxShake(3f)
                    fxBurst(x + 0.5f, 0.05f, z + 0.5f, 10, "chunk", 1.5f, 2.2f, 0.8f, 0.07f, rgbToHex(blendedTheme.tileMid), 12f)
                    fxBurst(x + 0.5f, 0.05f, z + 0.5f, 6, "dust", 1f, 0.6f, 0.7f, 0.18f, rgbToHex(shadeRgb(blendedTheme.dirtTop, 0.3f)), 0f)
                }
                GameConst.T_FALL -> { g.tiles[c] = GameConst.T_HOLE; g.tileT[c] = rand(11f, 15f) }
                GameConst.T_HOLE -> { g.tiles[c] = GameConst.T_RISE; g.tileT[c] = GameConst.RISE_TIME }
                else -> { g.tiles[c] = GameConst.T_OK; g.tileT[c] = 0f }
            }
        }
    }

    private fun updateRocks(g: GameState, dt: Float) {
        for (r in g.rocks) {
            if (r.st == "warn") {
                r.t -= dt
                if (r.t <= 0f) {
                    if (anySnakeAt(g, r.x, r.z) || beetleAt(g, r.x, r.z) != null || itemAt(g, r.x, r.z) != null) {
                        r.t = 0.3f
                        continue
                    }
                    r.st = "rise"
                    r.t = 0f
                    emitSfx("rumble")
                    fxBurst(r.x + 0.5f, 0.05f, r.z + 0.5f, 8, "dust", 1.4f, 0.5f, 0.6f, 0.18f, rgbToHex(shadeRgb(blendedTheme.tileMid, 0.25f)), 0f)
                }
            } else if (r.st == "rise") {
                r.t += dt / 0.45f
                if (r.t >= 1f) { r.t = 1f; r.st = "solid" }
            }
        }
    }

    /* ---------- Beetles ---------- */
    private fun updateBeetles(g: GameState, dt: Float) {
        for (b in g.beetles) {
            b.phase += dt * 12f
            if (b.emerge > 0f) { b.emerge = max(0f, b.emerge - dt); b.fx = b.x; b.fz = b.z; continue }
            if (b.stun > 0f) {
                b.stun = max(0f, b.stun - dt)
                b.fx = b.x
                b.fz = b.z
                if (Random.nextFloat() < dt * 2.2f) {
                    addPart(Particle("z", b.x + 0.6f, 0.6f, b.z + 0.4f, 0.15f, 0.5f, 0f, 1.1f, 1.1f, 0.16f, "#fff5e3", 0f, 1f, 0f, 0f))
                }
                continue
            }
            b.acc += dt
            if (b.acc >= b.iv) {
                b.acc -= b.iv
                beetleStep(g, b)
            }
        }
    }

    private fun beetleCan(g: GameState, b: Beetle, x: Int, z: Int): Int {
        if (!inBounds(g, x, z)) return 0
        if (g.tiles[z * g.W + x] != GameConst.T_OK) return 0
        if (rockAt(g, x, z) != null || itemAt(g, x, z) != null) return 0
        for (o in g.beetles) if (o !== b && o.x == x && o.z == z) return 0
        var bite = 0
        for (p in g.players) {
            if (p.st != "run") continue
            val h = p.snake[0]
            if (h.x == x && h.z == z) return 0
            if (h.x + p.dir.x == x && h.z + p.dir.z == z) return 0
            for (i in p.snake.indices) {
                val s = p.snake[i]
                if (s.x != x || s.z != z || s.h != 0f) continue
                if (i < 3) return 0
                if (p.power.ghost <= 0f) bite = 2
            }
        }
        return if (bite != 0) bite else 1
    }

    private fun beetleStep(g: GameState, b: Beetle) {
        b.fx = b.x
        b.fz = b.z
        val f = b.hx to b.hz
        val l = b.hz to -b.hx
        val r = -b.hz to b.hx
        val order = (if (Random.nextFloat() < 0.7f) {
            if (Random.nextFloat() < 0.5f) mutableListOf(f, l, r) else mutableListOf(f, r, l)
        } else {
            if (Random.nextFloat() < 0.5f) mutableListOf(l, f, r) else mutableListOf(r, f, l)
        })
        if (Random.nextFloat() < 0.45f) {
            var tgt: Segment? = null
            var td = Int.MAX_VALUE
            for (p in g.players) {
                if (p.st != "run" || p.power.ghost > 0f) continue
                for (i in 3 until p.snake.size) {
                    val s = p.snake[i]
                    if (s.h != 0f) continue
                    val d = abs(s.x - b.x) + abs(s.z - b.z)
                    if (d < td) { td = d; tgt = s }
                }
            }
            if (tgt != null && td < 9) {
                order.sortBy { abs(b.x + it.first - tgt.x) + abs(b.z + it.second - tgt.z) }
            }
        }
        order += (-b.hx to -b.hz)
        for (d in order) {
            val nx = b.x + d.first
            val nz = b.z + d.second
            val res = beetleCan(g, b, nx, nz)
            if (res == 0) continue
            b.x = nx
            b.z = nz
            b.hx = d.first
            b.hz = d.second
            if (res == 2) biteAt(g, b, nx, nz)
            return
        }
    }

    private fun biteAt(g: GameState, b: Beetle, x: Int, z: Int) {
        for (p in g.players) {
            if (p.st != "run" || p.power.ghost > 0f) continue
            val idx = p.snake.indices.firstOrNull { i ->
                i >= 3 && p.snake[i].x == x && p.snake[i].z == z && p.snake[i].h == 0f
            } ?: continue
            val lost = p.snake.subList(idx, p.snake.size).toList()
            p.snake.subList(idx, p.snake.size).clear()
            b.stun = 3.2f
            p.enclosed = null
            lost.forEachIndexed { i, seg -> fxPop(p, seg, i, bite = true) }
            fxFloatText(x + 0.5f, 0.9f, z + 0.5f, "−${lost.size}", "#ff6b78", 22f, 1.1f)
            emitSfx("bite")
            fxShake(5f)
            return
        }
    }

    /* ---------- Demo Autopilot ---------- */
    private fun aiSafe(g: GameState, p: PlayerState, x: Int, z: Int): Boolean {
        if (!inBounds(g, x, z)) return false
        if (g.tiles[z * g.W + x] >= GameConst.T_FALL) return false
        val r = rockAt(g, x, z)
        if (r != null && r.st != "warn") return false
        if (beetleAt(g, x, z) != null) return false
        for (q in g.players) {
            if (q.st != "run") continue
            val lim = if (q === p) (if (q.grow > 0) q.snake.size else q.snake.size - 1) else q.snake.size
            for (i in 0 until lim) {
                val s = q.snake[i]
                if (s.x == x && s.z == z && s.h == 0f) return false
            }
            if (q !== p) {
                val h = q.snake[0]
                if (abs(h.x + q.dir.x - x) + abs(h.z + q.dir.z - z) == 0) return false
            }
        }
        return true
    }

    private fun floodRoom(g: GameState, p: PlayerState, sx: Int, sz: Int, cap: Int): Int {
        val w = g.W
        val n = w * g.H
        val bl = BooleanArray(n)
        for (q in g.players) {
            if (q.st != "run") continue
            val lim = if (q === p) q.snake.size - 1 else q.snake.size
            for (i in 0 until lim) {
                val s = q.snake[i]
                bl[s.z * w + s.x] = true
            }
        }
        for (r in g.rocks) if (r.st != "warn") bl[r.z * w + r.x] = true
        for (b in g.beetles) bl[b.z * w + b.x] = true
        for (c in 0 until n) if (g.tiles[c] >= GameConst.T_FALL) bl[c] = true
        val s0 = sz * w + sx
        val q = ArrayDeque<Int>()
        q.add(s0)
        bl[s0] = true
        var count = 0
        while (q.isNotEmpty() && count < cap) {
            val c = q.removeLast()
            count++
            val x = c % w
            val z = c / w
            if (x > 0 && !bl[c - 1]) { bl[c - 1] = true; q.add(c - 1) }
            if (x < w - 1 && !bl[c + 1]) { bl[c + 1] = true; q.add(c + 1) }
            if (z > 0 && !bl[c - w]) { bl[c - w] = true; q.add(c - w) }
            if (z < g.H - 1 && !bl[c + w]) { bl[c + w] = true; q.add(c + w) }
        }
        return count
    }

    private fun aiSteer(g: GameState, p: PlayerState) {
        val h = p.snake[0]
        val dirs = arrayOf(p.dir, GridDir(p.dir.z, -p.dir.x), GridDir(-p.dir.z, p.dir.x))
        var tgt: GameItem? = null
        var td = Int.MAX_VALUE
        for (it in g.items) {
            val d = abs(it.x - h.x) + abs(it.z - h.z)
            if (d < td) { td = d; tgt = it }
        }
        var bestD: GridDir? = null
        var bestS = Float.NEGATIVE_INFINITY
        for (d in dirs) {
            val nx = h.x + d.x
            val nz = h.z + d.z
            if (!aiSafe(g, p, nx, nz)) continue
            var sc = if (tgt != null) -(abs(tgt.x - nx) + abs(tgt.z - nz)).toFloat() else 0f
            val room = floodRoom(g, p, nx, nz, p.snake.size + 6)
            if (room < p.snake.size + 3) sc -= (200 - room)
            sc += (if (d === p.dir) 0.4f else 0f) + Random.nextFloat() * 0.6f
            if (sc > bestS) { bestS = sc; bestD = d }
        }
        if (bestD != null) {
            p.dir = bestD
            p.queue.clear()
        } else if (p.hops > 0 && p.airSteps == 0) {
            p.hops--
            p.airSteps = GameConst.JUMP_STEPS
        }
    }

    /* ---------- FX & Network Recording ---------- */
    private fun addPart(p: Particle) {
        if (particles.size < 650) particles += p
    }

    private fun recordNet(ev: NetFxEvent) {
        val g = state
        if (netRole == NetRole.HOST && netUp && g != null && !g.demo && netFxDepth == 0 && netEvents.size < 300) {
            netEvents += ev
        }
    }

    private fun emitSfx(name: String, arg: Int = 0) {
        val g = state
        if (netReplay || (g != null && g.demo)) return
        if (netRole == NetRole.HOST && netUp && netEvents.size < 300) {
            netEvents += NetFxEvent(op = "sfx", s1 = name, i1 = arg)
        }
        sound.play(name, arg)
    }

    private fun fxBurst(
        x: Float, y: Float, z: Float, n: Int, kind: String,
        speed: Float, up: Float, life: Float, size: Float, colorHex: String, gGrav: Float
    ) {
        recordNet(NetFxEvent(op = "burst", f1 = x, f2 = y, f3 = z, i1 = n, s1 = kind, f4 = speed, f5 = up, f6 = life, f7 = size, s2 = colorHex, i2 = gGrav.toInt()))
        netFxDepth++
        try {
            repeat(n) {
                val a = Random.nextFloat() * GameConst.TAU
                val sp = rand(speed * 0.4f, speed)
                val lf = rand(life * 0.6f, life)
                addPart(
                    Particle(
                        k = kind, x = x, y = y, z = z,
                        vx = cos(a) * sp, vy = rand(up * 0.5f, up), vz = sin(a) * sp,
                        life = lf, max = lf, size = rand(size * 0.6f, size),
                        colorHex = colorHex, g = gGrav, drag = 0.9f,
                        rot = Random.nextFloat() * GameConst.TAU, vr = rand(-8f, 8f)
                    )
                )
            }
        } finally {
            netFxDepth--
        }
    }

    private fun fxRing(x: Float, z: Float, r0: Float, r1: Float, life: Float, colorHex: String, alpha: Float, w: Float) {
        recordNet(NetFxEvent(op = "ring", f1 = x, f2 = z, f3 = r0, f4 = r1, f5 = life, s1 = colorHex, f6 = alpha, f7 = w))
        rings += RingFx(x, z, r0, r1, life, life, hexToRgb(colorHex), alpha, w)
    }

    private fun fxFloatText(x: Float, y: Float, z: Float, text: String, colorHex: String, size: Float, life: Float) {
        recordNet(NetFxEvent(op = "floatText", f1 = x, f2 = y, f3 = z, s1 = text, s2 = colorHex, f4 = size, f5 = life))
        floatTexts += FloatTextFx(x, y, z, text, colorHex, size, life, life)
    }

    private fun fxShake(amt: Float) {
        recordNet(NetFxEvent(op = "addShake", f1 = amt))
        shakeAmt = max(shakeAmt, amt)
    }

    private fun fxPop(p: PlayerState, seg: Segment, i: Int, bite: Boolean) {
        recordNet(NetFxEvent(op = "popFx", i1 = p.id, seg = seg.copy(), i2 = i, b1 = bite))
        netFxDepth++
        try {
            val x = seg.x + 0.5f
            val z = seg.z + 0.5f
            val y = seg.h + 0.35f
            val col = skinOf(p).bodyHex
            fxBurst(x, y, z, if (bite) 3 else 5, "chunk", 2.2f, 3.6f, 1.1f, 0.11f, col, 12f)
            fxBurst(x, y, z, 2, "spark", 1.4f, 1.4f, 0.35f, 0.2f, "#ffffff", 0f)
            if (!bite && i % 2 == 0) emitSfx("pop", i)
        } finally {
            netFxDepth--
        }
    }

    private fun fxCollect(p: PlayerState, it: GameItem, pts: Int, lasso: Boolean) {
        recordNet(NetFxEvent(op = "collectFx", i1 = p.id, item = it.copy(), i2 = pts, b1 = lasso))
        netFxDepth++
        try {
            val x = it.x + 0.5f
            val z = it.z + 0.5f
            if (it.type == "beetle") {
                for (i in 0 until 3) flyItems += FlyFx(p.id, "#78f0ff", x, 0.4f, z, -i * 0.07f, 0.5f, 0.13f, 1.4f)
            } else {
                flyItems += FlyFx(p.id, itemColorHex(it), x, itemHeight(it), z, 0f, if (lasso) 0.5f else 0.13f, itemRadius(it), if (lasso) 1.3f else 0.1f)
            }
            val big = it.type == "crystal" || it.type == "beetle"
            fxFloatText(x, 0.95f, z, "+${fmtInt(pts)}", if (lasso) "#ffc23a" else playerColorHex(p.id), if (big) 24f else 19f, 1.1f)
            fxRing(x, z, 0.1f, 0.8f, 0.45f, itemColorHex(it), 0.9f, 0.06f)
        } finally {
            netFxDepth--
        }
    }

    private fun fxLasso(p: PlayerState, cells: IntArray, count: Int) {
        recordNet(NetFxEvent(op = "lassoFx", i1 = p.id, cells = cells, i2 = count))
        netFxDepth++
        try {
            val g = state ?: return
            glows += GlowFx(cells, 0.9f, 0.9f, p.id)
            p.glowT = 0.9f
            for (c in cells) {
                val x = (c % g.W) + 0.5f
                val z = (c / g.W) + 0.5f
                fxBurst(x, 0.1f, z, 2, "spark", 0.6f, 2.6f, 0.7f, 0.13f, "#ffc23a", 2f)
            }
            val h = p.snake[0]
            fxFloatText(h.x + 0.5f, 1.7f, h.z + 0.5f, if (count > 1) "LASSO ×$count" else "LASSO!", "#ffc23a", 30f, 1.4f)
            fxShake(4f)
            zoomKick = 0.035f
            flash = 0.16f
            flashColorHex = "#ffd77a"
        } finally {
            netFxDepth--
        }
    }

    fun fxShowBanner(title: String, sub: String, blurb: String) {
        recordNet(NetFxEvent(op = "showBanner", s1 = title, s2 = sub, s3 = blurb))
        bannerTitle = title
        bannerSub = sub
        bannerBlurb = blurb
        bannerTimer = 3.4f
    }

    fun fxShowCount(text: String) {
        recordNet(NetFxEvent(op = "showCount", s1 = text))
        countText = text
        countKey++
        countTimer = if (text == "GO!") 0.7f else 0.95f
    }

    fun fxShowHint(text: String, dur: Float, kind: String = "") {
        recordNet(NetFxEvent(op = "showHint", s1 = text, f1 = dur, s2 = kind))
        hintText = text
        hintTimer = dur
        hintKind = kind
    }

    fun hideHint() {
        hintTimer = 0f
        hintKind = ""
        hintText = ""
    }

    private fun updateVisuals(dt: Float) {
        val g = state
        for (i in particles.lastIndex downTo 0) {
            val p = particles[i]
            p.life -= dt
            if (p.life <= 0f) {
                particles[i] = particles[particles.lastIndex]
                particles.removeAt(particles.lastIndex)
                continue
            }
            val dr = p.drag.pow(dt * 60f)
            p.vx *= dr
            p.vz *= dr
            p.vy -= p.g * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.z += p.vz * dt
            if ((p.k == "chunk" || p.k == "shard") && p.y < p.size * 0.5f) {
                p.y = p.size * 0.5f
                if (p.vy < 0f) p.vy *= -0.45f
                p.vx *= 0.75f
                p.vz *= 0.75f
            }
            p.rot += p.vr * dt
        }
        for (i in rings.lastIndex downTo 0) {
            rings[i].life -= dt
            if (rings[i].life <= 0f) rings.removeAt(i)
        }
        for (i in glows.lastIndex downTo 0) {
            glows[i].life -= dt
            if (glows[i].life <= 0f) glows.removeAt(i)
        }
        for (i in floatTexts.lastIndex downTo 0) {
            floatTexts[i].life -= dt
            if (floatTexts[i].life <= 0f) floatTexts.removeAt(i)
        }
        for (i in flyItems.lastIndex downTo 0) {
            val f = flyItems[i]
            f.t += dt / f.dur
            if (f.t >= 1f) {
                flyItems.removeAt(i)
                val h = heads[f.owner]
                if (h.ok) fxBurst(h.x, h.y, h.z, 4, "spark", 1.4f, 1.6f, 0.4f, 0.16f, f.colorHex, 0f)
            }
        }
        for (i in bulges.lastIndex downTo 0) {
            val b = bulges[i]
            val pl = g?.players?.getOrNull(b.playerIdx)
            b.p += (dt / (if (pl != null) stepInterval(pl) else 0.15f)) * 0.6f
            if (pl == null || b.p > pl.snake.size + 1) bulges.removeAt(i)
        }
        if (g != null) {
            for (p in g.players) {
                p.glowT = max(0f, p.glowT - dt * 1.3f)
                p.pulse = max(0f, p.pulse - dt * 5f)
            }
            if (g.started) {
                var anyFrost = false
                for (p in g.players) {
                    val h = heads[p.id]
                    if (p.st != "run") continue
                    if (p.power.frost > 0f) anyFrost = true
                    if (p.power.blaze > 0f && h.ok) {
                        blazeAcc[p.id] += dt * 45f
                        while (blazeAcc[p.id] >= 1f) {
                            blazeAcc[p.id]--
                            val lf = rand(0.3f, 0.5f)
                            val c = arrayOf("#ffb03a", "#ff6a2a", "#ffd36a")[Random.nextInt(3)]
                            addPart(
                                Particle(
                                    "flame",
                                    h.x + rand(-0.15f, 0.15f), h.y + 0.1f, h.z + rand(-0.15f, 0.15f),
                                    rand(-0.3f, 0.3f), rand(0.8f, 1.6f), rand(-0.3f, 0.3f),
                                    lf, 0.5f, rand(0.12f, 0.2f), c, -0.5f, 0.95f, 0f, 0f
                                )
                            )
                        }
                    }
                }
                if (anyFrost) {
                    frostAcc += dt * 26f
                    while (frostAcc >= 1f) {
                        frostAcc--
                        addPart(
                            Particle(
                                "snow",
                                rand(0f, g.W.toFloat()), rand(2.5f, 4f), rand(0f, g.H.toFloat()),
                                rand(-0.2f, 0.2f), -rand(0.6f, 1.1f), rand(-0.1f, 0.2f),
                                3f, 3f, rand(0.04f, 0.08f), "#ffffff", 0f, 1f, 0f, 0f
                            )
                        )
                    }
                }
            }
        }
    }

    /* ---------- Main Tick ---------- */
    fun update(rawDt: Float): Boolean {
        val dt = rawDt.coerceIn(0f, 0.05f)
        clock += dt
        if (themeT < 1f) {
            themeT = min(1f, themeT + dt / 1.6f)
            computeBlendedTheme()
        }
        val k = 1f - exp(-dt * 3.2f)
        fitFocal = lerp(fitFocal, fitTargetFocal, k)
        fitCx = lerp(fitCx, fitTargetCx, k)
        fitCy = lerp(fitCy, fitTargetCy, k)

        sound.tickMusic(dt, inMatch = screen == ScreenMode.PLAY)

        if (screen == ScreenMode.PAUSE) return false

        updateCamera(dt)
        val g = state
        if (netRole == NetRole.GUEST && screen == ScreenMode.PLAY) {
            guestStep(dt)
        } else if (screen == ScreenMode.TITLE || screen == ScreenMode.PLAY) {
            if (g != null) {
                if (g.phase == "run") simulate(dt)
                if (g.phase == "run" || g.phase == "ending") updateDying(dt)
                if (g.phase == "ending" && screen == ScreenMode.PLAY) {
                    g.endT -= dt
                    if (g.endT <= 0f) {
                        showOver()
                        return true
                    }
                }
            }
        }
        netFxDepth++
        try {
            updateVisuals(dt)
        } finally {
            netFxDepth--
        }
        if (hintKind == "start" && g?.started == true) hideHint()
        if (hintTimer > 0f) {
            hintTimer -= dt
            if (hintTimer <= 0f) hideHint()
        }
        if (bannerTimer > 0f) {
            bannerTimer -= dt
            if (bannerTimer <= 0f) bannerTimer = 0f
        }
        if (countTimer > 0f) {
            countTimer -= dt
            if (countTimer <= 0f) countTimer = 0f
        }
        netTick(dt)
        return false
    }

    /* ---------- Network Host/Guest Protocol ---------- */
    private fun sendNetMsg(msg: NetWireMsg) {
        if (!netUp) return
        try {
            onNetSend(json.encodeToString(msg))
        } catch (_: Exception) {
        }
    }

    fun sendNetStart(sk: List<Int>) {
        netMe = 0
        netSnapAcc = 0f
        netEvents.clear()
        sendNetMsg(NetWireMsg(t = "go", sk = sk))
    }

    fun sendNetLobby() {
        if (netUp) sendNetMsg(NetWireMsg(t = "lobby"))
    }

    fun sendNetRematch() {
        if (netUp) {
            sendNetMsg(NetWireMsg(t = "rematch"))
            onToast("Waiting for the host…")
        }
    }

    private fun netTick(dt: Float) {
        val g = state ?: return
        if (netRole == NetRole.HOST && netUp && !g.demo && (screen == ScreenMode.PLAY || screen == ScreenMode.OVER)) {
            netSnapAcc += dt
            if (netSnapAcc >= 0.04f) {
                netSnapAcc = 0f
                val ev = netEvents.toList()
                netEvents.clear()
                sendNetMsg(NetWireMsg(t = "s", g = g, ev = ev))
            }
        }
    }

    private fun guestStep(dt: Float) {
        val g = state ?: return
        if (netFresh) return
        if (g.phase == "run" && g.started) {
            for (p in g.players) {
                if (p.st == "run") p.stepAcc = min(stepInterval(p), p.stepAcc + dt)
            }
        }
        for (it in g.items) it.age += dt
        if (g.phase == "ending") {
            g.endT -= dt
            if (g.endT <= 0f) showOver()
        }
    }

    fun onNetOpen() {
        netUp = true
        netBusy = false
        onToast(if (netRole == NetRole.HOST) "Friend connected!" else "Connected to the host!")
        val g = state
        if (netRole == NetRole.HOST && screen == ScreenMode.PLAY && g != null && !g.demo) {
            sendNetMsg(NetWireMsg(t = "go", sk = skinIndices.toList()))
        }
    }

    fun onNetClose() {
        val was = netUp
        netUp = false
        netBusy = false
        netEvents.clear()
        if (!was || netRole == NetRole.NONE) return
        if (netRole == NetRole.HOST) {
            onToast("Your friend left")
            if (screen == ScreenMode.PLAY || screen == ScreenMode.OVER) onGoToMenuAfterDisconnect(true)
        } else {
            onToast("Lost connection to the host")
            if (screen != ScreenMode.TITLE) onGoToMenuAfterDisconnect(false)
        }
    }

    fun onNetMessage(raw: String, onStartGuestMatch: () -> Unit, onHostRematch: () -> Unit) {
        val m = try {
            json.decodeFromString<NetWireMsg>(raw)
        } catch (_: Exception) {
            return
        }
        when (netRole) {
            NetRole.HOST -> {
                if (m.t == "i" && screen == ScreenMode.PLAY) {
                    if (m.k == 0) steer(1, m.x, m.z) else tryHop(1)
                } else if (m.t == "rematch" && screen == ScreenMode.OVER) {
                    onHostRematch()
                }
            }
            NetRole.GUEST -> {
                when (m.t) {
                    "s" -> guestApply(m)
                    "go" -> {
                        val sk = m.sk
                        if (sk != null && sk.size >= 2 && sk[0] in SKIN_LIST.indices && sk[1] in SKIN_LIST.indices) {
                            skinIndices[0] = sk[0]
                            skinIndices[1] = sk[1]
                        }
                        onStartGuestMatch()
                    }
                    "lobby" -> if (screen != ScreenMode.TITLE) onGoToMenuAfterDisconnect(true)
                }
            }
            NetRole.NONE -> Unit
        }
    }

    private fun guestApply(m: NetWireMsg) {
        if (screen != ScreenMode.PLAY && screen != ScreenMode.OVER) return
        val g = m.g ?: return
        val old = state
        state = g
        if (netFresh || deco == null || old == null || old.W != g.W || old.H != g.H || old.seed != g.seed) {
            netFresh = false
            buildDeco(g)
            setTheme(g.theme, instant = true)
            netTheme = g.theme
            lookX = g.W / 2f
            lookZ = g.H / 2f
            computeFit()
        } else if (g.theme != netTheme) {
            netTheme = g.theme
            setTheme(g.theme, instant = false)
        }
        m.ev?.forEach { ev ->
            netReplay = true
            try {
                replayNetEvent(ev)
            } catch (_: Exception) {
            } finally {
                netReplay = false
            }
        }
    }

    private fun replayNetEvent(e: NetFxEvent) {
        val g = state ?: return
        when (e.op) {
            "sfx" -> sound.play(e.s1, e.i1)
            "burst" -> fxBurst(e.f1, e.f2, e.f3, e.i1, e.s1, e.f4, e.f5, e.f6, e.f7, e.s2, e.i2.toFloat())
            "ring" -> fxRing(e.f1, e.f2, e.f3, e.f4, e.f5, e.s1, e.f6, e.f7)
            "floatText" -> fxFloatText(e.f1, e.f2, e.f3, e.s1, e.s2, e.f4, e.f5)
            "addShake" -> fxShake(e.f1)
            "popFx" -> e.seg?.let { g.players.getOrNull(e.i1)?.let { p -> fxPop(p, it, e.i2, e.b1) } }
            "collectFx" -> e.item?.let { g.players.getOrNull(e.i1)?.let { p -> fxCollect(p, it, e.i2, e.b1) } }
            "lassoFx" -> g.players.getOrNull(e.i1)?.let { p -> fxLasso(p, e.cells, e.i2) }
            "showBanner" -> fxShowBanner(e.s1, e.s2, e.s3)
            "showCount" -> fxShowCount(e.s1)
            "showHint" -> fxShowHint(e.s1, e.f1, e.s2)
        }
    }

    fun showOver() {
        screen = ScreenMode.OVER
        bannerTimer = 0f
        countTimer = 0f
        hideHint()
        computeFit()
    }

    fun currentPlayerIndex(): Int = when {
        state?.solo == true -> 0
        netRole != NetRole.NONE -> netMe
        else -> meIndex
    }

    fun maxPower(kind: String): Float {
        val g = state ?: return 0f
        var m = 0f
        for (p in g.players) {
            val v = when (kind) {
                "blaze" -> p.power.blaze
                "frost" -> p.power.frost
                else -> p.power.ghost
            }
            if (v > m) m = v
        }
        return m
    }

    /* ---------- Math Utils ---------- */
    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    fun smooth(t: Float) = t * t * (3f - 2f * t)
    fun easeOut(t: Float): Float { val u = 1f - t; return 1f - u * u * u }
    fun easeBack(t: Float): Float { val c = 1.70158f; val u = t - 1f; return 1f + (c + 1f) * u * u * u + c * u * u }
    private fun rand(a: Float, b: Float) = a + Random.nextFloat() * (b - a)
    fun shadeRgb(c: FloatArray, f: Float): FloatArray {
        return if (f >= 0f) {
            floatArrayOf(c[0] + (255f - c[0]) * f, c[1] + (255f - c[1]) * f, c[2] + (255f - c[2]) * f)
        } else {
            val k = -f
            floatArrayOf(c[0] * (1f - k), c[1] * (1f - k), c[2] * (1f - k))
        }
    }
    fun rgbToHex(c: FloatArray): String {
        val r = c[0].toInt().coerceIn(0, 255)
        val g = c[1].toInt().coerceIn(0, 255)
        val b = c[2].toInt().coerceIn(0, 255)
        return String.format("#%02x%02x%02x", r, g, b)
    }
    fun fmtInt(n: Int): String = "%,d".format(n)
}
