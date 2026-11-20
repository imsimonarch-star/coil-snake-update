package com.coilsnake.game.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.coilsnake.game.R
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class GameRenderer(context: Context) {
    private val bungeeTypeface: Typeface =
        ResourcesCompat.getFont(context, R.font.bungee) ?: Typeface.DEFAULT_BOLD

    private val spriteCache = HashMap<String, Bitmap>()
    private val shadowSprite: Bitmap = createShadowSprite()
    private val puffSprite: Bitmap = createPuffSprite()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = bungeeTypeface
        textAlign = Paint.Align.CENTER
    }
    private val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = bungeeTypeface
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val addXfer = PorterDuffXfermode(PorterDuff.Mode.ADD)

    private val p0 = ProjPt()
    private val q1 = ProjPt()
    private val q2 = ProjPt()
    private val q3 = ProjPt()
    private val q4 = ProjPt()
    private val q5 = ProjPt()
    private val q6 = ProjPt()
    private val q7 = ProjPt()
    private val q8 = ProjPt()
    private val t1 = ProjPt()
    private val t2 = ProjPt()
    private val t3 = ProjPt()
    private val t4 = ProjPt()
    private val e1 = ProjPt()
    private val e2 = ProjPt()
    private val e3 = ProjPt()

    private val pathA = Path()
    private val pathB = Path()
    private val topPaths = Array(2) { Array(3) { Path() } }
    private val frontPath = Path()
    private val sidePath = Path()
    private val rectTmp = RectF()

    private val mpx = FloatArray(64)
    private val mpy = FloatArray(64)
    private val mpd = FloatArray(64)
    private val faceBuf = ArrayList<MeshFace>(128)

    private data class Cloud(val x: Float, val y: Float, val s: Float, val v: Float)
    private data class Star(val x: Float, val y: Float, val r: Float, val ph: Float, val v: Float)
    private data class FarIsland(val x: Float, val y: Float, val s: Float, val ph: Float)

    private val clouds = List(7) {
        Cloud(Random.nextFloat(), 0.04f + Random.nextFloat() * 0.36f, 0.7f + Random.nextFloat() * 0.8f, 0.004f + Random.nextFloat() * 0.007f)
    }
    private val stars = List(70) {
        Star(Random.nextFloat(), Random.nextFloat() * 0.8f, 0.6f + Random.nextFloat() * 1.2f, Random.nextFloat() * 6f, 0.002f + Random.nextFloat() * 0.008f)
    }
    private val farIslands = listOf(
        FarIsland(0.07f, 0.66f, 0.9f, 0f),
        FarIsland(0.93f, 0.3f, 0.6f, 2f),
        FarIsland(0.88f, 0.84f, 1.1f, 4f)
    )

    private class DrawItem {
        var kind = 0
        var d = 0f
        var x = 0f
        var y = 0f
        var r = 0f
        var s = 0f
        var a = 1f
        var spr: Bitmap? = null
        var wx = 0f
        var wy = 0f
        var wz = 0f
        var fx = 0f
        var fz = 0f
        var wr = 0f
        var headHex = ""
        var pid = 0
        var dying = false
        var blaze = false
        var look: GameItem? = null
        var item: GameItem? = null
        var rock: Rock? = null
        var beetle: Beetle? = null
        var k = 0f
        var sc = 1f
        var em = 1f
    }

    private val drawList = ArrayList<DrawItem>(512)
    private var drawCount = 0

    private class ShadowCall(var x: Float = 0f, var y: Float = 0f, var rx: Float = 0f, var ry: Float = 0f, var a: Float = 1f)
    private val shadowCalls = ArrayList<ShadowCall>(512)
    private var shadowCount = 0

    private val sxBuf = FloatArray(256)
    private val szBuf = FloatArray(256)
    private val shBuf = FloatArray(256)

    private val lightVec = run {
        val v = floatArrayOf(-0.5f, 0.8f, -0.35f)
        val l = hypot(hypot(v[0], v[1]), v[2])
        floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
    }

    private val cracks = arrayOf(
        arrayOf(0.5f to 0.5f, 0.3f to 0.38f, 0.12f to 0.3f),
        arrayOf(0.5f to 0.5f, 0.68f to 0.36f, 0.86f to 0.22f),
        arrayOf(0.5f to 0.5f, 0.42f to 0.7f, 0.3f to 0.9f),
        arrayOf(0.5f to 0.5f, 0.72f to 0.62f, 0.9f to 0.78f),
        arrayOf(0.3f to 0.38f, 0.36f to 0.18f)
    )

    private fun nextDrawItem(): DrawItem {
        if (drawCount >= drawList.size) drawList += DrawItem()
        return drawList[drawCount++]
    }

    private fun pushShadow(x: Float, y: Float, rx: Float, ry: Float, a: Float) {
        if (shadowCount >= shadowCalls.size) shadowCalls += ShadowCall()
        val sc = shadowCalls[shadowCount++]
        sc.x = x; sc.y = y; sc.rx = rx; sc.ry = ry; sc.a = a
    }

    fun render(canvas: Canvas, eng: GameEngine, width: Float, height: Float) {
        val g = eng.state ?: return
        val deco = eng.deco ?: return

        eng.cam.pose(eng.lookX, eng.lookZ)
        eng.cam.focal = eng.fitFocal * (1f + eng.zoomKick)
        eng.cam.cx = eng.fitCx + eng.shakeX
        eng.cam.cy = eng.fitCy + eng.shakeY

        val th = eng.blendedTheme
        drawSky(canvas, eng, th, g, width, height)
        drawUnderside(canvas, eng, th, g, deco)
        drawSlab(canvas, eng, th, g, deco)
        drawTiles(canvas, eng, th, g, deco)
        drawDecals(canvas, eng, th, g, deco)
        drawBoardLight(canvas, eng, th, g)
        drawSpecialTiles(canvas, eng, th, g)
        drawFloorFx(canvas, eng, g)

        drawCount = 0
        shadowCount = 0
        for (p in g.players) buildSnake(eng, g, p, eng.snakeT(p))
        buildItems(eng, g)
        buildRocks(eng, g)
        buildBeetles(eng, g)

        if (shadowCount > 0) {
            val saved = canvas.saveLayerAlpha(0f, 0f, width, height, (th.shadow * 255f).toInt().coerceIn(0, 255))
            for (i in 0 until shadowCount) {
                val sc = shadowCalls[i]
                paint.alpha = (sc.a * 255f).toInt().coerceIn(0, 255)
                rectTmp.set(sc.x - sc.rx, sc.y - sc.ry, sc.x + sc.rx, sc.y + sc.ry)
                canvas.drawBitmap(shadowSprite, null, rectTmp, paint)
            }
            paint.alpha = 255
            canvas.restoreToCount(saved)
        }

        drawDepthSorted(canvas, eng, th)
        drawLassoGlow(canvas, eng, g)
        drawFly(canvas, eng)
        drawParticles(canvas, eng)
        drawTexts(canvas, eng)
        drawScreenFx(canvas, eng, th, width, height)
    }

    /* ---------- Color Helpers ---------- */
    private fun argb(c: FloatArray, a: Float = 1f): Int {
        val alpha = (a.coerceIn(0f, 1f) * 255f).toInt()
        val r = c[0].toInt().coerceIn(0, 255)
        val g = c[1].toInt().coerceIn(0, 255)
        val b = c[2].toInt().coerceIn(0, 255)
        return Color.argb(alpha, r, g, b)
    }

    private fun shade(c: FloatArray, f: Float): FloatArray {
        return if (f >= 0f) {
            floatArrayOf(c[0] + (255f - c[0]) * f, c[1] + (255f - c[1]) * f, c[2] + (255f - c[2]) * f)
        } else {
            val k = -f
            floatArrayOf(c[0] * (1f - k), c[1] * (1f - k), c[2] * (1f - k))
        }
    }

    private fun mix(a: FloatArray, b: FloatArray, t: Float) = floatArrayOf(
        a[0] + (b[0] - a[0]) * t,
        a[1] + (b[1] - a[1]) * t,
        a[2] + (b[2] - a[2]) * t
    )

    private fun parseHex(hex: String, a: Float = 1f): Int {
        val rgb = hexToRgb(hex)
        return argb(rgb, a)
    }

    /* ---------- Sprites ---------- */
    private fun sphereSprite(hex: String): Bitmap {
        return spriteCache.getOrPut("s$hex") {
            val sz = 128
            val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val base = hexToRgb(hex)
            val r = sz / 2f - 1f
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = RadialGradient(
                sz * 0.36f, sz * 0.32f, r,
                intArrayOf(
                    argb(shade(base, 0.55f)),
                    argb(shade(base, 0.16f)),
                    argb(base),
                    argb(shade(base, -0.3f)),
                    argb(shade(base, -0.5f))
                ),
                floatArrayOf(0f, 0.28f, 0.62f, 0.9f, 1f),
                Shader.TileMode.CLAMP
            )
            c.drawCircle(sz / 2f, sz / 2f, r, p)
            p.shader = RadialGradient(
                sz * 0.62f, sz * 0.86f, sz * 0.42f,
                intArrayOf(Color.argb(40, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            c.drawCircle(sz / 2f, sz / 2f, r, p)
            p.shader = RadialGradient(
                sz * 0.35f, sz * 0.3f, sz * 0.17f,
                intArrayOf(Color.argb(217, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            c.drawCircle(sz / 2f, sz / 2f, r, p)
            bmp
        }
    }

    private fun glowSprite(hex: String): Bitmap {
        return spriteCache.getOrPut("g$hex") {
            val sz = 64
            val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val base = hexToRgb(hex)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = RadialGradient(
                sz / 2f, sz / 2f, sz / 2f,
                intArrayOf(argb(shade(base, 0.6f)), argb(base, 0.75f), argb(base, 0f)),
                floatArrayOf(0f, 0.25f, 1f),
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, sz.toFloat(), sz.toFloat(), p)
            bmp
        }
    }

    private fun createShadowSprite(): Bitmap {
        val sz = 64
        val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            sz / 2f, sz / 2f, sz / 2f,
            intArrayOf(Color.BLACK, Color.argb(216, 0, 0, 0), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, sz.toFloat(), sz.toFloat(), p)
        return bmp
    }

    private fun createPuffSprite(): Bitmap {
        val sz = 64
        val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            sz / 2f, sz / 2f, sz / 2f,
            intArrayOf(Color.argb(242, 255, 255, 255), Color.argb(153, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, sz.toFloat(), sz.toFloat(), p)
        return bmp
    }

    private fun drawSprite(canvas: Canvas, spr: Bitmap, x: Float, y: Float, r: Float, a: Float) {
        if (r <= 0.2f) return
        paint.alpha = (a.coerceIn(0f, 1f) * 255f).toInt()
        rectTmp.set(x - r, y - r, x + r, y + r)
        canvas.drawBitmap(spr, null, rectTmp, paint)
        paint.alpha = 255
    }

    private fun drawGlowAt(canvas: Canvas, hex: String, x: Float, y: Float, r: Float, a: Float) {
        if (r <= 0.2f || a <= 0f) return
        paint.xfermode = addXfer
        paint.alpha = (a.coerceIn(0f, 1f) * 255f).toInt()
        rectTmp.set(x - r, y - r, x + r, y + r)
        canvas.drawBitmap(glowSprite(hex), null, rectTmp, paint)
        paint.alpha = 255
        paint.xfermode = null
    }

    /* ---------- World Layers ---------- */
    private fun drawSky(canvas: Canvas, eng: GameEngine, th: BlendedTheme, g: GameState, vw: Float, vh: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, 0f, 0f, vh, argb(th.skyTop), argb(th.skyBot), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, vw, vh, paint)
        val bigR = max(vw, vh) * 0.55f
        paint.shader = RadialGradient(
            vw * 0.18f, vh * 0.06f, bigR,
            argb(shade(th.skyBot, 0.5f), 0.5f), argb(th.skyBot, 0f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, vw, vh, paint)
        paint.shader = null

        val light = 1f - th.dark
        if (light > 0.02f) {
            paint.alpha = (light * 0.75f * 255f).toInt()
            for (c in clouds) {
                val x = (((c.x + eng.clock * c.v) % 1.3f) - 0.15f) * vw - (eng.lookX - g.W / 2f) * 4f
                val y = c.y * vh
                val w = vw * 0.08f * c.s
                rectTmp.set(x - w, y - w * 0.45f, x + w, y + w * 0.45f)
                canvas.drawBitmap(puffSprite, null, rectTmp, paint)
                rectTmp.set(x - w * 0.2f, y - w * 0.7f, x + w * 1.1f, y + w * 0.3f)
                canvas.drawBitmap(puffSprite, null, rectTmp, paint)
                rectTmp.set(x - w * 1.2f, y - w * 0.3f, x - w * 0.1f, y + w * 0.3f)
                canvas.drawBitmap(puffSprite, null, rectTmp, paint)
            }
            paint.alpha = 255
        }
        if (th.dark > 0.02f) {
            val starRgb = shade(th.cloud, 0.35f)
            for (s in stars) {
                val tw = 0.5f + 0.5f * sin(eng.clock * 2f + s.ph)
                paint.color = argb(starRgb, th.dark * (0.25f + 0.6f * tw))
                val y = ((((s.y - eng.clock * s.v) % 0.8f) + 0.8f) % 0.8f) * vh
                canvas.drawCircle(s.x * vw, y, s.r * eng.density * 0.55f, paint)
            }
        }
        val base = min(vw, vh) * 0.1f
        for (f in farIslands) {
            val w = base * f.s
            val x = f.x * vw - (eng.lookX - g.W / 2f) * 3f * f.s
            val y = f.y * vh + sin(eng.clock * 0.6f + f.ph) * 4f * eng.density
            paint.color = argb(mix(th.dirtBot, th.skyBot, 0.55f))
            pathA.reset()
            pathA.moveTo(x - w, y)
            pathA.lineTo(x - w * 0.55f, y + w * 0.45f)
            pathA.lineTo(x - w * 0.2f, y + w * 0.85f)
            pathA.lineTo(x + w * 0.1f, y + w * 0.55f)
            pathA.lineTo(x + w * 0.4f, y + w * 1.0f)
            pathA.lineTo(x + w * 0.7f, y + w * 0.4f)
            pathA.lineTo(x + w, y)
            pathA.close()
            canvas.drawPath(pathA, paint)

            paint.color = argb(mix(th.dirtTop, th.skyBot, 0.5f))
            rectTmp.set(x - w, y - w * 0.3f, x + w, y + w * 0.3f)
            canvas.drawArc(rectTmp, 0f, 180f, true, paint)

            paint.color = argb(mix(th.tileA, th.skyBot, 0.45f))
            rectTmp.set(x - w, y - w * 0.33f, x + w, y + w * 0.21f)
            canvas.drawOval(rectTmp, paint)
        }
    }

    private fun drawUnderside(canvas: Canvas, eng: GameEngine, th: BlendedTheme, g: GameState, deco: BoardDeco) {
        val w = g.W.toFloat()
        val h = g.H.toFloat()
        eng.proj(w / 2f, -GameConst.SLAB_T, h, p0)
        val topY = p0.y
        var botY = topY

        pathA.reset()
        eng.proj(0f, -GameConst.SLAB_T + 0.02f, h, p0)
        pathA.moveTo(p0.x, p0.y)
        for (pt in deco.under) {
            eng.proj(pt[0], pt[1], pt[2], p0)
            pathA.lineTo(p0.x, p0.y)
            if (p0.y > botY) botY = p0.y
        }
        eng.proj(w, -GameConst.SLAB_T + 0.02f, h, p0)
        pathA.lineTo(p0.x, p0.y)
        pathA.close()

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, topY, 0f, botY + 1f,
            intArrayOf(argb(shade(th.dirtTop, -0.18f)), argb(th.dirtBot), argb(mix(th.dirtBot, th.hole, 0.5f))),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(pathA, paint)
        paint.shader = null

        strokePaint.color = argb(shade(th.dirtBot, -0.3f), 0.55f)
        strokePaint.strokeWidth = 1.5f * eng.density
        for (band in floatArrayOf(0.33f, 0.66f)) {
            pathA.reset()
            deco.under.forEachIndexed { idx, pt ->
                eng.proj(pt[0], eng.lerp(-GameConst.SLAB_T, pt[1], band), eng.lerp(h, pt[2], band), p0)
                if (idx == 0) pathA.moveTo(p0.x, p0.y) else pathA.lineTo(p0.x, p0.y)
            }
            canvas.drawPath(pathA, strokePaint)
        }
        if (th.glow > 0.03f) {
            for (i in 2 until deco.under.size step 5) {
                val pt = deco.under[i]
                eng.proj(pt[0], pt[1] + 0.15f, pt[2], p0)
                drawGlowAt(canvas, th.glowHex, p0.x, p0.y, 0.5f * p0.s, 0.35f * th.glow * (0.7f + 0.3f * sin(eng.clock * 2f + i)))
            }
        }
    }

    private fun quad(path: Path, a: ProjPt, b: ProjPt, c: ProjPt, d: ProjPt, reset: Boolean = true) {
        if (reset) path.reset()
        path.moveTo(a.x, a.y)
        path.lineTo(b.x, b.y)
        path.lineTo(c.x, c.y)
        path.lineTo(d.x, d.y)
        path.close()
    }

    private fun drawSlab(canvas: Canvas, eng: GameEngine, th: BlendedTheme, g: GameState, deco: BoardDeco) {
        val w = g.W.toFloat()
        val h = g.H.toFloat()
        eng.proj(0f, -GameConst.TILE_H, h, q1)
        eng.proj(w, -GameConst.TILE_H, h, q2)
        eng.proj(w, -GameConst.SLAB_T, h, q3)
        eng.proj(0f, -GameConst.SLAB_T, h, q4)
        quad(pathA, q1, q2, q3, q4)
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, q1.y, 0f, q4.y,
            intArrayOf(argb(shade(th.lip, 0.08f)), argb(th.lip), argb(th.dirtTop), argb(shade(th.dirtTop, -0.3f))),
            floatArrayOf(0f, 0.17f, 0.2f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(pathA, paint)
        paint.shader = null

        paint.color = argb(shade(th.dirtTop, -0.24f), 0.8f)
        for (pb in deco.pebbles) {
            eng.proj(pb.u, eng.lerp(-GameConst.TILE_H - 0.22f, -GameConst.SLAB_T + 0.07f, pb.v), h, p0)
            val rx = pb.r * p0.s
            val ry = pb.r * p0.s * 0.7f
            rectTmp.set(p0.x - rx, p0.y - ry, p0.x + rx, p0.y + ry)
            canvas.drawOval(rectTmp, paint)
        }

        strokePaint.color = argb(shade(th.lip, 0.35f), 0.8f)
        strokePaint.strokeWidth = 1.2f * eng.density
        canvas.drawLine(q1.x, q1.y + 0.5f, q2.x, q2.y + 0.5f, strokePaint)

        eng.proj(0f, -GameConst.TILE_H, 0f, q5)
        eng.proj(w, -GameConst.TILE_H, 0f, q6)
        quad(pathA, q5, q6, q2, q1)
        val pulse = if (th.glow > 0f) 0.1f * sin(eng.clock * 2.2f) * th.glow else 0f
        paint.color = argb(shade(th.grout, pulse))
        canvas.drawPath(pathA, paint)
    }

    private fun drawTiles(canvas: Canvas, eng: GameEngine, th: BlendedTheme, g: GameState, deco: BoardDeco) {
        val w = g.W
        val h = g.H
        val gap = GameConst.TILE_GAP
        for (k in 0..1) for (v in 0..2) topPaths[k][v].reset()
        frontPath.reset()
        sidePath.reset()

        for (j in 0 until h) {
            for (i in 0 until w) {
                val c = j * w + i
                if (g.tiles[c] != GameConst.T_OK) continue
                val x0 = i + gap
                val x1 = i + 1 - gap
                val z0 = j + gap
                val z1 = j + 1 - gap
                eng.proj(x0, 0f, z0, q1)
                eng.proj(x1, 0f, z0, q2)
                eng.proj(x1, 0f, z1, q3)
                eng.proj(x0, 0f, z1, q4)
                quad(topPaths[(i + j) and 1][deco.variant[c]], q1, q2, q3, q4, reset = false)

                eng.proj(x1, -GameConst.TILE_H, z1, q5)
                eng.proj(x0, -GameConst.TILE_H, z1, q6)
                quad(frontPath, q4, q3, q5, q6, reset = false)

                if (x1 < eng.cam.px) {
                    eng.proj(x1, -GameConst.TILE_H, z0, q7)
                    quad(sidePath, q2, q3, q5, q7, reset = false)
                } else if (x0 > eng.cam.px) {
                    eng.proj(x0, -GameConst.TILE_H, z0, q7)
                    quad(sidePath, q1, q4, q6, q7, reset = false)
                }
            }
        }
        paint.style = Paint.Style.FILL
        paint.color = argb(shade(th.tileMid, -0.38f))
        canvas.drawPath(sidePath, paint)
        paint.color = argb(shade(th.tileMid, -0.26f))
        canvas.drawPath(frontPath, paint)
        val vars = floatArrayOf(0f, 0.045f, -0.04f)
        for (k in 0..1) {
            val base = if (k == 1) th.tileB else th.tileA
            for (v in 0..2) {
                paint.color = argb(shade(base, vars[v]))
                canvas.drawPath(topPaths[k][v], paint)
            }
        }
    }

    private fun drawDecals(canvas: Canvas, eng: GameEngine, th: BlendedTheme, g: GameState, deco: BoardDeco) {
        val kind = th.decal
        val sp = eng.cam.sinP
        for (d in deco.decals) {
            if (g.tiles[d.z * g.W + d.x] != GameConst.T_OK) continue
            eng.proj(d.x + d.u, 0.002f, d.z + d.v, p0)
            val s = p0.s
            val x = p0.x
            val y = p0.y
            when (kind) {
                "flower" -> {
                    if (d.k < 0.5f) {
                        paint.color = if (d.k < 0.25f) Color.WHITE else Color.rgb(255, 209, 232)
                        for (p in 0 until 5) {
                            val a = d.rot + (p * GameConst.TAU) / 5f
                            val px = x + cos(a) * 0.055f * s
                            val py = y + sin(a) * 0.055f * s * sp
                            rectTmp.set(px - 0.04f * s, py - 0.04f * s * sp, px + 0.04f * s, py + 0.04f * s * sp)
                            canvas.drawOval(rectTmp, paint)
                        }
                        paint.color = Color.rgb(255, 201, 58)
                        rectTmp.set(x - 0.03f * s, y - 0.03f * s * sp, x + 0.03f * s, y + 0.03f * s * sp)
                        canvas.drawOval(rectTmp, paint)
                    } else {
                        strokePaint.color = argb(shade(th.tileA, -0.32f))
                        strokePaint.strokeWidth = max(1f, 0.025f * s)
                        strokePaint.strokeCap = Paint.Cap.ROUND
                        for (p in -1..1) {
                            canvas.drawLine(x + p * 0.04f * s, y, x + p * 0.075f * s, y - (0.12f - kotlin.math.abs(p) * 0.035f) * s, strokePaint)
                        }
                    }
                }
                "pebble" -> {
                    paint.color = argb(shade(th.tileB, -0.22f))
                    canvas.save()
                    canvas.rotate(Math.toDegrees(d.rot.toDouble()).toFloat(), x, y)
                    rectTmp.set(x - 0.06f * s, y - 0.045f * s * sp, x + 0.06f * s, y + 0.045f * s * sp)
                    canvas.drawOval(rectTmp, paint)
                    canvas.restore()
                }
                "sparkle" -> {
                    val tw = 0.5f + 0.5f * sin(eng.clock * 3f + d.rot * 4f)
                    strokePaint.color = Color.argb(((0.35f + 0.6f * tw) * 255).toInt(), 255, 255, 255)
                    strokePaint.strokeWidth = max(1f, 0.018f * s)
                    val l = (0.05f + 0.05f * tw) * s
                    canvas.drawLine(x - l, y, x + l, y, strokePaint)
                    canvas.drawLine(x, y - l * sp, x, y + l * sp, strokePaint)
                }
                "crack" -> {
                    if (d.k > 0.6f) continue
                    strokePaint.color = argb(th.grout, 0.55f + 0.25f * sin(eng.clock * 2f + d.rot))
                    strokePaint.strokeWidth = max(1f, 0.02f * s)
                    strokePaint.strokeCap = Paint.Cap.ROUND
                    pathA.reset()
                    pathA.moveTo(x - 0.12f * s, y - 0.03f * s)
                    pathA.lineTo(x - 0.02f * s, y + 0.02f * s)
                    pathA.lineTo(x + 0.04f * s, y - 0.04f * s)
                    pathA.lineTo(x + 0.13f * s, y + 0.01f * s)
                    canvas.drawPath(pathA, strokePaint)
                }
                "dot" -> {
                    if (d.k > 0.5f) continue
                    drawGlowAt(canvas, "#22e4ff", x, y, 0.09f * s, 0.4f + 0.3f * sin(eng.clock * 2.5f + d.rot * 3f))
                }
            }
        }
    }

    private fun drawBoardLight(canvas: Canvas, eng: GameEngine, th: BlendedTheme, g: GameState) {
        val w = g.W.toFloat()
        val h = g.H.toFloat()
        eng.proj(0f, 0f, 0f, q1)
        eng.proj(w, 0f, 0f, q2)
        eng.proj(w, 0f, h, q3)
        eng.proj(0f, 0f, h, q4)

        canvas.save()
        quad(pathA, q1, q2, q3, q4)
        canvas.clipPath(pathA)

        val bx = min(q1.x, q4.x) - 4f
        val bw = max(q2.x, q3.x) - bx + 8f
        val by = q1.y - 4f
        val bh = q4.y - q1.y + 8f

        paint.shader = LinearGradient(0f, q1.y, 0f, eng.lerp(q1.y, q4.y, 0.6f), argb(th.fog, 0.3f), argb(th.fog, 0f), Shader.TileMode.CLAMP)
        canvas.drawRect(bx, by, bx + bw, by + bh, paint)

        val lx = eng.lerp(q1.x, q2.x, 0.2f)
        val ly = eng.lerp(q1.y, q4.y, 0.25f)
        paint.shader = RadialGradient(
            lx, ly, max(10f, (q3.x - q4.x) * 0.75f),
            Color.argb((0.14f * (1f - th.dark * 0.6f) * 255).toInt(), 255, 255, 255),
            Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(bx, by, bx + bw, by + bh, paint)

        paint.shader = LinearGradient(
            0f, eng.lerp(q1.y, q4.y, 0.65f), 0f, q4.y,
            Color.TRANSPARENT, Color.argb(31, 10, 6, 30), Shader.TileMode.CLAMP
        )
        canvas.drawRect(bx, by, bx + bw, by + bh, paint)
        paint.shader = null

        val frost = eng.maxPower("frost")
        if (frost > 0f) {
            paint.color = Color.argb((0.2f * min(1f, frost) * 255).toInt(), 200, 235, 255)
            canvas.drawRect(bx, by, bx + bw, by + bh, paint)
        }
        canvas.restore()
    }

    private fun tileBlock(canvas: Canvas, eng: GameEngine, i: Int, j: Int, yOff: Float, col: FloatArray, dark: Float, jx: Float, jz: Float, th: Float) {
        val g = GameConst.TILE_GAP
        val x0 = i + g + jx
        val x1 = i + 1f - g + jx
        val z0 = j + g + jz
        val z1 = j + 1f - g + jz
        eng.proj(x0, yOff, z0, t1)
        eng.proj(x1, yOff, z0, t2)
        eng.proj(x1, yOff, z1, t3)
        eng.proj(x0, yOff, z1, t4)
        eng.proj(x1, yOff - th, z1, q5)
        eng.proj(x0, yOff - th, z1, q6)

        paint.style = Paint.Style.FILL
        paint.color = argb(shade(col, -0.28f - dark * 0.55f))
        quad(pathA, t4, t3, q5, q6)
        canvas.drawPath(pathA, paint)

        if (x1 < eng.cam.px) {
            eng.proj(x1, yOff - th, z0, q7)
            paint.color = argb(shade(col, -0.4f - dark * 0.5f))
            quad(pathA, t2, t3, q5, q7)
            canvas.drawPath(pathA, paint)
        } else if (x0 > eng.cam.px) {
            eng.proj(x0, yOff - th, z0, q7)
            paint.color = argb(shade(col, -0.4f - dark * 0.5f))
            quad(pathA, t1, t4, q6, q7)
            canvas.drawPath(pathA, paint)
        }
        paint.color = argb(shade(col, -dark * 0.65f))
        quad(pathA, t1, t2, t3, t4)
        canvas.drawPath(pathA, paint)
    }

    private fun drawSpecialTiles(canvas: Canvas, eng: GameEngine, th: BlendedTheme, g: GameState) {
        for (j in 0 until g.H) {
            for (i in 0 until g.W) {
                val c = j * g.W + i
                val st = g.tiles[c]
                if (st == GameConst.T_OK) continue
                val col = if (((i + j) and 1) != 0) th.tileB else th.tileA
                if (st == GameConst.T_SHAKE) {
                    val k = (1f - g.tileT[c] / GameConst.SHAKE_TIME).coerceIn(0f, 1f)
                    val amp = 0.012f + k * 0.03f
                    val jx = (Random.nextFloat() - 0.5f) * amp * 2f
                    val jz = (Random.nextFloat() - 0.5f) * amp * 2f
                    val warm = mix(col, floatArrayOf(255f, 90f, 80f), 0.12f + 0.18f * (0.5f + 0.5f * sin(eng.clock * 18f)))
                    tileBlock(canvas, eng, i, j, 0f, warm, 0f, jx, jz, GameConst.TILE_H)
                    strokePaint.color = argb(shade(col, -0.6f), 0.85f)
                    strokePaint.strokeWidth = 1.4f * eng.density
                    strokePaint.strokeCap = Paint.Cap.ROUND
                    val n = ceil(k * cracks.size).toInt().coerceAtMost(cracks.size)
                    for (q in 0 until n) {
                        val cr = cracks[q]
                        pathA.reset()
                        cr.forEachIndexed { m, pt ->
                            eng.proj(i + pt.first + jx, 0.003f, j + pt.second + jz, p0)
                            if (m == 0) pathA.moveTo(p0.x, p0.y) else pathA.lineTo(p0.x, p0.y)
                        }
                        canvas.drawPath(pathA, strokePaint)
                    }
                } else {
                    drawHole(canvas, eng, th, i, j, st, g.tileT[c], col)
                }
            }
        }
    }

    private fun drawHole(canvas: Canvas, eng: GameEngine, th: BlendedTheme, i: Int, j: Int, st: Int, tLeft: Float, col: FloatArray) {
        eng.proj(i.toFloat(), 0f, j.toFloat(), q1)
        eng.proj((i + 1).toFloat(), 0f, j.toFloat(), q2)
        eng.proj((i + 1).toFloat(), 0f, (j + 1).toFloat(), q3)
        eng.proj(i.toFloat(), 0f, (j + 1).toFloat(), q4)
        canvas.save()
        quad(pathA, q1, q2, q3, q4)
        paint.style = Paint.Style.FILL
        paint.color = argb(th.hole)
        canvas.drawPath(pathA, paint)
        canvas.clipPath(pathA)

        val depth = 1.8f
        eng.proj((i + 1).toFloat(), -depth, j.toFloat(), q5)
        eng.proj(i.toFloat(), -depth, j.toFloat(), q6)
        paint.shader = LinearGradient(0f, q1.y, 0f, q6.y, argb(shade(th.dirtTop, -0.05f)), argb(th.hole), Shader.TileMode.CLAMP)
        quad(pathA, q1, q2, q5, q6)
        canvas.drawPath(pathA, paint)

        if (eng.cam.px > i) {
            eng.proj(i.toFloat(), -depth, (j + 1).toFloat(), q7)
            paint.shader = LinearGradient(0f, q1.y, 0f, q7.y, argb(shade(th.dirtTop, -0.25f)), argb(th.hole), Shader.TileMode.CLAMP)
            quad(pathA, q1, q4, q7, q6)
            canvas.drawPath(pathA, paint)
        }
        if (eng.cam.px < i + 1) {
            eng.proj((i + 1).toFloat(), -depth, (j + 1).toFloat(), q8)
            paint.shader = LinearGradient(0f, q2.y, 0f, q8.y, argb(shade(th.dirtTop, -0.35f)), argb(th.hole), Shader.TileMode.CLAMP)
            quad(pathA, q2, q3, q8, q5)
            canvas.drawPath(pathA, paint)
        }
        paint.shader = null
        paint.color = argb(th.lip, 0.9f)
        eng.proj(i.toFloat(), -0.1f, j.toFloat(), q5)
        eng.proj((i + 1).toFloat(), -0.1f, j.toFloat(), q6)
        quad(pathA, q1, q2, q6, q5)
        canvas.drawPath(pathA, paint)

        if (st == GameConst.T_FALL) {
            val k = (1f - tLeft / GameConst.FALL_TIME).coerceIn(0f, 1f)
            tileBlock(canvas, eng, i, j, -2.4f * k * k, col, k, 0f, 0f, 0.3f)
        } else if (st == GameConst.T_RISE) {
            val k = (1f - tLeft / GameConst.RISE_TIME).coerceIn(0f, 1f)
            tileBlock(canvas, eng, i, j, -1.4f * (1f - eng.easeOut(k)), col, 1f - k, 0f, 0f, 0.3f)
        }
        canvas.restore()
    }

    private fun floorOval(eng: GameEngine, x: Float, z: Float, r: Float): RectF {
        eng.proj(x, 0.004f, z, p0)
        val rx = max(0.1f, r * p0.s)
        val ry = max(0.1f, r * p0.s * eng.cam.sinP)
        rectTmp.set(p0.x - rx, p0.y - ry, p0.x + rx, p0.y + ry)
        return rectTmp
    }

    private fun drawFloorFx(canvas: Canvas, eng: GameEngine, g: GameState) {
        for (r in g.rocks) {
            if (r.st != "warn") continue
            val k = (1f - r.t / 1.5f).coerceIn(0f, 1f)
            val rect = floorOval(eng, r.x + 0.5f, r.z + 0.5f, 0.42f)
            strokePaint.color = Color.argb(((0.35f + 0.35f * sin(eng.clock * 14f)) * 255).toInt(), 255, 90, 90)
            strokePaint.strokeWidth = max(1f, 0.05f * p0.s)
            canvas.drawOval(rect, strokePaint)

            strokePaint.color = Color.argb(153, 40, 20, 20)
            strokePaint.strokeWidth = 1.4f * eng.density
            val n = ceil(k * cracks.size).toInt().coerceAtMost(cracks.size)
            for (q in 0 until n) {
                val cr = cracks[q]
                pathA.reset()
                cr.forEachIndexed { m, pt ->
                    eng.proj(r.x + pt.first, 0.004f, r.z + pt.second, p0)
                    if (m == 0) pathA.moveTo(p0.x, p0.y) else pathA.lineTo(p0.x, p0.y)
                }
                canvas.drawPath(pathA, strokePaint)
            }
        }
        for (b in g.beetles) {
            if (b.emerge <= 0f) continue
            val k = 1f - b.emerge / 1.3f
            val rect = floorOval(eng, b.x + 0.5f, b.z + 0.5f, 0.18f + 0.22f * min(1f, k * 2f))
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(217, 96, 62, 36)
            canvas.drawOval(rect, paint)
        }
        for (it in g.items) {
            if (it.age < 0f) continue
            if (it.type == "orb") {
                val rect = floorOval(eng, it.x + 0.5f, it.z + 0.5f, 0.42f)
                strokePaint.color = parseHex(GameConst.ORB_COLORS[it.sub] ?: "#ffffff", 0.8f)
                strokePaint.strokeWidth = 2f * eng.density
                strokePaint.pathEffect = DashPathEffect(floatArrayOf(4f * eng.density, 5f * eng.density), -eng.clock * 20f * eng.density)
                canvas.drawOval(rect, strokePaint)
                strokePaint.pathEffect = null
            }
            if (it.life > 0f && it.type != "berry") {
                val frac = (1f - it.age / it.life).coerceIn(0f, 1f)
                val rect = floorOval(eng, it.x + 0.5f, it.z + 0.5f, 0.34f)
                strokePaint.color = Color.argb(178, 255, 255, 255)
                strokePaint.strokeWidth = max(1.5f, 0.04f * p0.s)
                strokePaint.strokeCap = Paint.Cap.ROUND
                canvas.drawArc(rect, -90f, frac * 360f, false, strokePaint)
            }
        }
        for (rg in eng.rings) {
            val k = 1f - rg.life / rg.max
            val rect = floorOval(eng, rg.x, rg.z, eng.lerp(rg.r0, rg.r1, eng.easeOut(k)))
            strokePaint.color = argb(rg.colorRgb, rg.alpha * (1f - k))
            strokePaint.strokeWidth = max(1f, rg.w * p0.s)
            canvas.drawOval(rect, strokePaint)
        }
        if (eng.glows.isNotEmpty()) {
            paint.style = Paint.Style.FILL
            paint.xfermode = addXfer
            for (gl in eng.glows) {
                val a = gl.life / gl.max
                paint.color = Color.argb((0.5f * a * 255).toInt(), 255, 196, 70)
                for (c in gl.cells) {
                    val i = c % g.W
                    val j = c / g.W
                    eng.proj(i.toFloat(), 0.002f, j.toFloat(), q1)
                    eng.proj((i + 1).toFloat(), 0.002f, j.toFloat(), q2)
                    eng.proj((i + 1).toFloat(), 0.002f, (j + 1).toFloat(), q3)
                    eng.proj(i.toFloat(), 0.002f, (j + 1).toFloat(), q4)
                    quad(pathA, q1, q2, q3, q4)
                    canvas.drawPath(pathA, paint)
                }
            }
            paint.xfermode = null
        }
    }

    private fun addShadow(eng: GameEngine, g: GameState, x: Float, z: Float, h: Float, r: Float, k: Float = 1f) {
        val sx = x + 0.1f + h * 0.3f
        val sz = z + 0.12f + h * 0.22f
        val cx = floor(sx).toInt()
        val cz = floor(sz).toInt()
        if (cx < 0 || cz < 0 || cx >= g.W || cz >= g.H) return
        if (g.tiles[cz * g.W + cx] >= GameConst.T_FALL) return
        eng.proj(sx, 0f, sz, q8)
        val rr = r * (1f + h * 0.15f) * q8.s * 1.25f
        if (rr <= 0.3f) return
        pushShadow(q8.x, q8.y, rr, rr * eng.cam.sinP, ((1f - h * 0.4f) * k).coerceIn(0.12f, 1f))
    }

    /* ---------- Entity Builders ---------- */
    private fun bulgeAt(eng: GameEngine, pid: Int, idx: Float): Float {
        var m = 1f
        for (b in eng.bulges) {
            if (b.playerIdx != pid) continue
            val d = idx - b.p
            m += 0.26f * exp(-(d * d) / 0.7f)
        }
        return m
    }

    private fun buildSnake(eng: GameEngine, g: GameState, p: PlayerState, t: Float) {
        val sn = p.snake
        val pv = p.prev
        val n = sn.size
        val headOut = eng.heads[p.id]
        val bs = eng.bodyScreen[p.id]
        headOut.ok = false
        bs.clear()
        if (n == 0 || p.st == "dead") return

        val dying = p.st == "dying"
        val lead = when {
            p.st == "run" -> if (g.started && g.phase == "run") GameConst.LEAD else 0f
            dying -> GameConst.LEAD * (1f - p.deathT / 0.12f).coerceIn(0f, 1f)
            else -> 0f
        }
        val u = (if (p.st == "run") t else 1f) + lead
        var nd = p.dir
        if (p.queue.isNotEmpty()) {
            val q = p.queue[0]
            if (!(q.x == -p.dir.x && q.z == -p.dir.z)) nd = q
        }
        val nh = if (p.airSteps > 0) GameConst.JUMP_ARC[GameConst.JUMP_STEPS - p.airSteps] else 0f
        if (!p.ldInit) {
            p.ldX = nd.x.toFloat()
            p.ldZ = nd.z.toFloat()
            p.ldInit = true
        } else {
            val kk = 1f - exp(-0.016f * 45f)
            p.ldX += (nd.x - p.ldX) * kk
            p.ldZ += (nd.z - p.ldZ) * kk
        }

        for (i in 0 until n) {
            val to = sn[i]
            val fr = if (pv.isNotEmpty()) pv[min(i, pv.lastIndex)] else to
            if (u <= 1f) {
                sxBuf[i] = eng.lerp(fr.x.toFloat(), to.x.toFloat(), u) + 0.5f
                szBuf[i] = eng.lerp(fr.z.toFloat(), to.z.toFloat(), u) + 0.5f
                shBuf[i] = eng.lerp(fr.h, to.h, u)
            } else {
                val w = u - 1f
                val nx = if (i == 0) to.x + p.ldX else sn[i - 1].x.toFloat()
                val nz = if (i == 0) to.z + p.ldZ else sn[i - 1].z.toFloat()
                val nhh = if (i == 0) nh else sn[i - 1].h
                sxBuf[i] = eng.lerp(to.x.toFloat(), nx, w) + 0.5f
                szBuf[i] = eng.lerp(to.z.toFloat(), nz, w) + 0.5f
                shBuf[i] = eng.lerp(to.h, nhh, w)
            }
        }
        if (p.airSteps > 0 && sn[0].h == 0f) shBuf[0] += 0.16f * t
        val bonk = p.bonk
        if (dying && bonk != null) {
            val k = (p.deathT / 0.3f).coerceIn(0f, 1f)
            val off = sin(k * PI.toFloat()) * 0.32f
            sxBuf[0] += bonk.x * off
            szBuf[0] += bonk.z * off
        }

        val skin = eng.skinOf(p)
        val alpha = when {
            p.shield > 0f -> if ((floor(eng.clock * 12f).toInt() and 1) != 0) 0.35f else 1f
            p.power.ghost > 0f -> 0.58f + 0.1f * sin(eng.clock * 8f)
            else -> 1f
        }
        val sprBody = sphereSprite(skin.bodyHex)
        val sprBand = sphereSprite(skin.bandHex)
        val sprHead = sphereSprite(skin.headHex)

        val m = (n - 1) * GameConst.SUB + 1
        for (s in m - 1 downTo 1) {
            val i = s / GameConst.SUB
            val k = (s % GameConst.SUB).toFloat() / GameConst.SUB
            if (dying && i < p.popped) continue
            val j = min(i + 1, n - 1)
            val x = eng.lerp(sxBuf[i], sxBuf[j], k)
            val z = eng.lerp(szBuf[i], szBuf[j], k)
            val h = eng.lerp(shBuf[i], shBuf[j], k)
            val frac = s.toFloat() / max(1, m - 1)
            var r = GameConst.BODY_R * (1f - 0.55f * eng.smooth(((frac - 0.5f) / 0.5f).coerceIn(0f, 1f)))
            r *= bulgeAt(eng, p.id, s.toFloat() / GameConst.SUB)
            eng.proj(x, h + r, z, p0)
            val o = nextDrawItem()
            o.kind = 1
            o.d = p0.d
            o.x = p0.x
            o.y = p0.y
            o.r = r * p0.s
            o.spr = if ((s + 2) % 9 < 3) sprBand else sprBody
            o.a = alpha
            if (s % 2 == 0) {
                bs += p0.x; bs += p0.y; bs += o.r
            }
            addShadow(eng, g, x, z, h, r)
        }

        var lookItem: GameItem? = null
        var td = Int.MAX_VALUE
        for (it in g.items) {
            val d = kotlin.math.abs(it.x - sn[0].x) + kotlin.math.abs(it.z - sn[0].z)
            if (d < td) { td = d; lookItem = it }
        }
        if (dying && p.popped > 0) return

        var fx = if (n > 1) sxBuf[0] - sxBuf[1] else p.dir.x.toFloat()
        var fz = if (n > 1) szBuf[0] - szBuf[1] else p.dir.z.toFloat()
        var l = hypot(fx, fz)
        if (l < 0.05f) { fx = p.dir.x.toFloat(); fz = p.dir.z.toFloat(); l = 1f }
        fx /= l; fz /= l

        val r = GameConst.HEAD_R * (1f + 0.14f * p.pulse)
        val hx = sxBuf[0]
        val hz = szBuf[0]
        val hy = shBuf[0] + r * 0.92f
        eng.proj(hx, hy, hz, p0)
        val o = nextDrawItem()
        o.kind = 2
        o.d = p0.d
        o.x = p0.x
        o.y = p0.y
        o.r = r * p0.s
        o.spr = sprHead
        o.a = alpha
        o.wx = hx; o.wy = hy; o.wz = hz
        o.fx = fx; o.fz = fz
        o.wr = r
        o.headHex = skin.headHex
        o.pid = p.id
        o.dying = dying
        o.blaze = p.power.blaze > 0f
        o.look = lookItem
        addShadow(eng, g, hx, hz, shBuf[0], r)
        headOut.x = hx; headOut.y = hy; headOut.z = hz; headOut.fx = fx; headOut.fz = fz; headOut.ok = true
    }

    private fun buildItems(eng: GameEngine, g: GameState) {
        for (it in g.items) {
            if (it.age < 0f) continue
            val sc = if (it.age < 0.4f) eng.easeBack((it.age / 0.4f).coerceIn(0f, 1f)) else 1f
            var a = 1f
            if (it.life > 0f && it.life - it.age < 2.2f) {
                a = if ((floor(eng.clock * 10f).toInt() and 1) != 0) 0.35f else 1f
            }
            val x = it.x + 0.5f
            val z = it.z + 0.5f
            val bob = sin(eng.clock * 3f + it.id * 1.7f) * 0.06f
            val r = eng.itemRadius(it) * sc
            val y = eng.itemHeight(it) + bob
            eng.proj(x, y, z, p0)
            val o = nextDrawItem()
            o.kind = 3
            o.d = p0.d
            o.item = it
            o.x = p0.x; o.y = p0.y
            o.r = r * p0.s; o.s = p0.s
            o.a = a; o.wy = y; o.sc = sc
            addShadow(eng, g, x, z, max(0f, y - r - 0.05f), max(0.05f, r * 0.9f), 0.9f)
        }
    }

    private fun buildRocks(eng: GameEngine, g: GameState) {
        for (r in g.rocks) {
            if (r.st == "warn") continue
            val k = if (r.st == "rise") r.t else 1f
            if (k <= 0f) continue
            eng.proj(r.x + 0.5f, 0.4f, r.z + 0.5f, p0)
            val o = nextDrawItem()
            o.kind = 4
            o.d = p0.d
            o.rock = r
            o.k = k
            addShadow(eng, g, r.x + 0.5f, r.z + 0.5f, 0f, 0.5f, k.coerceIn(0f, 1f))
        }
    }

    private fun buildBeetles(eng: GameEngine, g: GameState) {
        for (b in g.beetles) {
            val tb = if (b.emerge > 0f || b.stun > 0f) 1f else (b.acc / b.iv).coerceIn(0f, 1f)
            val x = eng.lerp(b.fx.toFloat(), b.x.toFloat(), tb) + 0.5f
            val z = eng.lerp(b.fz.toFloat(), b.z.toFloat(), tb) + 0.5f
            val em = if (b.emerge > 0f) 1f - b.emerge / 1.3f else 1f
            val y = 0.14f - (1f - eng.easeOut(em)) * 0.22f
            eng.proj(x, y, z, p0)
            val o = nextDrawItem()
            o.kind = 5
            o.d = p0.d
            o.beetle = b
            o.x = p0.x; o.y = p0.y; o.s = p0.s; o.em = em
            if (em > 0.4f) addShadow(eng, g, x, z, 0f, 0.36f, em)
        }
    }

    private fun drawDepthSorted(canvas: Canvas, eng: GameEngine, th: BlendedTheme) {
        val items = drawList.subList(0, drawCount)
        items.sortByDescending { it.d }
        for (o in items) {
            when (o.kind) {
                1 -> o.spr?.let { drawSprite(canvas, it, o.x, o.y, o.r, o.a) }
                2 -> drawHead(canvas, eng, o)
                3 -> drawItem(canvas, eng, o)
                4 -> o.rock?.let { rk ->
                    drawMesh(
                        canvas, eng, th, MeshFactory.rockMesh(rk.seed),
                        rk.x + 0.5f, 0f, rk.z + 0.5f, 0f, 1f,
                        th.rock, th.glow * 0.08f, 1f,
                        max(0.02f, eng.easeBack(o.k.coerceIn(0f, 1f))), th.glow
                    )
                }
                5 -> drawBeetle(canvas, eng, o)
            }
        }
    }

    private fun drawMesh(
        canvas: Canvas, eng: GameEngine, th: BlendedTheme, m: PolyMesh,
        wx: Float, wy: Float, wz: Float, rot: Float, scale: Float,
        baseRgb: FloatArray, emiss: Float, alpha: Float, ys: Float, edgeGlow: Float
    ) {
        val cr = cos(rot)
        val sr = sin(rot)
        for (i in m.verts.indices) {
            val v = m.verts[i]
            val x = (v[0] * cr - v[2] * sr) * scale + wx
            val y = v[1] * scale * ys + wy
            val z = (v[0] * sr + v[2] * cr) * scale + wz
            eng.proj(x, y, z, p0)
            mpx[i] = p0.x; mpy[i] = p0.y; mpd[i] = p0.d
        }
        faceBuf.clear()
        for (f in m.faces) {
            val a = f.a; val b = f.b; val c = f.c
            val area = (mpx[b] - mpx[a]) * (mpy[c] - mpy[a]) - (mpy[b] - mpy[a]) * (mpx[c] - mpx[a])
            if (area >= 0f) continue
            f.dd = mpd[a] + mpd[b] + mpd[c]
            faceBuf += f
        }
        faceBuf.sortByDescending { it.dd }
        val al = alpha.coerceIn(0f, 1f)
        for (f in faceBuf) {
            var nx = f.nx; val ny = f.ny; var nz = f.nz
            if (rot != 0f) {
                val tx = nx * cr - nz * sr
                val tz = nx * sr + nz * cr
                nx = tx; nz = tz
            }
            val dl = nx * lightVec[0] + ny * lightVec[1] + nz * lightVec[2]
            val lf = 0.6f + 0.46f * dl + emiss
            var col = floatArrayOf(
                (baseRgb[0] * lf).coerceIn(0f, 255f),
                (baseRgb[1] * lf).coerceIn(0f, 255f),
                (baseRgb[2] * lf).coerceIn(0f, 255f)
            )
            if (dl > 0.72f) col = mix(col, floatArrayOf(255f, 255f, 255f), (dl - 0.72f) * 0.6f)
            val argbCol = argb(col, al)
            pathA.reset()
            pathA.moveTo(mpx[f.a], mpy[f.a])
            pathA.lineTo(mpx[f.b], mpy[f.b])
            pathA.lineTo(mpx[f.c], mpy[f.c])
            pathA.close()
            paint.style = Paint.Style.FILL
            paint.color = argbCol
            canvas.drawPath(pathA, paint)
            strokePaint.color = argbCol
            strokePaint.strokeWidth = 0.9f
            strokePaint.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(pathA, strokePaint)
        }
        if (edgeGlow > 0.03f) {
            strokePaint.xfermode = addXfer
            strokePaint.color = argb(th.grout, 0.45f * edgeGlow * al)
            strokePaint.strokeWidth = 1.3f * eng.density
            for (f in faceBuf) {
                pathA.reset()
                pathA.moveTo(mpx[f.a], mpy[f.a])
                pathA.lineTo(mpx[f.b], mpy[f.b])
                pathA.lineTo(mpx[f.c], mpy[f.c])
                pathA.close()
                canvas.drawPath(pathA, strokePaint)
            }
            strokePaint.xfermode = null
        }
    }

    private fun drawHead(canvas: Canvas, eng: GameEngine, o: DrawItem) {
        val dying = o.dying
        val tPhase = eng.clock % 2.7f
        val tongue = !dying && tPhase < 0.34f
        val tk = if (tongue) sin((tPhase / 0.34f) * PI.toFloat()) else 0f
        val behind = o.fz < -0.25f
        if (tongue && behind) drawTongue(canvas, eng, o, tk)
        if (o.blaze && !dying) drawGlowAt(canvas, "#ff8a3a", o.x, o.y, o.r * 2.5f, 0.5f)
        o.spr?.let { drawSprite(canvas, it, o.x, o.y, o.r, o.a) }

        val sx = -o.fz
        val sz = o.fx
        eng.proj(o.wx + o.fx * 0.19f - sx * 0.19f, o.wy + 0.2f, o.wz + o.fz * 0.19f - sz * 0.19f, e1)
        eng.proj(o.wx + o.fx * 0.19f + sx * 0.19f, o.wy + 0.2f, o.wz + o.fz * 0.19f + sz * 0.19f, e2)
        val blink = (eng.clock % 3.7f) < 0.12f
        if (e1.d > e2.d) {
            drawEye(canvas, eng, e1, o, dying, blink)
            drawEye(canvas, eng, e2, o, dying, blink)
        } else {
            drawEye(canvas, eng, e2, o, dying, blink)
            drawEye(canvas, eng, e1, o, dying, blink)
        }
        if (tongue && !behind) drawTongue(canvas, eng, o, tk)
        if (!dying) {
            val by = o.y - o.r * 1.85f
            val br = max(7f * eng.density * 0.5f, o.r * 0.5f)
            val ink = Color.rgb(22, 18, 46)
            paint.style = Paint.Style.FILL
            paint.color = parseHex(eng.playerColorHex(o.pid), 0.95f)
            canvas.drawCircle(o.x, by, br, paint)
            strokePaint.color = ink
            strokePaint.strokeWidth = max(1.5f, br * 0.24f)
            canvas.drawCircle(o.x, by, br, strokePaint)
            textPaint.color = ink
            textPaint.textSize = br * 1.2f
            canvas.drawText((o.pid + 1).toString(), o.x, by + br * 0.42f, textPaint)
        }
    }

    private fun drawEye(canvas: Canvas, eng: GameEngine, e: ProjPt, o: DrawItem, dying: Boolean, blink: Boolean) {
        val r = 0.115f * e.s
        if (r <= 0.5f) return
        val alpha = (o.a.coerceIn(0f, 1f) * 255).toInt()
        val ink = Color.argb(alpha, 22, 18, 46)
        paint.style = Paint.Style.FILL
        paint.color = ink
        canvas.drawCircle(e.x, e.y + r * 0.12f, r * 1.14f, paint)
        if (blink && !dying) {
            paint.color = parseHex(o.headHex, o.a)
            canvas.drawCircle(e.x, e.y, r, paint)
            strokePaint.color = ink
            strokePaint.strokeWidth = r * 0.3f
            strokePaint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(e.x - r * 0.7f, e.y + r * 0.1f, e.x + r * 0.7f, e.y + r * 0.1f, strokePaint)
            return
        }
        paint.color = Color.argb(alpha, 255, 255, 255)
        canvas.drawCircle(e.x, e.y, r, paint)
        if (dying) {
            strokePaint.color = ink
            strokePaint.strokeWidth = r * 0.32f
            strokePaint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(e.x - r * 0.5f, e.y - r * 0.5f, e.x + r * 0.5f, e.y + r * 0.5f, strokePaint)
            canvas.drawLine(e.x + r * 0.5f, e.y - r * 0.5f, e.x - r * 0.5f, e.y + r * 0.5f, strokePaint)
        } else {
            var lx = o.fx
            var ly = o.fz * eng.cam.sinP
            val look = o.look
            if (look != null) {
                eng.proj(look.x + 0.5f, 0.3f, look.z + 0.5f, e3)
                lx = e3.x - e.x
                ly = e3.y - e.y
            }
            val l = hypot(lx, ly).coerceAtLeast(1e-4f)
            val px = e.x + (lx / l) * r * 0.38f
            val py = e.y + (ly / l) * r * 0.38f
            val pr = r * 0.56f
            paint.color = ink
            canvas.drawCircle(px, py, pr, paint)
            paint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(px - pr * 0.35f, py - pr * 0.38f, pr * 0.32f, paint)
        }
    }

    private fun drawTongue(canvas: Canvas, eng: GameEngine, o: DrawItem, k: Float) {
        val sx = -o.fz
        val sz = o.fx
        val y = o.wy - o.wr * 0.3f
        eng.proj(o.wx + o.fx * o.wr * 0.9f, y, o.wz + o.fz * o.wr * 0.9f, t1)
        val tx = o.wx + o.fx * (o.wr + 0.3f * k)
        val tz = o.wz + o.fz * (o.wr + 0.3f * k)
        eng.proj(tx, y, tz, t2)
        eng.proj(tx + o.fx * 0.1f * k + sx * 0.07f * k, y, tz + o.fz * 0.1f * k + sz * 0.07f * k, t3)
        eng.proj(tx + o.fx * 0.1f * k - sx * 0.07f * k, y, tz + o.fz * 0.1f * k - sz * 0.07f * k, t4)
        strokePaint.color = Color.rgb(255, 61, 110)
        strokePaint.strokeWidth = max(1.5f, 0.045f * t2.s)
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.strokeJoin = Paint.Join.ROUND
        pathA.reset()
        pathA.moveTo(t1.x, t1.y)
        pathA.lineTo(t2.x, t2.y)
        pathA.lineTo(t3.x, t3.y)
        pathA.moveTo(t2.x, t2.y)
        pathA.lineTo(t4.x, t4.y)
        canvas.drawPath(pathA, strokePaint)
    }

    private fun drawOrbIcon(canvas: Canvas, kind: String, x: Float, y: Float, s: Float, alpha: Float) {
        val aInt = (alpha.coerceIn(0f, 1f) * 255).toInt()
        when (kind) {
            "blaze" -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb((aInt * 0.94f).toInt(), 255, 255, 255)
                pathA.reset()
                pathA.moveTo(x, y - s)
                pathA.cubicTo(x + s * 0.95f, y - s * 0.2f, x + s * 0.7f, y + s * 0.85f, x, y + s * 0.85f)
                pathA.cubicTo(x - s * 0.7f, y + s * 0.85f, x - s * 0.9f, y + s * 0.05f, x - s * 0.3f, y - s * 0.4f)
                pathA.cubicTo(x - s * 0.25f, y - s * 0.05f, x - s * 0.05f, y + s * 0.05f, x + s * 0.05f, y - s * 0.25f)
                pathA.cubicTo(x + s * 0.12f, y - s * 0.5f, x + s * 0.02f, y - s * 0.8f, x, y - s)
                canvas.drawPath(pathA, paint)
            }
            "frost" -> {
                strokePaint.color = Color.argb((aInt * 0.94f).toInt(), 255, 255, 255)
                strokePaint.strokeWidth = max(1f, s * 0.22f)
                strokePaint.strokeCap = Paint.Cap.ROUND
                for (k in 0 until 3) {
                    val ang = (k * PI / 3.0 + PI / 2.0).toFloat()
                    val dx = cos(ang) * s
                    val dy = sin(ang) * s
                    canvas.drawLine(x - dx, y - dy, x + dx, y + dy, strokePaint)
                }
            }
            else -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb((aInt * 0.94f).toInt(), 255, 255, 255)
                pathA.reset()
                pathA.moveTo(x - s * 0.7f, y + s * 0.8f)
                pathA.lineTo(x - s * 0.7f, y - s * 0.1f)
                rectTmp.set(x - s * 0.7f, y - s * 0.8f, x + s * 0.7f, y + s * 0.6f)
                pathA.arcTo(rectTmp, 180f, 180f, false)
                pathA.lineTo(x + s * 0.7f, y + s * 0.8f)
                pathA.lineTo(x + s * 0.35f, y + s * 0.55f)
                pathA.lineTo(x, y + s * 0.8f)
                pathA.lineTo(x - s * 0.35f, y + s * 0.55f)
                pathA.close()
                canvas.drawPath(pathA, paint)
                paint.color = Color.argb((aInt * 0.9f).toInt(), 40, 20, 80)
                canvas.drawCircle(x - s * 0.25f, y - s * 0.1f, s * 0.13f, paint)
                canvas.drawCircle(x + s * 0.25f, y - s * 0.1f, s * 0.13f, paint)
            }
        }
    }

    private fun drawItem(canvas: Canvas, eng: GameEngine, o: DrawItem) {
        val it = o.item ?: return
        when (it.type) {
            "apple" -> {
                drawSprite(canvas, sphereSprite("#ff4a4a"), o.x, o.y, o.r, o.a)
                val s = o.s * o.sc
                val aInt = (o.a.coerceIn(0f, 1f) * 255).toInt()
                strokePaint.color = Color.argb(aInt, 107, 61, 31)
                strokePaint.strokeWidth = max(1.2f, 0.045f * s)
                strokePaint.strokeCap = Paint.Cap.ROUND
                pathA.reset()
                pathA.moveTo(o.x, o.y - o.r * 0.78f)
                pathA.quadTo(o.x + 0.02f * s, o.y - o.r * 1.1f, o.x + 0.06f * s, o.y - o.r * 1.3f)
                canvas.drawPath(pathA, strokePaint)

                paint.style = Paint.Style.FILL
                paint.color = Color.argb(aInt, 76, 175, 60)
                canvas.save()
                canvas.translate(o.x + 0.13f * s, o.y - o.r * 1.12f)
                canvas.rotate(-25.8f)
                val rx = max(0.5f, 0.11f * s)
                val ry = max(0.3f, 0.05f * s)
                rectTmp.set(-rx, -ry, rx, ry)
                canvas.drawOval(rectTmp, paint)
                canvas.restore()
            }
            "berry" -> drawSprite(canvas, sphereSprite("#7457ff"), o.x, o.y, o.r, o.a)
            "orb" -> {
                val col = GameConst.ORB_COLORS[it.sub] ?: "#ffffff"
                drawGlowAt(canvas, col, o.x, o.y, o.r * 2.6f, 0.55f * o.a)
                drawSprite(canvas, sphereSprite(col), o.x, o.y, o.r, o.a)
                drawOrbIcon(canvas, it.sub, o.x, o.y + o.r * 0.05f, o.r * 0.55f, o.a)
            }
            "crystal" -> {
                drawGlowAt(canvas, "#7ff3ff", o.x, o.y, o.r * 3f, 0.45f * o.a)
                drawMesh(
                    canvas, eng, eng.blendedTheme, MeshFactory.GEM_MESH,
                    it.x + 0.5f, o.wy, it.z + 0.5f,
                    eng.clock * 1.8f, o.sc, hexToRgb("#6fe9ff"), 0.22f, o.a, 1f, 0f
                )
            }
        }
    }

    private fun drawBeetle(canvas: Canvas, eng: GameEngine, o: DrawItem) {
        val b = o.beetle ?: return
        val s = o.s * (0.55f + 0.45f * o.em)
        val ph = if (b.emerge <= 0f && b.stun <= 0f) b.phase else 0f
        val a = (o.em * 1.4f).coerceIn(0f, 1f)
        val aInt = (a * 255).toInt()
        canvas.save()
        canvas.translate(o.x, o.y)
        canvas.scale(s, s * eng.cam.sinP)
        canvas.rotate(Math.toDegrees(atan2(b.hz.toDouble(), b.hx.toDouble())).toFloat())

        strokePaint.color = Color.argb(aInt, 27, 16, 41)
        strokePaint.strokeWidth = 0.05f
        strokePaint.strokeCap = Paint.Cap.ROUND
        pathA.reset()
        for (side in intArrayOf(-1, 1)) {
            for (k in -1..1) {
                val w = sin(ph + k * 2.1f + (if (side > 0) 1.4f else 0f)) * 0.07f
                pathA.moveTo(k * 0.12f, side * 0.12f)
                pathA.quadTo(k * 0.16f + w, side * 0.3f, k * 0.2f + w * 1.3f, side * 0.36f)
            }
        }
        canvas.drawPath(pathA, strokePaint)

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, -0.26f, 0f, 0.26f,
            intArrayOf(Color.argb(aInt, 114, 83, 196), Color.argb(aInt, 60, 37, 117), Color.argb(aInt, 31, 18, 64)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        rectTmp.set(-0.34f, -0.23f, 0.26f, 0.23f)
        canvas.drawOval(rectTmp, paint)
        paint.shader = null

        paint.color = Color.argb((a * 0.38f * 255).toInt(), 120, 240, 255)
        canvas.save()
        canvas.translate(-0.08f, -0.1f)
        canvas.rotate(-8.6f)
        rectTmp.set(-0.16f, -0.05f, 0.16f, 0.05f)
        canvas.drawOval(rectTmp, paint)
        canvas.restore()

        strokePaint.color = Color.argb((a * 0.8f * 255).toInt(), 15, 8, 30)
        strokePaint.strokeWidth = 0.025f
        canvas.drawLine(0.2f, 0f, -0.33f, 0f, strokePaint)

        paint.color = Color.argb(aInt, 27, 16, 41)
        rectTmp.set(0.17f, -0.12f, 0.37f, 0.12f)
        canvas.drawOval(rectTmp, paint)

        strokePaint.color = Color.argb(aInt, 27, 16, 41)
        strokePaint.strokeWidth = 0.035f
        pathA.reset()
        pathA.moveTo(0.33f, -0.05f); pathA.quadTo(0.42f, -0.07f, 0.44f, 0f)
        pathA.moveTo(0.33f, 0.05f); pathA.quadTo(0.42f, 0.07f, 0.44f, 0f)
        canvas.drawPath(pathA, strokePaint)

        paint.color = if (b.stun > 0f) Color.argb(aInt, 119, 119, 138) else Color.argb(aInt, 255, 210, 58)
        canvas.drawCircle(0.31f, -0.055f, 0.03f, paint)
        canvas.drawCircle(0.31f, 0.055f, 0.03f, paint)
        canvas.restore()
    }

    private fun drawLassoGlow(canvas: Canvas, eng: GameEngine, g: GameState) {
        val spr = glowSprite("#ffc23a")
        paint.xfermode = addXfer
        for (p in g.players) {
            val bs = eng.bodyScreen.getOrNull(p.id) ?: continue
            if (p.glowT <= 0f || bs.isEmpty()) continue
            paint.alpha = (p.glowT.coerceIn(0f, 1f) * 0.7f * 255).toInt()
            var i = 0
            while (i + 2 < bs.size) {
                val x = bs[i]
                val y = bs[i + 1]
                val r = bs[i + 2] * 1.7f
                rectTmp.set(x - r, y - r, x + r, y + r)
                canvas.drawBitmap(spr, null, rectTmp, paint)
                i += 9
            }
        }
        paint.alpha = 255
        paint.xfermode = null
    }

    private fun drawFly(canvas: Canvas, eng: GameEngine) {
        for (f in eng.flyItems) {
            if (f.t < 0f) continue
            val k = f.t * f.t
            val h = eng.heads[f.owner]
            val tx = if (h.ok) h.x else f.x
            val ty = if (h.ok) h.y else f.y
            val tz = if (h.ok) h.z else f.z
            eng.proj(
                eng.lerp(f.x, tx, k),
                eng.lerp(f.y, ty, k) + sin(PI.toFloat() * f.t) * f.arc,
                eng.lerp(f.z, tz, k),
                p0
            )
            drawSprite(canvas, sphereSprite(f.colorHex), p0.x, p0.y, f.r * (1f - 0.55f * f.t) * p0.s, 1f)
        }
    }

    private fun drawParticles(canvas: Canvas, eng: GameEngine) {
        for (p in eng.particles) {
            if (p.k == "spark" || p.k == "flame") continue
            eng.proj(p.x, p.y, p.z, p0)
            val a = (p.life / p.max).coerceIn(0f, 1f)
            val r = p.size * p0.s
            if (r <= 0.2f) continue
            when (p.k) {
                "chunk" -> drawSprite(canvas, sphereSprite(p.colorHex), p0.x, p0.y, r, min(1f, a * 2.5f))
                "shard" -> {
                    canvas.save()
                    canvas.translate(p0.x, p0.y)
                    canvas.rotate(Math.toDegrees(p.rot.toDouble()).toFloat())
                    paint.style = Paint.Style.FILL
                    paint.color = parseHex(p.colorHex, min(1f, a * 2.5f))
                    pathA.reset()
                    pathA.moveTo(0f, -r); pathA.lineTo(r * 0.9f, r * 0.6f); pathA.lineTo(-r * 0.8f, r * 0.5f); pathA.close()
                    canvas.drawPath(pathA, paint)
                    canvas.restore()
                }
                "dust" -> {
                    paint.style = Paint.Style.FILL
                    paint.color = parseHex(p.colorHex, a * 0.55f)
                    canvas.drawCircle(p0.x, p0.y, r * (1.7f - a * 0.7f), paint)
                }
                "snow" -> {
                    paint.style = Paint.Style.FILL
                    paint.color = Color.argb((min(1f, a * 3f) * 0.85f * 255).toInt(), 255, 255, 255)
                    canvas.drawCircle(p0.x, p0.y, r, paint)
                }
                "z" -> {
                    textPaint.color = parseHex(p.colorHex, a)
                    textPaint.textSize = r * 2.4f
                    canvas.drawText("z", p0.x, p0.y, textPaint)
                }
            }
        }
        paint.xfermode = addXfer
        for (p in eng.particles) {
            if (p.k != "spark" && p.k != "flame") continue
            eng.proj(p.x, p.y, p.z, p0)
            val a = (p.life / p.max).coerceIn(0f, 1f)
            val r = p.size * p0.s * (if (p.k == "flame") 0.5f + a * 0.7f else 1f)
            if (r <= 0.2f) continue
            paint.alpha = (a * 255).toInt()
            rectTmp.set(p0.x - r, p0.y - r, p0.x + r, p0.y + r)
            canvas.drawBitmap(glowSprite(p.colorHex), null, rectTmp, paint)
        }
        paint.alpha = 255
        paint.xfermode = null
    }

    private fun drawTexts(canvas: Canvas, eng: GameEngine) {
        for (t in eng.floatTexts) {
            val k = 1f - t.life / t.max
            eng.proj(t.x, t.y + k * 1.1f, t.z, p0)
            val pop = if (k < 0.15f) max(0.1f, eng.easeBack(k / 0.15f)) else 1f
            val a = if (k > 0.7f) 1f - (k - 0.7f) / 0.3f else 1f
            val sz = max(10f * eng.density, t.size * eng.density * pop * (p0.s / (55f * eng.density)).coerceIn(0.75f, 1.35f))
            val al = a.coerceIn(0f, 1f)
            textStrokePaint.textSize = sz
            textStrokePaint.strokeWidth = sz * 0.24f
            textStrokePaint.color = Color.argb((al * 255).toInt(), 22, 18, 46)
            canvas.drawText(t.text, p0.x, p0.y, textStrokePaint)
            textPaint.textSize = sz
            textPaint.color = parseHex(t.colorHex, al)
            canvas.drawText(t.text, p0.x, p0.y, textPaint)
        }
    }

    private fun drawScreenFx(canvas: Canvas, eng: GameEngine, th: BlendedTheme, vw: Float, vh: Float) {
        val frost = eng.maxPower("frost")
        val blaze = eng.maxPower("blaze")
        paint.style = Paint.Style.FILL
        if (frost > 0f) {
            val a = min(1f, frost * 2f)
            paint.shader = RadialGradient(
                vw / 2f, vh / 2f, max(vw, vh) * 0.7f,
                intArrayOf(Color.argb((0.05f * a * 255).toInt(), 170, 220, 255), Color.argb((0.3f * a * 255).toInt(), 220, 240, 255)),
                floatArrayOf(0.4f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, vw, vh, paint)
        }
        if (blaze > 0f) {
            val a = min(1f, blaze * 2f)
            paint.shader = RadialGradient(
                vw / 2f, vh / 2f, max(vw, vh) * 0.7f,
                intArrayOf(Color.TRANSPARENT, Color.argb((0.2f * a * 255).toInt(), 255, 110, 40)),
                floatArrayOf(0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, vw, vh, paint)
        }
        paint.shader = null
        if (eng.flash > 0f) {
            paint.color = parseHex(eng.flashColorHex, eng.flash.coerceIn(0f, 1f) * 0.6f)
            canvas.drawRect(0f, 0f, vw, vh, paint)
        }
        paint.shader = RadialGradient(
            vw / 2f, vh * 0.55f, max(vw, vh) * 0.78f,
            intArrayOf(Color.TRANSPARENT, Color.argb(((0.3f + th.dark * 0.15f) * 255).toInt(), 8, 6, 24)),
            floatArrayOf(0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, vw, vh, paint)
        paint.shader = null
    }
}
