package com.kitsuneeesan.jadwalshalat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.LocalDate

/**
 * Verse-of-the-day reader: walks the Quran from 1:1 to 114:6, one verse per day. Each verse is shown
 * line by line (each Arabic line with its transliteration right underneath) on pages, then its Indonesian translation.
 * Surahs are cached offline after their first download.
 */
object Quran {
    /** Verses per surah (1..114). */
    val COUNTS = intArrayOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135, 112, 78, 118, 64,
        77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60,
        49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46,
        42, 29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3,
        5, 4, 5, 6,
    )
    val TOTAL = COUNTS.sum()

    private const val API = "https://api.quran.com/api/v4"
    private const val TRANSLATION_ID = 33 // Indonesian, Kementerian Agama

    /**
     * Pack items into groups, keeping their order; [fits] says whether a candidate group still fits.
     * Used for words into lines, lines into pages, and translation words into pages.
     */
    fun <T> lines(items: List<T>, fits: (List<T>) -> Boolean): List<List<T>> {
        val out = mutableListOf<MutableList<T>>()
        for (item in items) {
            // An item too big on its own still gets a group of its own.
            if (out.isEmpty() || !fits(out.last() + item)) out += mutableListOf<T>()
            out.last() += item
        }
        return out
    }

    /** Move [delta] verses from [surah]:[ayah], wrapping between 114:6 and 1:1. */
    fun step(surah: Int, ayah: Int, delta: Int): Pair<Int, Int> {
        var i = Math.floorMod((0 until surah - 1).sumOf { COUNTS[it] } + ayah - 1 + delta, TOTAL)
        var s = 0
        while (i >= COUNTS[s]) i -= COUNTS[s++]
        return s + 1 to i + 1
    }

    private fun prefs(c: Context) = c.getSharedPreferences("quran", Context.MODE_PRIVATE)

    /** Current verse, advanced by one for every day that passed since it was last shown. */
    @Synchronized
    fun current(c: Context, today: LocalDate = LocalDate.now()): Pair<Int, Int> {
        val p = prefs(c)
        val day = today.toEpochDay()
        val days = (day - p.getLong("day", day)).coerceAtLeast(0).toInt()
        val pos = step(p.getInt("surah", 1), p.getInt("ayah", 1), days)
        val edit = p.edit().putInt("surah", pos.first).putInt("ayah", pos.second).putLong("day", day)
        if (days > 0) edit.putInt("page", 0) // a new verse starts on its first page
        edit.apply()
        return pos
    }

    /** Previous/next verse buttons. */
    @Synchronized
    fun move(c: Context, delta: Int) {
        val (s, a) = current(c)
        val (ns, na) = step(s, a, delta)
        prefs(c).edit().putInt("surah", ns).putInt("ayah", na).putInt("page", 0).apply()
    }

    /** Taps on the verse turn pages; the widget wraps this count around the verse's page count. */
    fun page(c: Context) = prefs(c).getInt("page", 0)

    @Synchronized
    fun turnPage(c: Context) = prefs(c).edit().putInt("page", page(c) + 1).apply()

    /** A verse as its words (Arabic to transliteration) and translation. */
    data class Verse(val words: List<Pair<String, String>>, val translation: String)

    /** Verse [surah]:[ayah]; may download the surah. Blocking. */
    fun verse(c: Context, surah: Int, ayah: Int): Verse {
        val v = surah(c, surah).getJSONArray("ayat").getJSONObject(ayah - 1)
        val w = v.getJSONArray("w")
        return Verse(List(w.length()) { w.getJSONArray(it).let { p -> p.getString(0) to p.getString(1) } }, v.getString("id"))
    }

    private fun file(c: Context, n: Int) = File(c.filesDir, "verses/$n.json")

    fun isSaved(c: Context, n: Int) = file(c, n).exists()

    /** Footnote markers (`<sup foot_note=…>1</sup>`) and any other markup in the translation. */
    private fun plain(html: String) = html.replace(Regex("<sup[^>]*>.*?</sup>"), "").replace(Regex("<[^>]+>"), "").trim()

    /**
     * Surah [n] as {nomor, namaLatin, arti, ayat: [{w: [[arabic, latin], …], id}]}, from the offline copy
     * or downloaded (and saved) on first use. Blocking.
     */
    fun surah(c: Context, n: Int): JSONObject {
        val f = file(c, n)
        if (f.exists()) return JSONObject(f.readText())
        val chapter = requestJson("$API/chapters/$n?language=id").getJSONObject("chapter")
        val ayat = JSONArray()
        var page: Int? = 1
        while (page != null) {
            val res = requestJson(
                "$API/verses/by_chapter/$n?language=id&words=true&word_fields=text_uthmani" +
                    "&translations=$TRANSLATION_ID&per_page=50&page=$page",
            )
            val verses = res.getJSONArray("verses")
            for (i in 0 until verses.length()) {
                val v = verses.getJSONObject(i)
                val words = v.getJSONArray("words")
                val w = JSONArray()
                for (j in 0 until words.length()) {
                    val word = words.getJSONObject(j)
                    if (word.optString("char_type_name") != "word") continue // skip the verse-end marker
                    val latin = word.optJSONObject("transliteration")?.optString("text").orEmpty()
                    w.put(JSONArray().put(word.getString("text_uthmani")).put(if (latin == "null") "" else latin))
                }
                val tr = v.getJSONArray("translations").getJSONObject(0).getString("text")
                ayat.put(JSONObject().put("w", w).put("id", plain(tr)))
            }
            page = res.getJSONObject("pagination").optInt("next_page").takeIf { it > 0 }
        }
        if (ayat.length() != COUNTS[n - 1]) throw IOException("Surah $n: got ${ayat.length()} of ${COUNTS[n - 1]} verses")
        val slim = JSONObject()
            .put("nomor", n).put("namaLatin", chapter.getString("name_simple"))
            .put("arti", chapter.getJSONObject("translated_name").getString("name"))
            .put("ayat", ayat)
        f.parentFile!!.mkdirs()
        // Write then rename, so a half-written file is never mistaken for a saved surah.
        val tmp = File(f.parentFile, "$n.tmp")
        tmp.writeText(slim.toString())
        tmp.renameTo(f)
        return slim
    }
}
