package com.coilsnake.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import com.coilsnake.game.R
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Handles both the looping background music ("Jaunty Gumption", ducked in matches)
 * and synthesized retro/arcade sound effects matching the original Web Audio synthesizer.
 */
class SoundEngine(context: Context) {
    private val appCtx = context.applicationContext
    private val executor = Executors.newCachedThreadPool()

    @Volatile
    var muted: Boolean = false
        private set

    private var bgmPlayer: MediaPlayer? = null
    private var curVol = MENU_VOL
    private var isPausedByLifecycle = false

    init {
        initMusic()
    }

    private fun initMusic() {
        try {
            val mp = MediaPlayer.create(appCtx, R.raw.theme) ?: return
            mp.isLooping = true
            mp.setVolume(curVol, curVol)
            mp.start()
            bgmPlayer = mp
        } catch (_: Exception) {
            bgmPlayer = null
        }
    }

    fun setMuted(value: Boolean) {
        muted = value
        applyMusicVolume()
    }

    fun tickMusic(dt: Float, inMatch: Boolean) {
        val mp = bgmPlayer ?: return
        val target = if (inMatch) GAME_VOL else MENU_VOL
        curVol += (target - curVol) * (1f - exp(-dt * 2.5f))
        applyMusicVolume()
        if (!isPausedByLifecycle && !mp.isPlaying) {
            try {
                mp.start()
            } catch (_: Exception) {
            }
        }
    }

    private fun applyMusicVolume() {
        val v = if (muted) 0f else curVol.coerceIn(0f, 1f)
        try {
            bgmPlayer?.setVolume(v, v)
        } catch (_: Exception) {
        }
    }

    fun onPause() {
        isPausedByLifecycle = true
        try {
            if (bgmPlayer?.isPlaying == true) bgmPlayer?.pause()
        } catch (_: Exception) {
        }
    }

    fun onResume() {
        isPausedByLifecycle = false
        try {
            if (bgmPlayer != null && bgmPlayer?.isPlaying == false) {
                bgmPlayer?.start()
                applyMusicVolume()
            }
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            bgmPlayer?.stop()
            bgmPlayer?.release()
        } catch (_: Exception) {
        }
        bgmPlayer = null
        executor.shutdownNow()
    }

    fun play(name: String, arg: Int = 0) {
        if (muted) return
        executor.execute {
            try {
                synthesizeAndPlay(name, arg)
            } catch (_: Throwable) {
            }
        }
    }

    private enum class Wave { SINE, TRIANGLE, SQUARE, SAW }

    private data class ToneOp(
        val f1: Double,
        val f2: Double,
        val dur: Double,
        val wave: Wave,
        val vol: Double,
        val delay: Double = 0.0
    )

    private data class NoiseOp(
        val dur: Double,
        val vol: Double,
        val f1: Double,
        val f2: Double,
        val lowpass: Boolean,
        val delay: Double = 0.0
    )

