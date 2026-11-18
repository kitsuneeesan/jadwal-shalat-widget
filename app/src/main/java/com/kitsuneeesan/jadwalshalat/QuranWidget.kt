package com.kitsuneeesan.jadwalshalat

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.StaticLayout
import android.text.TextPaint
import android.util.SizeF
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import kotlin.concurrent.thread

/** The same widget, offered at 4x1 in the widget picker (it switches to the compact layout by size). */
class QuranRowWidget : QuranWidget()

/**
 * Verse of the day: Arabic lines with their transliteration underneath, then the translation, split into
 * pages that fit the widget. Tapping the verse shows its next page; ‹ › switch verses (sideways swipes
 * belong to the launcher). Advances one verse per day (checked on each periodic update).
 */
open class QuranWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action: () -> Unit = when (intent.action) {
            NEXT -> { { Quran.move(context, 1) } }
            PREV -> { { Quran.move(context, -1) } }
            PAGE -> { { Quran.turnPage(context) } }
            Intent.ACTION_MY_PACKAGE_REPLACED -> { {} }
            else -> return
        }
        refresh(context, action)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context) {}

    // Resizing changes how much of the verse fits on a page.
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        refresh(context) {}

    private fun refresh(context: Context, before: () -> Unit) {
        val pending = goAsync()
        thread {
            try {
                before()
                render(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val NEXT = "com.kitsuneeesan.jadwalshalat.QURAN_NEXT"
        private const val PREV = "com.kitsuneeesan.jadwalshalat.QURAN_PREV"
        private const val PAGE = "com.kitsuneeesan.jadwalshalat.QURAN_PAGE"

        private fun button(c: Context, action: String, code: Int) = PendingIntent.getBroadcast(
            c, code, Intent(c, QuranWidget::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE,
        )

        /**
         * Lays a verse out on pages the size of the verse area ([widthDp] x [heightDp]), measuring text with the
         * same fonts and sizes as w_quran_line (Arabic 22sp over transliteration 12sp italic) and w_quran_tr (13sp).
         */
        private class Pager(c: Context, widthDp: Int, heightDp: Int) {
            private val dm = c.resources.displayMetrics
            private fun dp(v: Int) = v * dm.density
            private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, dm)
            private val arabic = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(22f) }
            private val latin = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = sp(12f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            }
            private val translation = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(13f) }

            private val width = dp(widthDp).toInt()
            private val height = dp(heightDp)

            private fun textHeight(text: String, paint: TextPaint, spacing: Float = 1f) =
                StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                    .setLineSpacing(0f, spacing).setUseLineSpacingFromFallbacks(true).build().height

            private fun arabicOf(line: List<Pair<String, String>>) = line.joinToString(" ") { it.first }
            private fun latinOf(line: List<Pair<String, String>>) = line.map { it.second }.filter { it.isNotEmpty() }.joinToString(" ")

            /** Height of a w_quran_line (4dp top padding, Arabic, transliteration). */
            private fun lineHeight(line: List<Pair<String, String>>) =
                dp(4) + textHeight(arabicOf(line), arabic) + textHeight(latinOf(line), latin)

            /** Height of a w_quran_tr (1.1 line spacing; 6dp top padding when it follows lines on the same page). */
            private fun translationHeight(text: String) = textHeight(text, translation, 1.1f)

            sealed interface Block
            data class Line(val arabic: String, val latin: String) : Block
            data class Translation(val text: String) : Block

            fun pages(verse: Quran.Verse): List<List<Block>> {
                val lines = Quran.lines(verse.words) { line ->
                    arabic.measureText(arabicOf(line)) <= width && latin.measureText(latinOf(line)) <= width
                }
                val linePages = Quran.lines(lines) { page -> page.sumOf { lineHeight(it).toDouble() } <= height }
                val used = linePages.last().sumOf { lineHeight(it).toDouble() }
                val pages = linePages.map { page -> page.map<List<Pair<String, String>>, Block> { Line(arabicOf(it), latinOf(it)) } }
                // A short translation shares the last page; a long one gets pages of its own.
                if (used + dp(6) + translationHeight(verse.translation) <= height) {
                    return pages.dropLast(1) + listOf(pages.last() + Translation(verse.translation))
                }
                val words = verse.translation.split(' ').filter { it.isNotEmpty() }
                return pages + Quran.lines(words) { translationHeight(it.joinToString(" ")) <= height }
                    .map { listOf(Translation(it.joinToString(" "))) }
            }
        }

        /** [prefix] followed by "● ○ ○" ("3 / 13" when dots would get too long); just [prefix] for a single page. */
        private fun dots(current: Int, count: Int, prefix: String = ""): String {
            if (count <= 1) return prefix
            val gap = if (prefix.isEmpty()) "" else "   "
            if (count > 12) return "$prefix$gap${current + 1} / $count"
            return prefix + gap + List(count) { if (it == current) "●" else "○" }.joinToString(" ")
        }

        /** Below this height (dp) the widget uses the 4x1 layout. */
        private const val COMPACT_BELOW_DP = 150

        /**
         * The widget for the current verse and page, sized [widthDp] x [heightDp]. On Android 12+ both layouts are
         * sent, so the launcher swaps between them instantly while resizing (the options-changed refresh then
         * re-paginates for the exact size). May download the surah. Blocking.
         */
        internal fun views(c: Context, widthDp: Int, heightDp: Int, lockScreen: Boolean = false): RemoteViews {
            fun build(height: Int, compact: Boolean) =
                layout(c, widthDp, height, compact).also { if (lockScreen) it.clearBackground() }
            if (Build.VERSION.SDK_INT < 31) return build(heightDp, heightDp < COMPACT_BELOW_DP)
            return RemoteViews(mapOf(
                SizeF(0f, 0f) to build(minOf(heightDp, COMPACT_BELOW_DP - 1), compact = true),
                SizeF(0f, COMPACT_BELOW_DP.toFloat()) to build(maxOf(heightDp, COMPACT_BELOW_DP), compact = false),
            ))
        }

        internal fun layout(c: Context, widthDp: Int, heightDp: Int, compact: Boolean): RemoteViews {
            val (s, a) = Quran.current(c)
            val v = RemoteViews(c.packageName, if (compact) R.layout.w_quran_compact else R.layout.w_quran)
            v.setOnClickPendingIntent(R.id.q_prev, button(c, PREV, 10))
            v.setOnClickPendingIntent(R.id.q_next, button(c, NEXT, 11))
            v.setOnClickPendingIntent(R.id.q_body, button(c, PAGE, 12))
            v.setTextViewText(R.id.q_meta, c.getString(R.string.verse_of, a, Quran.COUNTS[s - 1]) + " · QS $s:$a")
            v.removeAllViews(R.id.q_body)

            val surah = runCatching { Quran.surah(c, s) }.getOrNull()
            if (surah == null) {
                v.setTextViewText(R.id.q_name, c.getString(R.string.surah_n, s))
                v.addView(R.id.q_body, RemoteViews(c.packageName, R.layout.w_quran_tr).apply {
                    setTextViewText(R.id.q_item, c.getString(R.string.quran_offline))
                })
                v.setTextViewText(R.id.q_dots, "")
                return v
            }
            v.setTextViewText(R.id.q_name, "${surah.getString("namaLatin")} · ${surah.getString("arti")}")
            // Verse area: what's left of the widget around it (padding, header and dots / buttons and meta line).
            // ponytail: 4dp of slack for rounding; tune if a page ever clips.
            val pager = if (compact) Pager(c, widthDp - 4 - 2 * 32 - 16 - 4, heightDp - 20 - 14 - 4)
            else Pager(c, widthDp - 32 - 4, heightDp - 32 - 40 - 6 - 14 - 4)
            val pages = pager.pages(Quran.verse(c, s, a))
            val current = Quran.page(c) % pages.size
            // 4x1: "Al-Fatihah 1:7  ● ○ ○" centred under the verse, which is centred too.
            if (compact) v.setTextViewText(R.id.q_meta, dots(current, pages.size, "${surah.getString("namaLatin")} $s:$a"))
            val gravity = if (compact) Gravity.CENTER_HORIZONTAL else null
            for (block in pages[current]) v.addView(R.id.q_body, when (block) {
                is Pager.Line -> RemoteViews(c.packageName, R.layout.w_quran_line).apply {
                    setTextViewText(R.id.q_ar, block.arabic)
                    setTextViewText(R.id.q_lat, block.latin)
                    gravity?.let { setInt(R.id.q_ar, "setGravity", it); setInt(R.id.q_lat, "setGravity", it) }
                }
                is Pager.Translation -> RemoteViews(c.packageName, R.layout.w_quran_tr).apply {
                    setTextViewText(R.id.q_item, block.text)
                    gravity?.let { setInt(R.id.q_item, "setGravity", it) }
                    if (pages[current].size > 1) setViewPadding(R.id.q_item, 0, (6 * c.resources.displayMetrics.density).toInt(), 0, 0)
                }
            })
            v.setTextViewText(R.id.q_dots, dots(current, pages.size))
            v.setViewVisibility(R.id.q_dots, if (pages.size > 1) View.VISIBLE else View.GONE)
            return v
        }

        fun render(c: Context) {
            val manager = AppWidgetManager.getInstance(c)
            val ids = listOf(QuranWidget::class.java, QuranRowWidget::class.java)
                .flatMap { manager.getAppWidgetIds(ComponentName(c, it)).asList() }
            if (ids.isEmpty()) return
            for (id in ids) {
                val options = manager.getAppWidgetOptions(id)
                // Portrait: the narrowest width and the tallest height.
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300)
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 200)
                manager.updateAppWidget(id, views(c, width, height, manager.isOnLockScreen(id)))
            }
            // Fetch the following surah ahead of time so tomorrow's verse also works offline.
            val next = Quran.current(c).first % 114 + 1
            if (!Quran.isSaved(c, next)) runCatching { Quran.surah(c, next) }
        }
    }
}
