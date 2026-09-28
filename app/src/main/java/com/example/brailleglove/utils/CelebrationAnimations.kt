package com.example.brailleglove.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.random.Random

/**
 * Small feedback/celebration animations for correct/wrong answers and level
 * completion.
 *
 * Everything here is built only from Android's own animation framework
 * (android.animation.ObjectAnimator / AnimatorSet and basic Views) — no
 * external animation library, Lottie file, or third-party asset pack is
 * used anywhere in this app. The star burst is a handful of plain "★"
 * TextViews animated outward with translation/alpha/rotation, not an
 * image or vector asset.
 */
object CelebrationAnimations {

    /** Quick scale "pop" — used right when an answer is marked correct. */
    fun playCorrectPop(target: View) {
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.18f, 1f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.18f, 1f)
        ObjectAnimator.ofPropertyValuesHolder(target, scaleX, scaleY).apply {
            duration = 380
            start()
        }
    }

    /** Short side-to-side shake — used right when an answer is marked wrong. */
    fun playWrongShake(target: View) {
        ObjectAnimator.ofFloat(
            target, View.TRANSLATION_X,
            0f, -22f, 22f, -16f, 16f, -8f, 8f, 0f
        ).apply {
            duration = 420
            start()
        }
    }

    /**
     * Spawns a handful of star characters inside [overlay] (must be a
     * FrameLayout so children can be freely positioned) near ([originX],
     * [originY]), animates them outward with fade + rotation, then removes
     * them automatically. [overlay] should sit on top of the rest of the
     * screen (e.g. the root FrameLayout of the fragment).
     */
    fun playStarBurst(
        overlay: ViewGroup,
        originX: Float,
        originY: Float,
        starCount: Int = 10
    ) {
        repeat(starCount) {
            val star = TextView(overlay.context).apply {
                text = "★"
                textSize = 18f
                setTextColor(STAR_COLORS.random())
                alpha = 1f
                translationX = 0f
                translationY = 0f
            }
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            overlay.addView(star, lp)
            star.x = originX
            star.y = originY

            val angle = Random.nextDouble(0.0, 2 * Math.PI)
            val distance = Random.nextInt(90, 220).toFloat()
            val dx = (Math.cos(angle) * distance).toFloat()
            val dy = (Math.sin(angle) * distance).toFloat()

            val moveX = ObjectAnimator.ofFloat(star, View.TRANSLATION_X, 0f, dx)
            val moveY = ObjectAnimator.ofFloat(star, View.TRANSLATION_Y, 0f, dy)
            val fade = ObjectAnimator.ofFloat(star, View.ALPHA, 1f, 0f)
            val spin = ObjectAnimator.ofFloat(
                star, View.ROTATION, 0f, Random.nextInt(-180, 180).toFloat()
            )

            AnimatorSet().apply {
                playTogether(moveX, moveY, fade, spin)
                duration = 700L + Random.nextInt(0, 350)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        overlay.removeView(star)
                    }
                })
                start()
            }
        }
    }

    // Drawn from the app's own mode palette (colors.xml) so celebration
    // moments feel like part of the same design system, not a generic
    // rainbow confetti effect.
    private val STAR_COLORS = listOf(
        Color.parseColor("#E8862E"), // mode_learning (amber)
        Color.parseColor("#12805F"), // mode_practice (teal)
        Color.parseColor("#5B4EA8"), // mode_test (indigo)
        Color.parseColor("#F2B84B"), // warm gold accent
    )
}