    private fun synthesizeAndPlay(name: String, arg: Int) {
        val tones = mutableListOf<ToneOp>()
        val noises = mutableListOf<NoiseOp>()

        when (name) {
            "start" -> {
                tones += ToneOp(392.0, 0.0, 0.12, Wave.TRIANGLE, 0.18)
                tones += ToneOp(587.0, 0.0, 0.16, Wave.TRIANGLE, 0.18, 0.08)
            }
            "eat" -> {
                val b = 480.0 * 1.07.pow(min(arg.coerceAtLeast(0), 12).toDouble())
                tones += ToneOp(b, b * 1.5, 0.09, Wave.TRIANGLE, 0.24)
                tones += ToneOp(b * 2.0, 0.0, 0.06, Wave.SINE, 0.09, 0.03)
            }
            "berry" -> tones += ToneOp(880.0, 1320.0, 0.07, Wave.SINE, 0.16)
            "crystal" -> {
                tones += ToneOp(1180.0, 0.0, 0.12, Wave.SINE, 0.14)
                tones += ToneOp(1480.0, 0.0, 0.12, Wave.SINE, 0.14, 0.05)
                tones += ToneOp(1960.0, 0.0, 0.18, Wave.SINE, 0.12, 0.10)
            }
            "power" -> {
                tones += ToneOp(300.0, 1200.0, 0.32, Wave.SAW, 0.08)
                tones += ToneOp(600.0, 2400.0, 0.32, Wave.SINE, 0.10)
            }
            "powerEnd" -> tones += ToneOp(700.0, 260.0, 0.25, Wave.SINE, 0.10)
            "hop" -> {
                noises += NoiseOp(0.20, 0.12, 500.0, 2600.0, lowpass = false)
                tones += ToneOp(240.0, 520.0, 0.16, Wave.SINE, 0.16)
            }
            "land" -> {
                tones += ToneOp(150.0, 70.0, 0.11, Wave.SINE, 0.22)
                noises += NoiseOp(0.08, 0.07, 500.0, 0.0, lowpass = true)
            }
            "lasso" -> {
                val notes = doubleArrayOf(523.0, 659.0, 784.0, 1047.0, 1319.0, 1568.0)
                val k = min(2 + arg.coerceAtLeast(1), notes.size)
                for (i in 0 until k) {
                    tones += ToneOp(notes[i], 0.0, 0.18, Wave.TRIANGLE, 0.18, i * 0.055)
                }
            }
            "bite" -> {
                noises += NoiseOp(0.12, 0.22, 1100.0, 0.0, lowpass = false)
                tones += ToneOp(200.0, 90.0, 0.12, Wave.SQUARE, 0.07)
            }
            "smash" -> {
                noises += NoiseOp(0.28, 0.26, 400.0, 70.0, lowpass = true)
                tones += ToneOp(110.0, 45.0, 0.22, Wave.SINE, 0.25)
            }
            "death" -> {
                tones += ToneOp(420.0, 60.0, 0.55, Wave.SAW, 0.11)
                noises += NoiseOp(0.45, 0.18, 600.0, 90.0, lowpass = true)
            }
            "pop" -> tones += ToneOp(320.0 + (arg % 9) * 45.0, 160.0, 0.05, Wave.SINE, 0.07)
            "level" -> {
                val freqs = doubleArrayOf(523.0, 659.0, 784.0, 1047.0)
                freqs.forEachIndexed { i, f -> tones += ToneOp(f, 0.0, 0.24, Wave.TRIANGLE, 0.18, i * 0.09) }
            }
            "crumble" -> noises += NoiseOp(0.48, 0.16, 260.0, 60.0, lowpass = true)
            "rumble" -> noises += NoiseOp(0.50, 0.12, 150.0, 50.0, lowpass = true)
            "count" -> tones += ToneOp(660.0, 0.0, 0.10, Wave.SQUARE, 0.10)
            "go" -> {
                tones += ToneOp(880.0, 0.0, 0.12, Wave.SQUARE, 0.12)
                tones += ToneOp(1320.0, 0.0, 0.24, Wave.SQUARE, 0.12, 0.11)
            }
            "win" -> {
                val freqs = doubleArrayOf(523.0, 659.0, 784.0, 1047.0, 1319.0)
                freqs.forEachIndexed { i, f -> tones += ToneOp(f, 0.0, 0.22, Wave.TRIANGLE, 0.18, i * 0.1) }
            }
            "draw" -> {
                tones += ToneOp(330.0, 0.0, 0.20, Wave.TRIANGLE, 0.14)
                tones += ToneOp(330.0, 0.0, 0.30, Wave.TRIANGLE, 0.14, 0.25)
            }
            "ui_start" -> {
                val freqs = doubleArrayOf(523.0, 659.0, 784.0, 1047.0)
                freqs.forEachIndexed { i, f ->
                    tones += ToneOp(f, 0.0, if (i == 3) 0.18 else 0.09, Wave.SQUARE, 0.09, i * 0.09)
                }
            }
            "ui_restart" -> {
                tones += ToneOp(1319.0, 0.0, 0.06, Wave.SQUARE, 0.09)
                tones += ToneOp(1319.0, 0.0, 0.06, Wave.SQUARE, 0.09, 0.10)
                tones += ToneOp(1760.0, 0.0, 0.15, Wave.SQUARE, 0.09, 0.20)
            }
            "ui_menu" -> {
                tones += ToneOp(784.0, 0.0, 0.14, Wave.SQUARE, 0.09)
                tones += ToneOp(587.0, 0.0, 0.14, Wave.SQUARE, 0.09, 0.15)
                tones += ToneOp(392.0, 0.0, 0.22, Wave.SQUARE, 0.09, 0.30)
            }
            "ui_pick" -> {
                tones += ToneOp(880.0, 0.0, 0.05, Wave.SQUARE, 0.08)
                tones += ToneOp(1175.0, 0.0, 0.09, Wave.SQUARE, 0.08, 0.06)
            }
            "fizzle" -> tones += ToneOp(220.0, 120.0, 0.12, Wave.SQUARE, 0.07)
            "ready" -> tones += ToneOp(990.0, 0.0, 0.05, Wave.SINE, 0.07)
        }

        if (tones.isEmpty() && noises.isEmpty()) return

        var totalDur = 0.0
        for (t in tones) totalDur = maxOf(totalDur, t.delay + t.dur + 0.03)
        for (n in noises) totalDur = maxOf(totalDur, n.delay + n.dur + 0.03)

        val numSamples = (totalDur * SAMPLE_RATE).toInt().coerceAtLeast(1)
        val mix = DoubleArray(numSamples)

        for (t in tones) {
            val start = (t.delay * SAMPLE_RATE).toInt()
            val len = (t.dur * SAMPLE_RATE).toInt().coerceAtLeast(1)
            val attack = (0.008 * SAMPLE_RATE).toInt().coerceAtLeast(1)
            var phase = 0.0
            for (i in 0 until len) {
                val idx = start + i
                if (idx >= numSamples) break
                val frac = i.toDouble() / len
                val freq = if (t.f2 > 0) {
                    t.f1 * exp(ln(t.f2 / t.f1) * frac)
                } else {
                    t.f1
                }
                phase += freq / SAMPLE_RATE
                val p = phase - phase.toLong()
                val raw = when (t.wave) {
                    Wave.SINE -> sin(2.0 * PI * p)
                    Wave.TRIANGLE -> 1.0 - 4.0 * abs(p - 0.5)
                    Wave.SQUARE -> if (p < 0.5) 1.0 else -1.0
                    Wave.SAW -> 2.0 * p - 1.0
                }
                val env = if (i < attack) {
                    i.toDouble() / attack
                } else {
                    exp(-5.0 * (i - attack).toDouble() / (len - attack).coerceAtLeast(1))
                }
                mix[idx] += raw * env * t.vol * MASTER_GAIN
            }
        }

        for (n in noises) {
            val start = (n.delay * SAMPLE_RATE).toInt()
            val len = (n.dur * SAMPLE_RATE).toInt().coerceAtLeast(1)
            var lp = 0.0
            var hp = 0.0
            var prevIn = 0.0
            for (i in 0 until len) {
                val idx = start + i
                if (idx >= numSamples) break
                val frac = i.toDouble() / len
                val cutoff = if (n.f2 > 0) n.f1 * exp(ln(n.f2 / n.f1) * frac) else n.f1
                val alpha = (2.0 * PI * cutoff / SAMPLE_RATE).let { it / (it + 1.0) }.coerceIn(0.01, 0.99)
                val white = Random.nextDouble(-1.0, 1.0)
                lp += alpha * (white - lp)
                val sig = if (n.lowpass) {
                    lp
                } else {
                    hp = 0.85 * (hp + lp - prevIn)
                    prevIn = lp
                    hp
                }
                val env = exp(-4.5 * frac)
                mix[idx] += sig * env * n.vol * MASTER_GAIN
            }
        }

        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            pcm[i] = (mix[i].coerceIn(-0.98, 0.98) * Short.MAX_VALUE).toInt().toShort()
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcm, 0, pcm.size)
        track.play()
        Thread.sleep((totalDur * 1000).toLong() + 40L)
        track.stop()
        track.release()
    }

    companion object {
        private const val SAMPLE_RATE = 22050
        private const val MASTER_GAIN = 0.65
        private const val MENU_VOL = 0.70f
        private const val GAME_VOL = 0.12f
    }
}
