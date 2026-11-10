package com.rizki.jadwalshalat

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import kotlin.concurrent.thread

/**
 * Debug only: marketing shots for the README / social posts.
 *   adb shell am start -n com.rizki.jadwalshalat/.ShowcaseActivity --es scene night|day|logo
 */
class ShowcaseActivity : Activity() {
    private val dp by lazy { resources.displayMetrics.density }
    private fun px(v: Float) = (v * dp).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        val root = FrameLayout(this)
        setContentView(root)
        window.insetsController?.hide(WindowInsets.Type.systemBars())
        when (val scene = intent.getStringExtra("scene") ?: "night") {
            "logo" -> logo(root)
            else -> widgets(root, night = scene == "night")
        }
    }

    private fun logo(root: FrameLayout) {
        root.setBackgroundColor(Color.WHITE)
        val tile = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF3E8E5E.toInt(), 0xFF1A7F86.toInt()),
            ).apply { cornerRadius = 56 * dp }
        }
        tile.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_mosque)
            setColorFilter(Color.WHITE)
        }, FrameLayout.LayoutParams(px(130f), px(130f), Gravity.CENTER))
        root.addView(tile, FrameLayout.LayoutParams(px(220f), px(220f), Gravity.CENTER))
    }

    private fun widgets(root: FrameLayout, night: Boolean) {
        root.addView(ImageView(this).apply {
            setImageResource(if (night) R.drawable.showcase_night else R.drawable.showcase_day)
            scaleType = ImageView.ScaleType.CENTER_CROP
        })
        val config = Configuration(resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val themed = createConfigurationContext(config)
        val width = resources.displayMetrics.widthPixels / dp - 32
        val half = (width - 14) / 2
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(grid, FrameLayout.LayoutParams(px(width), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        thread {
            // Order matches BaseWidget.SIZES: tiny, row, small, tall, card.
            val (tiny, row, small, tall, card) = BaseWidget.previews(themed).map { it.second }
            runOnUiThread {
                fun slot(parent: LinearLayout, rv: android.widget.RemoteViews, w: Float, h: Float, top: Float = 0f) {
                    val frame = FrameLayout(themed)
                    frame.addView(rv.apply(themed, frame))
                    parent.addView(frame, LinearLayout.LayoutParams(px(w), px(h)).apply { topMargin = px(top) })
                }
                slot(grid, card, width, 190f)
                slot(grid, row, width, 86f, 14f)
                val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                grid.addView(bottom, LinearLayout.LayoutParams(px(width), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(14f) })
                val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                bottom.addView(left)
                bottom.addView(View(this), LinearLayout.LayoutParams(px(14f), 1))
                bottom.addView(right)
                slot(left, tall, half, 300f)
                slot(right, small, half, 190f)
                slot(right, tiny, half, 96f, 14f)
            }
        }
    }
}
