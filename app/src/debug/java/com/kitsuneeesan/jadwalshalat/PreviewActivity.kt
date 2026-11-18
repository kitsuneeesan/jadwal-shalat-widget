package com.kitsuneeesan.jadwalshalat

import android.app.Activity
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import kotlin.concurrent.thread

/** Debug only: renders every widget layout at a typical phone cell size over a wallpaper-like backdrop. */
class PreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (40 * dp).toInt(), (16 * dp).toInt(), (40 * dp).toInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF1E4FA0.toInt(), 0xFF5C8FC7.toInt(), 0xFF2F6E6A.toInt(), 0xFFD9C3A0.toInt(), 0xFFE8D9BE.toInt()),
            )
        }
        setContentView(ScrollView(this).apply { addView(col) })
        // Typical cell sizes (dp) for the buckets in BaseWidget.sizes(): tiny, row, small, tall, card.
        val real = listOf(188 to 96, 401 to 96, 188 to 218, 188 to 340, 401 to 218)
        thread {
            val quranWidth = (resources.displayMetrics.widthPixels / dp).toInt() - 32
            // The Quran widget at 4x2 and at 4x1 (compact layout).
            val quran = listOf(210, 96).map { it to QuranWidget.layout(this, quranWidth, it, compact = it < 150) }
            runOnUiThread {
                for ((height, views) in quran) {
                    val frame = FrameLayout(this)
                    frame.addView(views.apply(this, frame))
                    col.addView(frame, LinearLayout.LayoutParams((quranWidth * dp).toInt(), (height * dp).toInt()).apply {
                        bottomMargin = (12 * dp).toInt()
                    })
                }
            }
            val previews = BaseWidget.previews(this)
            runOnUiThread {
                for (p in listOf(0.3f, 0.65f, 0.9f, 1f)) {
                    val frame = FrameLayout(this)
                    frame.addView(BaseWidget.rowAt(this, p).apply(this, frame))
                    col.addView(frame, LinearLayout.LayoutParams(((resources.displayMetrics.widthPixels / dp) - 32).toInt().let { (it * dp).toInt() }, (80 * dp).toInt()).apply {
                        bottomMargin = (12 * dp).toInt()
                    })
                }
                for (night in listOf(false, true)) {
                    val mode = Configuration(resources.configuration).apply {
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                            (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
                    }
                    val themed = createConfigurationContext(mode)
                    previews.forEachIndexed { i, (_, rv) ->
                        val (w0, h) = real[i]
                        val w = minOf(w0, (resources.displayMetrics.widthPixels / dp).toInt() - 8)
                        val frame = FrameLayout(themed)
                        frame.addView(rv.apply(themed, frame))
                        col.addView(frame, LinearLayout.LayoutParams(((w - 24) * dp).toInt(), ((h - 16) * dp).toInt()).apply {
                            bottomMargin = (12 * dp).toInt()
                        })
                    }
                }
            }
        }
    }
}
