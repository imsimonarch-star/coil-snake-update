package com.coilsnake.game.game

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

object GameConst {
    const val TAU = (PI * 2.0).toFloat()
    const val TILE_GAP = 0.045f
    const val TILE_H = 0.07f
    const val SLAB_T = 1.1f
    const val BODY_R = 0.355f
    const val HEAD_R = 0.43f
    const val SUB = 3
    val JUMP_ARC = floatArrayOf(0.72f, 1.06f, 0.72f)
    val JUMP_STEPS = JUMP_ARC.size
    const val HOP_MAX = 3
    const val HOP_RECHARGE = 4.5f
    const val COMBO_WINDOW = 3.6f
    const val MAX_MULT = 9
    const val APPLES_PER_LEVEL = 8
    const val APPLE_COUNT = 3

    const val T_OK = 0
    const val T_SHAKE = 1
    const val T_FALL = 2
    const val T_HOLE = 3
    const val T_RISE = 4

    const val SHAKE_TIME = 1.7f
    const val FALL_TIME = 0.8f
    const val RISE_TIME = 0.65f

    const val POWER_BLAZE_DUR = 7f
    const val POWER_FROST_DUR = 7f
    const val POWER_GHOST_DUR = 7f

    const val HEARTS = 3
    const val SHIELD_TIME = 2.2f
    const val LEAD = 0.45f
    const val FROST_SLOW = 0.6f
    const val COUNT_STEP = 0.9f
    const val NET_PORT = 8765

    val BASE_PTS = mapOf(
        "apple" to 10,
        "berry" to 5,
        "crystal" to 50,
        "orb" to 20,
        "beetle" to 100
    )

    val ORB_COLORS = mapOf(
        "blaze" to "#ff7a2f",
        "frost" to "#58d2ff",
        "ghost" to "#b08cff"
    )

    val CAUSES = mapOf(
        "edge" to "slid off the edge of the island",
        "hole" to "fell into a hole",
        "rock" to "bonked into a rock",
        "self" to "bit their own tail",
        "beetle" to "ran into a beetle",
        "rival" to "crashed into the other snake",
        "head" to "crashed head-on"
    )
}

data class SnakeSkin(
    val name: String,
    val bodyHex: String,
    val bandHex: String,
    val headHex: String
)

val SKIN_LIST = listOf(
    SnakeSkin("Teal", "#22c7b2", "#11867f", "#31d9c5"),
    SnakeSkin("Pink", "#ff5fa8", "#c42d77", "#ff82bd"),
    SnakeSkin("Lime", "#8be04a", "#4f9d1f", "#a9f069"),
    SnakeSkin("Sunny", "#ffc23a", "#c4850f", "#ffd877"),
    SnakeSkin("Grape", "#8a5cff", "#5a34c7", "#a889ff"),
    SnakeSkin("Crimson", "#e8434f", "#a32937", "#ff6b78"),
    SnakeSkin("Ocean", "#3b82f6", "#1d4fb0", "#6aa3ff"),
    SnakeSkin("Snow", "#e9f1f7", "#a9b8c6", "#ffffff"),
    SnakeSkin("Peach", "#ffa58a", "#d96a4a", "#ffc2ad"),
    SnakeSkin("Chocolate", "#a0714f", "#6a4430", "#bb8a68")
)

val POWER_SKINS = mapOf(
    "blaze" to SnakeSkin("Blaze", "#ff7d33", "#d9431d", "#ff9a4d"),
    "ghost" to SnakeSkin("Ghost", "#b9a6ff", "#8c74ea", "#cbbcff"),
    "frozen" to SnakeSkin("Frozen", "#8fd8ff", "#4fa4d9", "#bdeaff")
)

data class IslandThemeDef(
    val name: String,
    val skyTop: FloatArray,
    val skyBot: FloatArray,
    val tileA: FloatArray,
    val tileB: FloatArray,
    val grout: FloatArray,
    val groutHex: String,
    val lip: FloatArray,
    val dirtTop: FloatArray,
    val dirtBot: FloatArray,
    val rock: FloatArray,
    val fog: FloatArray,
    val hole: FloatArray,
    val cloud: FloatArray,
    val shadow: Float,
    val glow: Float,
    val dark: Float,
    val decal: String
)

