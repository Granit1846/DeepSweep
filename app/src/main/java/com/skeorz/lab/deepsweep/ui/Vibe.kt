package com.skeorz.lab.deepsweep.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Game vibration: a strong long rumble for a mine, a short medium tick for a buoy. */
class Vibe(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(VibratorManager::class.java))?.defaultVibrator
        else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)

    /** ~0.5 s: a full-strength hit that fades out. */
    fun boom() = play(
        VibrationEffect.createWaveform(longArrayOf(0, 180, 40, 140, 40, 120), intArrayOf(0, 255, 0, 200, 0, 140), -1),
    )

    /** 56 ms at strength 196 (both +40% over the first 40 ms / 140). */
    fun buoy() = play(VibrationEffect.createOneShot(56, 196))

    private fun play(effect: VibrationEffect) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        // devices without amplitude control get the same rhythm at their fixed strength
        v.vibrate(effect)
    }
}
