package com.rizki.jadwalshalat

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.SizeF
import android.widget.RemoteViews
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.concurrent.thread

// One provider per starting size; all responsive (resizing swaps the layout) and all follow the
// phone's light/dark theme via values-night. Class names are kept from earlier versions so widgets
// already on the home screen survive updates.
class PrayerWidget : BaseWidget()  // 4x2 card; also receives the tick/tap/app-updated broadcasts
class GlassRow : BaseWidget()      // 4x1 schedule
class WidgetNext : BaseWidget()    // 2x2 next prayer

abstract class BaseWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            TAP -> refresh(context, true)
            TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED -> refresh(context, false)
        }
    }

    // Periodic (30 min): also follow the phone's location; fails quietly without background location access.
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context, true)

    override fun onDisabled(context: Context) = refresh(context, false)

    private fun refresh(context: Context, locate: Boolean) {
        val pending = goAsync()
        thread {
            try {
                // Render first so a tap shows something immediately; location lookup can take seconds.
                render(context)
                if (locate && Shalat.isAuto(context)) {
                    val before = Shalat.location(context)
                    runCatching { Shalat.autoLocate(context) }
                    if (Shalat.location(context) != before) render(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private class State(val times: List<LocalTime>, val next: Int, val minutes: Int, val progress: Float)

    companion object {
        private const val TICK = "com.rizki.jadwalshalat.TICK"
        private const val TAP = "com.rizki.jadwalshalat.REFRESH"

        /** Provider -> layout to use where sized layouts aren't supported (< Android 12). */
        private val TYPES = listOf(
            PrayerWidget::class.java to R.layout.w_card,
            GlassRow::class.java to R.layout.w_row,
            WidgetNext::class.java to R.layout.w_small,
        )

        /**
         * Minimum size (dp) for each layout. The launcher uses the closest one that fits.
         * One cell on a typical phone is ~100dp wide and ~96/218/340dp tall for 1/2/3 rows.
         */
        private val SIZES = listOf(
            SizeF(0f, 0f) to R.layout.w_tiny,        // 2x1
            SizeF(220f, 60f) to R.layout.w_row,      // 3x1, 4x1
            SizeF(100f, 150f) to R.layout.w_small,   // 2x2
            SizeF(100f, 300f) to R.layout.w_tall,    // 2x3+
            SizeF(280f, 150f) to R.layout.w_card,    // 3x2, 4x2+
        )

        private val CELL = intArrayOf(R.id.cell_subuh, R.id.cell_dzuhur, R.id.cell_ashar, R.id.cell_maghrib, R.id.cell_isya)
        private val NAME = intArrayOf(R.id.name_subuh, R.id.name_dzuhur, R.id.name_ashar, R.id.name_maghrib, R.id.name_isya)
        private val TIME = intArrayOf(R.id.time_subuh, R.id.time_dzuhur, R.id.time_ashar, R.id.time_maghrib, R.id.time_isya)
        private val LABEL = intArrayOf(R.string.subuh, R.string.dzuhur, R.string.ashar, R.string.maghrib, R.string.isya)
        private val ICON = intArrayOf(R.drawable.ic_subuh, R.drawable.ic_dzuhur, R.drawable.ic_ashar, R.drawable.ic_maghrib, R.drawable.ic_isya)

        private fun hhmm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

        private fun state(c: Context): State? {
            val now = LocalDateTime.now()
            val date = now.toLocalDate()
            val days = runCatching { Shalat.month(c, date.year, date.monthValue) }.getOrNull() ?: return null
            val times = Shalat.find(days, date)?.let(Shalat::times) ?: return null
            val tomorrowSubuh = Shalat.find(days, date.plusDays(1))?.let { Shalat.times(it)[0] } ?: times[0]
            val (next, minutes) = Shalat.next(times, tomorrowSubuh, now.toLocalTime())
            return State(times, next, minutes, Shalat.progress(times, next, minutes))
        }

        private fun views(c: Context, layout: Int, s: State?): RemoteViews {
            val v = RemoteViews(c.packageName, layout)
            v.setTextViewText(R.id.location, Shalat.location(c).second)
            // Tap = refresh in place (re-detects location too); settings live behind the widget's reconfigure.
            v.setOnClickPendingIntent(
                R.id.root,
                PendingIntent.getBroadcast(c, 1, Intent(c, PrayerWidget::class.java).setAction(TAP), PendingIntent.FLAG_IMMUTABLE),
            )
            if (s == null) {
                v.setTextViewText(R.id.next_name, c.getString(R.string.offline))
                v.setTextViewText(R.id.next_in, c.getString(R.string.tap_retry))
                return v
            }
            s.times.forEachIndexed { i, time ->
                val active = i == s.next
                v.setTextViewText(TIME[i], hhmm(time))
                v.setInt(CELL[i], "setBackgroundResource", if (active) R.drawable.cell_active else R.drawable.cell_idle)
                textColor(c, v, NAME[i], if (active) R.color.w_accent else R.color.w_muted)
                textColor(c, v, TIME[i], if (active) R.color.w_accent else R.color.w_text)
            }
            v.setTextViewText(R.id.next_name, c.getString(LABEL[s.next]))
            v.setTextViewText(R.id.next_time, hhmm(s.times[s.next]))
            val h = s.minutes / 60
            val m = s.minutes % 60
            val left = if (h > 0) c.getString(R.string.countdown_hm, h, m) else c.getString(R.string.countdown_m, m)
            v.setTextViewText(R.id.next_in, c.getString(R.string.time_left, left))
            v.setImageViewResource(R.id.next_icon, ICON[s.next])
            v.setProgressBar(R.id.progress, 1000, (s.progress * 1000).toInt(), false)
            return v
        }

        /** By resource id where possible, so the launcher resolves it for the current light/dark theme. */
        private fun textColor(c: Context, v: RemoteViews, id: Int, color: Int) =
            if (Build.VERSION.SDK_INT >= 31) v.setColor(id, "setTextColor", color) else v.setTextColor(id, c.getColor(color))

        /** Every size layout with live data, for the debug preview screen. Blocking. */
        internal fun previews(c: Context): List<Pair<SizeF, RemoteViews>> {
            val s = state(c)
            return SIZES.map { (size, layout) -> size to views(c, layout, s) }
        }

        /** Blocking (may hit the network); call off the main thread. */
        fun render(c: Context) {
            val manager = AppWidgetManager.getInstance(c)
            val targets = TYPES.map { (cls, fallback) -> manager.getAppWidgetIds(ComponentName(c, cls)) to fallback }
                .filter { it.first.isNotEmpty() }
            val am = c.getSystemService(AlarmManager::class.java)
            if (targets.isEmpty()) return am.cancel(tickIntent(c))
            val s = state(c)
            targets.forEach { (ids, fallback) ->
                val rv = if (Build.VERSION.SDK_INT >= 31) RemoteViews(SIZES.associate { (size, layout) -> size to views(c, layout, s) })
                else views(c, fallback, s)
                manager.updateAppWidget(ids, rv)
            }

            // Re-render just after the next minute boundary so the countdown stays in step with the clock.
            // Non-wakeup: fires when the screen is next on; inexact (can lag) without exact-alarm access.
            val at = (System.currentTimeMillis() / 60_000 + 1) * 60_000 + 500
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExact(AlarmManager.RTC, at, tickIntent(c))
            else am.set(AlarmManager.RTC, at, tickIntent(c))
        }

        private fun tickIntent(c: Context) = PendingIntent.getBroadcast(
            c, 0, Intent(c, PrayerWidget::class.java).setAction(TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