fun hexToRgb(hex: String): FloatArray {
    val clean = hex.removePrefix("#")
    val n = clean.toLong(16).toInt()
    return floatArrayOf(
        ((n shr 16) and 0xFF).toFloat(),
        ((n shr 8) and 0xFF).toFloat(),
        (n and 0xFF).toFloat()
    )
}

fun makeTheme(
    name: String,
    skyTop: String, skyBot: String, tileA: String, tileB: String,
    grout: String, lip: String, dirtTop: String, dirtBot: String,
    rock: String, fog: String, hole: String, cloud: String,
    shadow: Float, glow: Float, dark: Float, decal: String
) = IslandThemeDef(
    name,
    hexToRgb(skyTop), hexToRgb(skyBot), hexToRgb(tileA), hexToRgb(tileB),
    hexToRgb(grout), grout, hexToRgb(lip), hexToRgb(dirtTop), hexToRgb(dirtBot),
    hexToRgb(rock), hexToRgb(fog), hexToRgb(hole), hexToRgb(cloud),
    shadow, glow, dark, decal
)

val ISLAND_THEMES = listOf(
    makeTheme("Sunny Meadow", "#79c4ee", "#ffe8c2", "#c4e27f", "#b2d56d", "#6c9a45", "#7bbb4b", "#9b6a42", "#4b2e20", "#a4adb8", "#fff1d8", "#1c2433", "#ffffff", 0.30f, 0f, 0f, "flower"),
    makeTheme("Amber Dunes", "#ef8a6b", "#ffd9a2", "#f3d59c", "#e9c585", "#c39056", "#e7bb7a", "#b9753f", "#5c3318", "#cd8858", "#ffe3ba", "#2a1a16", "#fff0da", 0.30f, 0f, 0f, "pebble"),
    makeTheme("Frostbite Tundra", "#8cb1f4", "#eef4ff", "#f4f8ff", "#e2eaf9", "#a7bce3", "#ffffff", "#8ea9dc", "#3d5286", "#9edcf3", "#f3f7ff", "#17213f", "#ffffff", 0.24f, 0f, 0f, "sparkle"),
    makeTheme("Ember Caldera", "#1d0e18", "#6b2727", "#4c3d42", "#403338", "#ff6b2c", "#3b2e32", "#3b2a2e", "#130b0e", "#3a3433", "#5b2b27", "#0b0405", "#ff8a4a", 0.40f, 1f, 1f, "crack"),
    makeTheme("Neon Abyss", "#0b0a27", "#2d2566", "#2c2657", "#25204c", "#22e4ff", "#3d3386", "#231d4d", "#0a0821", "#a96cff", "#2e2869", "#050410", "#7c5cff", 0.40f, 1f, 1f, "dot")
)

class BlendedTheme {
    var skyTop = FloatArray(3)
    var skyBot = FloatArray(3)
    var tileA = FloatArray(3)
    var tileB = FloatArray(3)
    var tileMid = FloatArray(3)
    var grout = FloatArray(3)
    var lip = FloatArray(3)
    var dirtTop = FloatArray(3)
    var dirtBot = FloatArray(3)
    var rock = FloatArray(3)
    var fog = FloatArray(3)
    var hole = FloatArray(3)
    var cloud = FloatArray(3)
    var shadow = 0.3f
    var glow = 0f
    var dark = 0f
    var decal = "flower"
    var glowHex = "#ff6b2c"
}

@Serializable
data class Segment(var x: Int, var z: Int, var h: Float = 0f)

@Serializable
data class GridDir(var x: Int, var z: Int)

@Serializable
data class BonkVec(var x: Int, var z: Int)

@Serializable
data class PowerTimers(var blaze: Float = 0f, var frost: Float = 0f, var ghost: Float = 0f)

@Serializable
data class PlayerStats(
    var lassos: Int = 0,
    var beetles: Int = 0,
    var maxLen: Int = 5,
    var bestLasso: Int = 0
)

