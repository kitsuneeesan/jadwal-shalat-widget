package com.rizki.jadwalshalat

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object Shalat {
    val KEYS = listOf("subuh", "dzuhur", "ashar", "maghrib", "isya")
    private const val API = "https://equran.id/api/v2/shalat"
    private const val CACHE = "jadwal|"

    fun prefs(c: Context) = c.getSharedPreferences("shalat", Context.MODE_PRIVATE)

    fun location(c: Context): Pair<String, String> {
        val p = prefs(c)
        return p.getString("provinsi", "Jawa Barat")!! to p.getString("kabkota", "Kota Bogor")!!
    }

    fun saveLocation(c: Context, provinsi: String, kabkota: String) =
        prefs(c).edit().putString("provinsi", provinsi).putString("kabkota", kabkota).apply()

    fun isAuto(c: Context) = prefs(c).getBoolean("auto", true)
    fun setAuto(c: Context, on: Boolean) = prefs(c).edit().putBoolean("auto", on).apply()

    fun hasLocation(c: Context, permission: String = Manifest.permission.ACCESS_COARSE_LOCATION) =
        c.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** Detect the phone's kab/kota and save it. Returns false when it couldn't be determined. Blocking. */
    fun autoLocate(c: Context): Boolean {
        val (provinsi, kabkota) = detect(c) ?: return false
        saveLocation(c, provinsi, kabkota)
        return true
    }

    private fun detect(c: Context): Pair<String, String>? {
        if (!hasLocation(c) || !Geocoder.isPresent()) return null
        val loc = currentLocation(c) ?: return null
        @Suppress("DEPRECATION")
        val addr = Geocoder(c, Locale.forLanguageTag("id-ID")).getFromLocation(loc.latitude, loc.longitude, 1)
            ?.firstOrNull() ?: return null
        val provinsi = match(provinces(), addr.adminArea) ?: return null
        val cities = cities(provinsi)
        val kabkota = match(cities, addr.subAdminArea) ?: match(cities, addr.locality) ?: return null
        return provinsi to kabkota
    }

    @SuppressLint("MissingPermission")
    private fun currentLocation(c: Context): Location? {
        val lm = c.getSystemService(LocationManager::class.java)
        val enabled = lm.getProviders(true)
        val provider = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { it in enabled } ?: return null
        if (Build.VERSION.SDK_INT >= 30) {
            val latch = CountDownLatch(1)
            var fix: Location? = null
            runCatching {
                lm.getCurrentLocation(provider, null, c.mainExecutor) { fix = it; latch.countDown() }
                latch.await(8, TimeUnit.SECONDS)
            }
            fix?.let { return it }
        }
        return enabled.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }

    private fun norm(s: String) = s.lowercase()
        .replace("daerah khusus ibukota", "dki").replace("daerah istimewa", "di")
        .replace("kabupaten", "kab").replace("provinsi", "").replace(".", "")
        .replace(Regex("\\s+"), " ").trim()

    /** Map a Geocoder name ("Daerah Istimewa Yogyakarta", "Kota Jakarta Selatan") onto the API's list. */
    fun match(options: List<String>, name: String?): String? {
        val n = norm(name ?: return null)
        val o = options.map { it to norm(it) }
        return o.firstOrNull { it.second == n }?.first
            ?: o.filter { n.startsWith(it.second + " ") }.maxByOrNull { it.second.length }?.first
            ?: o.firstOrNull { it.second.endsWith(" $n") || n.endsWith(" ${it.second}") }?.first
    }

    private fun call(path: String, body: JSONObject?): JSONObject {
        val conn = URL(API + path).openConnection() as HttpURLConnection
        // Stay well inside the ~10s a widget broadcast receiver is allowed to run.
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        if (body != null) {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        try {
            val stream = if (conn.responseCode < 400) conn.inputStream else conn.errorStream
            val json = JSONObject(stream.bufferedReader().use { it.readText() })
            if (json.optInt("code") != 200) throw IOException(json.optString("message"))
            return json
        } finally {
            conn.disconnect()
        }
    }

    private fun JSONArray.strings() = List(length()) { getString(it) }

    fun provinces() = call("/provinsi", null).getJSONArray("data").strings()

    fun cities(provinsi: String) =
        call("/kabkota", JSONObject().put("provinsi", provinsi)).getJSONArray("data").strings()

    /** Monthly schedule for the saved location; only the latest month is cached. */
    fun month(c: Context, year: Int, month: Int): JSONArray {
        val (provinsi, kabkota) = location(c)
        val key = "$CACHE$provinsi|$kabkota|$year-$month"
        val p = prefs(c)
        p.getString(key, null)?.let { return JSONArray(it) }
        val body = JSONObject().put("provinsi", provinsi).put("kabkota", kabkota)
            .put("bulan", month).put("tahun", year)
        val days = call("", body).getJSONObject("data").getJSONArray("jadwal")
        val edit = p.edit()
        p.all.keys.filter { it.startsWith(CACHE) }.forEach { edit.remove(it) }
        edit.putString(key, days.toString()).apply()
        return days
    }

    fun find(days: JSONArray, date: LocalDate): JSONObject? =
        (0 until days.length()).map { days.getJSONObject(it) }
            .firstOrNull { it.getString("tanggal_lengkap") == date.toString() }

    fun times(day: JSONObject) = KEYS.map { LocalTime.parse(day.getString(it)) }

    /**
     * Index (into KEYS) of the next prayer and minutes until it.
     * After Isya it is tomorrow's Subuh; [tomorrowSubuh] falls back to today's when
     * tomorrow is in a month we haven't fetched (last day of month, ~1 min off).
     */
    fun next(today: List<LocalTime>, tomorrowSubuh: LocalTime, now: LocalTime): Pair<Int, Int> {
        val nowMin = now.hour * 60 + now.minute
        today.forEachIndexed { i, t ->
            val m = t.hour * 60 + t.minute
            if (m > nowMin) return i to m - nowMin
        }
        return 0 to 24 * 60 - nowMin + tomorrowSubuh.hour * 60 + tomorrowSubuh.minute
    }

    /** Fraction of the gap between the previous prayer and the next one that has passed (0..1). */
    fun progress(times: List<LocalTime>, next: Int, minutes: Int): Float {
        fun min(t: LocalTime) = t.hour * 60 + t.minute
        val gap = if (next > 0) min(times[next]) - min(times[next - 1]) else 24 * 60 - min(times[4]) + min(times[0])
        return ((gap - minutes).toFloat() / gap).coerceIn(0f, 1f)
    }
}
