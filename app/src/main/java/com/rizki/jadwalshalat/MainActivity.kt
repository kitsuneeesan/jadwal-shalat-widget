package com.rizki.jadwalshalat

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import java.time.LocalDate
import kotlin.concurrent.thread

/** Widget settings: shown when a widget is added and from the widget's "Settings" (reconfigure). */
class MainActivity : Activity() {
    private lateinit var auto: Switch
    private lateinit var province: Spinner
    private lateinit var city: Spinner
    private lateinit var save: Button
    private lateinit var background: Button
    private lateinit var exactAlarm: Button
    private lateinit var status: TextView

    private val prayerKeys = Shalat.KEYS
    private val prayerLabels = intArrayOf(R.string.subuh, R.string.dzuhur, R.string.ashar, R.string.maghrib, R.string.isya)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Accept up front so leaving with Back still adds the widget.
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))

        val pad = (20 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        fun label(text: String, size: Float = 14f) = TextView(this).apply { this.text = text; textSize = size; setPadding(0, pad / 2, 0, 0) }
        fun button(text: Int, onClick: () -> Unit) = Button(this).apply { setText(text); setOnClickListener { onClick() } }.also(col::addView)

        col.addView(label(getString(R.string.app_name), 24f))
        auto = Switch(this).apply {
            setText(R.string.auto_location)
            textSize = 16f
            setPadding(0, pad, 0, 0)
            isChecked = Shalat.isAuto(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Shalat.setAuto(this@MainActivity, on); applyMode() }
        }.also(col::addView)
        col.addView(label(getString(R.string.province)))
        province = Spinner(this).also(col::addView)
        col.addView(label(getString(R.string.city)))
        city = Spinner(this).also(col::addView)
        save = button(R.string.save) {
            val p = province.selectedItem as? String
            val k = city.selectedItem as? String
            if (p != null && k != null) { Shalat.saveLocation(this, p, k); refresh() }
        }
        background = button(R.string.background_location) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2)
        }
        exactAlarm = button(R.string.exact_alarm) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
        button(R.string.done) { finish() }
        status = label("").also(col::addView)
        setContentView(FrameLayout(this).apply { fitsSystemWindows = true; addView(col) })

        province.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val name = province.selectedItem as String
                load(city, { Shalat.cities(name) }, Shalat.location(this@MainActivity).second)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    override fun onResume() {
        super.onResume()
        exactAlarm.visibility = visibleIf(Build.VERSION.SDK_INT >= 31 && !getSystemService(AlarmManager::class.java).canScheduleExactAlarms())
        applyMode()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) = applyMode()

    private fun visibleIf(on: Boolean) = if (on) View.VISIBLE else View.GONE

    /** Auto: detect location (asking for permission first). Manual: user picks from the spinners. */
    private fun applyMode() {
        val on = auto.isChecked
        province.isEnabled = !on
        city.isEnabled = !on
        save.visibility = visibleIf(!on)
        background.visibility = visibleIf(on && Shalat.hasLocation(this) && !Shalat.hasLocation(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        if (!on) {
            loadSpinners()
            refresh()
            return
        }
        if (!Shalat.hasLocation(this)) {
            loadSpinners()
            status.setText(R.string.need_location)
            return requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }
        status.setText(R.string.detecting)
        thread {
            val found = runCatching { Shalat.autoLocate(this) }.getOrDefault(false)
            runOnUiThread { loadSpinners() }
            refresh(if (found) "" else getString(R.string.not_detected) + "\n")
        }
    }

    private fun loadSpinners() = load(province, { Shalat.provinces() }, Shalat.location(this).first)

    private fun load(spinner: Spinner, fetch: () -> List<String>, selected: String) = thread {
        val items = runCatching(fetch)
        runOnUiThread {
            items.onSuccess {
                spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, it)
                spinner.setSelection(it.indexOf(selected).coerceAtLeast(0))
            }.onFailure { status.text = getString(R.string.list_failed, it.message) }
        }
    }

    /** Re-render the widgets and show today's schedule as confirmation. */
    private fun refresh(note: String = "") = thread {
        val (p, k) = Shalat.location(this)
        val text = runCatching {
            val today = LocalDate.now()
            val day = Shalat.find(Shalat.month(this, today.year, today.monthValue), today)!!
            prayerKeys.indices.joinToString("\n") { "${getString(prayerLabels[it])}  ${day.getString(prayerKeys[it])}" }
        }.getOrElse { getString(R.string.schedule_failed, it.message) }
        BaseWidget.render(this)
        runOnUiThread { status.text = "$note$k, $p\n\n$text" }
    }
}