@Serializable
data class PlayerState(
    val id: Int,
    val name: String,
    val snake: MutableList<Segment> = mutableListOf(),
    var prev: MutableList<Segment> = mutableListOf(),
    var dir: GridDir = GridDir(if (id == 0) 1 else -1, 0),
    val queue: MutableList<GridDir> = mutableListOf(),
    var grow: Int = 0,
    var airSteps: Int = 0,
    var stepAcc: Float = 0f,
    var st: String = "run",
    var deathT: Float = 0f,
    var popped: Int = 0,
    var cause: String = "",
    var bonk: BonkVec? = null,
    var diedAt: Float = 0f,
    var score: Int = 0,
    var combo: Int = 0,
    var comboT: Float = 0f,
    var hearts: Int = GameConst.HEARTS,
    var shield: Float = 0f,
    var hops: Int = GameConst.HOP_MAX,
    var hopCharge: Float = 0f,
    val power: PowerTimers = PowerTimers(),
    var enclosed: IntArray? = null,
    var pulse: Float = 0f,
    var glowT: Float = 0f,
    val stats: PlayerStats = PlayerStats(),
    @kotlinx.serialization.Transient
    var ldX: Float = if (id == 0) 1f else -1f,
    @kotlinx.serialization.Transient
    var ldZ: Float = 0f,
    @kotlinx.serialization.Transient
    var ldInit: Boolean = false
)

@Serializable
data class GameItem(
    val id: Int,
    val type: String,
    val sub: String = "",
    val x: Int,
    val z: Int,
    var age: Float = 0f,
    var life: Float = 0f
)

@Serializable
data class Rock(
    val id: Int,
    val x: Int,
    val z: Int,
    val seed: Int,
    var st: String,
    var t: Float
)

@Serializable
data class Beetle(
    val id: Int,
    var x: Int,
    var z: Int,
    var fx: Int,
    var fz: Int,
    var hx: Int,
    var hz: Int,
    var acc: Float = 0f,
    val iv: Float = 0.4f,
    var stun: Float = 0f,
    var emerge: Float = 1.3f,
    var phase: Float = 0f
)

@Serializable
data class WorldTimers(
    var orb: Float = 9f,
    var crystal: Float = 14f,
    var berry: Float = 16f,
    var beetle: Float = 3f,
    var crumble: Float = 5f
)

@Serializable
data class GameState(
    val v: Int = 2,
    val demo: Boolean,
    val solo: Boolean,
    val W: Int,
    val H: Int,
    val seed: Int,
    val players: List<PlayerState>,
    var started: Boolean = false,
    var cdT: Float = 0f,
    var cdIdx: Int = -1,
    var time: Float = 0f,
    var phase: String = "run",
    var endT: Float = 0f,
    var winner: Int = -2,
    var outHint: Boolean = false,
    var level: Int = 1,
    var applesLv: Int = 0,
    val items: MutableList<GameItem> = mutableListOf(),
    val rocks: MutableList<Rock> = mutableListOf(),
    val beetles: MutableList<Beetle> = mutableListOf(),
    val tiles: IntArray,
    val tileT: FloatArray,
    val timers: WorldTimers,
    var nextId: Int = 1,
    var theme: Int = 0,
    var tipLasso: Boolean = false,
    var tick: Int = 0
)

/* ---------- Visual FX ---------- */
data class Particle(
    val k: String,
    var x: Float, var y: Float, var z: Float,
    var vx: Float, var vy: Float, var vz: Float,
    var life: Float, val max: Float,
    val size: Float, val colorHex: String,
    val g: Float, val drag: Float,
    var rot: Float, val vr: Float
)

data class RingFx(
    val x: Float, val z: Float,
    val r0: Float, val r1: Float,
    var life: Float, val max: Float,
    val colorRgb: FloatArray, val alpha: Float, val w: Float
)

data class GlowFx(val cells: IntArray, var life: Float, val max: Float, val owner: Int)

data class FloatTextFx(
    val x: Float, val y: Float, val z: Float,
    val text: String, val colorHex: String,
    val size: Float, var life: Float, val max: Float
)

data class FlyFx(
    val owner: Int, val colorHex: String,
    val x: Float, val y: Float, val z: Float,
    var t: Float, val dur: Float, val r: Float, val arc: Float
)

data class BulgeFx(var p: Float, val playerIdx: Int)

class HeadPos(
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var fx: Float = 1f, var fz: Float = 0f, var ok: Boolean = false
)

