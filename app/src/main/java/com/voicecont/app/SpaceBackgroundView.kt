package com.voicecont.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animasyonlu uzay arka planı: derin uzay gradyanı, kayan/parıldayan yıldızlar,
 * yavaşça dönen galaksi ve süzülen iki gezegen. Canvas + frame döngüsü.
 */
class SpaceBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private class Star(
        var x: Float, var y: Float, var r: Float,
        var a: Float, var sp: Float, var tw: Float
    )

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stars = ArrayList<Star>()

    private var bgShader: RadialGradient? = null
    private var galaxyShader: RadialGradient? = null
    private var planet1Shader: RadialGradient? = null
    private var planet2Shader: RadialGradient? = null

    private var galaxyCx = 0f
    private var galaxyCy = 0f
    private var galaxyR = 0f
    private var p1x = 0f; private var p1y = 0f; private val r1 = 36f
    private var p2x = 0f; private var p2y = 0f; private val r2 = 26f

    private var angle = 0f
    private var t = 0f
    private var running = false

    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            invalidate()
            postOnAnimationDelayed(this, 16)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val wf = w.toFloat(); val hf = h.toFloat()

        bgShader = RadialGradient(
            wf * 0.5f, 0f, max(w, h) * 1.15f,
            intArrayOf(
                Color.parseColor("#2A1D5E"),
                Color.parseColor("#170F38"),
                Color.parseColor("#0A0820"),
                Color.parseColor("#05030F")
            ),
            floatArrayOf(0f, 0.38f, 0.70f, 1f),
            Shader.TileMode.CLAMP
        )

        galaxyCx = wf * 0.82f
        galaxyCy = hf * 0.07f
        galaxyR = 150f * density
        galaxyShader = RadialGradient(
            galaxyCx, galaxyCy, galaxyR,
            intArrayOf(
                Color.argb(150, 190, 150, 255),
                Color.argb(70, 130, 95, 230),
                Color.argb(0, 80, 60, 180)
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )

        planet1Shader = planetShader(r1 * density, intArrayOf(
            Color.parseColor("#8FD3FF"), Color.parseColor("#3B6FD4"), Color.parseColor("#1B2F7A")
        ))
        planet2Shader = planetShader(r2 * density, intArrayOf(
            Color.parseColor("#FFD9A8"), Color.parseColor("#E6864F"), Color.parseColor("#8A3B1E")
        ))
        p1x = wf * 0.20f; p1y = hf * 0.22f
        p2x = wf * 0.82f; p2y = hf * 0.78f

        stars.clear()
        val n = 130
        repeat(n) {
            stars.add(
                Star(
                    Random.nextFloat() * wf,
                    Random.nextFloat() * hf,
                    Random.nextFloat() * 1.4f + 0.4f,
                    Random.nextFloat() * 6.28f,
                    (Random.nextFloat() * 0.25f + 0.05f) * density,
                    Random.nextFloat() * 0.04f + 0.01f
                )
            )
        }
    }

    private fun planetShader(r: Float, colors: IntArray): RadialGradient =
        RadialGradient(
            -r * 0.3f, -r * 0.3f, r * 1.5f,
            colors, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f) return

        // Arka plan gradyanı
        paint.shader = bgShader
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // Galaksi: parlak çekirdek + yavaş dönen kollar
        paint.shader = galaxyShader
        canvas.drawCircle(galaxyCx, galaxyCy, galaxyR, paint)
        paint.shader = null
        canvas.save()
        canvas.rotate(angle, galaxyCx, galaxyCy)
        paint.color = Color.argb(40, 255, 255, 255)
        canvas.drawOval(
            galaxyCx - galaxyR * 0.85f, galaxyCy - galaxyR * 0.30f,
            galaxyCx + galaxyR * 0.85f, galaxyCy + galaxyR * 0.30f, paint
        )
        canvas.rotate(55f, galaxyCx, galaxyCy)
        paint.color = Color.argb(30, 180, 150, 255)
        canvas.drawOval(
            galaxyCx - galaxyR * 0.72f, galaxyCy - galaxyR * 0.26f,
            galaxyCx + galaxyR * 0.72f, galaxyCy + galaxyR * 0.26f, paint
        )
        canvas.restore()

        // Yıldızlar (parıltı + kayış)
        for (s in stars) {
            s.a += s.tw
            s.y += s.sp
            if (s.y > h) { s.y = 0f; s.x = Random.nextFloat() * w }
            val al = 0.4f + abs(sin(s.a)) * 0.6f
            starPaint.color = Color.WHITE
            starPaint.alpha = (al * 255).toInt()
            canvas.drawCircle(s.x, s.y, s.r, starPaint)
        }

        // Gezegenler (süzülme)
        drawPlanet(canvas, p1x, p1y + sin(t) * 14f * density, r1 * density, planet1Shader)
        drawPlanet(canvas, p2x, p2y + sin(t + 1.5f) * 11f * density, r2 * density, planet2Shader)

        angle += 0.08f
        t += 0.025f
    }

    private fun drawPlanet(canvas: Canvas, cx: Float, cy: Float, r: Float, shader: Shader?) {
        // hafif dış parıltı
        paint.color = Color.argb(45, 150, 170, 255)
        canvas.drawCircle(cx, cy, r * 1.25f, paint)
        canvas.save()
        canvas.translate(cx, cy)
        paint.shader = shader
        canvas.drawCircle(0f, 0f, r, paint)
        paint.shader = null
        canvas.restore()
    }

    private fun start() {
        if (running) return
        running = true
        postOnAnimation(frame)
    }

    private fun stop() {
        running = false
        removeCallbacks(frame)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) start() else stop()
    }
}