/* ---------- 3D Low-Poly Meshes ---------- */
data class MeshFace(val a: Int, val b: Int, val c: Int, var nx: Float, var ny: Float, var nz: Float, var dd: Float = 0f)
data class PolyMesh(val verts: List<FloatArray>, val faces: List<MeshFace>)

class Mulberry32(private var a: Int) {
    fun next(): Float {
        a = (a + 0x6D2B79F5)
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        val res = (t xor (t ushr 14)).toLong() and 0xFFFFFFFFL
        return res / 4294967296f
    }
}

object MeshFactory {
    private val rockCache = mutableMapOf<Int, PolyMesh>()

    val GEM_MESH: PolyMesh by lazy {
        val v = mutableListOf(floatArrayOf(0f, 0.27f, 0f))
        val k = 6
        for (i in 0 until k) {
            val a = (i.toFloat() / k) * GameConst.TAU
            v += floatArrayOf(cos(a) * 0.22f, 0.03f, sin(a) * 0.22f)
        }
        v += floatArrayOf(0f, -0.32f, 0f)
        val f = mutableListOf<MeshFace>()
        for (i in 0 until k) {
            val a = 1 + i
            val b = 1 + ((i + 1) % k)
            f += MeshFace(0, b, a, 0f, 0f, 0f)
            f += MeshFace(k + 1, a, b, 0f, 0f, 0f)
        }
        finalizeMesh(v, f, floatArrayOf(0f, 0f, 0f))
    }

    fun rockMesh(seed: Int): PolyMesh = rockCache.getOrPut(seed) { makeRockMesh(seed) }

    private fun makeRockMesh(seed: Int): PolyMesh {
        val r = Mulberry32(seed)
        val sides = 6 + (r.next() * 3f).toInt()
        val rings = listOf(
            0f to 0.44f,
            (0.3f + r.next() * 0.07f) to 0.47f,
            (0.58f + r.next() * 0.1f) to (0.31f + r.next() * 0.07f)
        )
        val v = mutableListOf<FloatArray>()
        val a0 = r.next() * GameConst.TAU
        rings.forEachIndexed { ri, (ry, rrBase) ->
            for (k in 0 until sides) {
                val a = a0 + ((k + (r.next() - 0.5f) * 0.35f) / sides) * GameConst.TAU
                val rr = rrBase * (0.8f + r.next() * 0.3f)
                val yOff = if (ri > 0) (r.next() - 0.5f) * 0.08f else 0f
                v += floatArrayOf(cos(a) * rr, ry + yOff, sin(a) * rr)
            }
        }
        val top = v.size
        v += floatArrayOf((r.next() - 0.5f) * 0.12f, 0.78f + r.next() * 0.14f, (r.next() - 0.5f) * 0.12f)
        val f = mutableListOf<MeshFace>()
        for (ri in 0 until 2) {
            for (k in 0 until sides) {
                val a = ri * sides + k
                val b = ri * sides + ((k + 1) % sides)
                val c = (ri + 1) * sides + ((k + 1) % sides)
                val d = (ri + 1) * sides + k
                f += MeshFace(a, b, c, 0f, 0f, 0f)
                f += MeshFace(a, c, d, 0f, 0f, 0f)
            }
        }
        for (k in 0 until sides) {
            f += MeshFace(2 * sides + k, 2 * sides + ((k + 1) % sides), top, 0f, 0f, 0f)
        }
        return finalizeMesh(v, f, floatArrayOf(0f, 0.35f, 0f))
    }

    private fun finalizeMesh(v: List<FloatArray>, f: List<MeshFace>, center: FloatArray): PolyMesh {
        val outFaces = f.map { face ->
            val a = v[face.a]
            val b = v[face.b]
            val c = v[face.c]
            val ux = b[0] - a[0]; val uy = b[1] - a[1]; val uz = b[2] - a[2]
            val vx = c[0] - a[0]; val vy = c[1] - a[1]; val vz = c[2] - a[2]
            var nx = uy * vz - uz * vy
            var ny = uz * vx - ux * vz
            var nz = ux * vy - uy * vx
            val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-5f)
            nx /= len; ny /= len; nz /= len
            val cx = (a[0] + b[0] + c[0]) / 3f - center[0]
            val cy = (a[1] + b[1] + c[1]) / 3f - center[1]
            val cz = (a[2] + b[2] + c[2]) / 3f - center[2]
            if (cx * nx + cy * ny + cz * nz < 0f) {
                MeshFace(face.a, face.c, face.b, -nx, -ny, -nz)
            } else {
                MeshFace(face.a, face.b, face.c, nx, ny, nz)
            }
        }
        return PolyMesh(v, outFaces)
    }
}

data class DecoPebble(val u: Float, val v: Float, val r: Float)
data class DecoDecal(val x: Int, val z: Int, val u: Float, val v: Float, val rot: Float, val k: Float)
data class BoardDeco(
    val variant: IntArray,
    val under: List<FloatArray>,
    val maxDepth: Float,
    val pebbles: List<DecoPebble>,
    val decals: List<DecoDecal>
)

/* ---------- UI / Screen Enums & Snapshots ---------- */
enum class ScreenMode { TITLE, PLAY, PAUSE, OVER }
enum class TitleSubView { MODE, SOLO, MULTI, NET }
enum class PlayMode { SOLO, LOCAL_MULTI, NET }
enum class NetRole { NONE, HOST, GUEST }

data class PlayerHudSnapshot(
    val id: Int = 0,
    val name: String = "P1",
    val colorHex: String = "#22c7b2",
    val score: Int = 0,
    val hearts: Int = 3,
    val isOut: Boolean = false,
    val comboMult: Int = 0,
    val comboFrac: Float = 0f,
    val hops: Int = 3,
    val hopCharge: Float = 0f,
    val blazeFrac: Float = 0f,
    val frostFrac: Float = 0f,
    val ghostFrac: Float = 0f,
    val maxLen: Int = 5,
    val lassos: Int = 0,
    val beetles: Int = 0,
    val cause: String = ""
)

data class NetUiState(
    val role: NetRole = NetRole.NONE,
    val connected: Boolean = false,
    val busy: Boolean = false,
    val hostIps: String = "",
    val joinIp: String = "192.168.43.1",
    val statusText: String = "Pick Host or Join."
)

data class GameUiState(
    val screen: ScreenMode = ScreenMode.TITLE,
    val titleView: TitleSubView = TitleSubView.MODE,
    val modeSel: Int = 0,
    val meIndex: Int = 0,
    val skinP1: Int = 0,
    val skinP2: Int = 1,
    val bestScore: Int = 0,
    val winsP1: Int = 0,
    val winsP2: Int = 0,
    val muted: Boolean = false,
    val isSolo: Boolean = false,
    val level: Int = 1,
    val themeName: String = "Sunny Meadow",
    val applesLv: Int = 0,
    val p1: PlayerHudSnapshot = PlayerHudSnapshot(id = 0, name = "P1"),
    val p2: PlayerHudSnapshot = PlayerHudSnapshot(id = 1, name = "P2"),
    val winner: Int = -2,
    val isNewBest: Boolean = false,
    val bannerTitle: String = "",
    val bannerSub: String = "",
    val bannerBlurb: String = "",
    val showBanner: Boolean = false,
    val countText: String = "",
    val showCount: Boolean = false,
    val countKey: Int = 0,
    val hintText: String = "",
    val showHint: Boolean = false,
    val toastText: String = "",
    val showToast: Boolean = false,
    val net: NetUiState = NetUiState()
)

/* ---------- Network Wire Protocol ---------- */
@Serializable
data class NetFxEvent(
    val op: String,
    val s1: String = "",
    val s2: String = "",
    val s3: String = "",
    val i1: Int = 0,
    val i2: Int = 0,
    val f1: Float = 0f,
    val f2: Float = 0f,
    val f3: Float = 0f,
    val f4: Float = 0f,
    val f5: Float = 0f,
    val f6: Float = 0f,
    val f7: Float = 0f,
    val b1: Boolean = false,
    val cells: IntArray = intArrayOf(),
    val item: GameItem? = null,
    val seg: Segment? = null
)

@Serializable
data class NetWireMsg(
    val t: String,
    val k: Int = 0,
    val x: Int = 0,
    val z: Int = 0,
    val sk: List<Int>? = null,
    val g: GameState? = null,
    val ev: List<NetFxEvent>? = null
)
