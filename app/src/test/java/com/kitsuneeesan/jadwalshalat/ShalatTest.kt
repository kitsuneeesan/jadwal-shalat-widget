package com.kitsuneeesan.jadwalshalat

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class ShalatTest {
    private val day = listOf("04:32", "11:42", "15:39", "17:56", "19:09").map(LocalTime::parse)
    private fun t(s: String) = LocalTime.parse(s)

    @Test fun nextPrayer() {
        assertEquals(2 to 135, Shalat.next(day, t("04:31"), t("13:24:59")))   // design: Ashar, 2j 15m
        assertEquals(0 to 1, Shalat.next(day, t("04:31"), t("04:31")))
        assertEquals(3 to 137, Shalat.next(day, t("04:31"), t("15:39")))       // at Ashar time -> Maghrib
        assertEquals(0 to 51 + 4 * 60 + 31, Shalat.next(day, t("04:31"), t("23:09"))) // after Isya
    }

    @Test fun currentPrayer() {
        val sunrise = t("05:50")
        val tomorrow = t("04:31")
        assertEquals(4 to t("04:32"), Shalat.current(day, sunrise, tomorrow, t("02:00")))   // Isha from last night
        assertEquals(0 to sunrise, Shalat.current(day, sunrise, tomorrow, t("04:32")))
        assertEquals(null, Shalat.current(day, sunrise, tomorrow, t("05:50")))           // sunrise..Dhuhr
        assertEquals(1 to t("15:39"), Shalat.current(day, sunrise, tomorrow, t("11:42")))
        assertEquals(2 to t("17:56"), Shalat.current(day, sunrise, tomorrow, t("16:20:30")))
        assertEquals(4 to tomorrow, Shalat.current(day, sunrise, tomorrow, t("23:59")))
    }

    @Test fun minutesUntil() {
        assertEquals(81, Shalat.minutesUntil(t("16:30:40"), t("17:51")))
        assertEquals(5 * 60 + 6, Shalat.minutesUntil(t("23:00"), t("04:06")))
    }

    @Test fun elapsedWindow() {
        assertEquals(0.5f, Shalat.elapsed(t("15:00"), t("17:00"), t("16:00")), 1e-6f)
        assertEquals(1f, Shalat.elapsed(t("15:00"), t("17:00"), t("17:00")), 1e-6f)
        // Isha 19:00 -> Fajr 04:00 is 540 min; at 01:00 there are 360 done.
        assertEquals(360f / 540, Shalat.elapsed(t("19:00"), t("04:00"), t("01:00")), 1e-6f)
    }

    @Test fun progress() {
        // Dzuhur 11:42 -> Ashar 15:39 is 237 min; at 13:24 there are 135 left.
        assertEquals(102f / 237, Shalat.progress(day, 2, 135), 1e-6f)
        // Overnight Isya 19:09 -> Subuh 04:32 is 563 min.
        assertEquals(0f, Shalat.progress(day, 0, 563), 1e-6f)
        assertEquals(1f, Shalat.progress(day, 0, 0), 1e-6f)
    }
}

class MatchTest {
    private val prov = listOf("Banten", "D.I. Yogyakarta", "DKI Jakarta", "Jawa Barat", "Jawa Tengah")
    private val jabar = listOf("Kab. Bandung", "Kab. Bandung Barat", "Kab. Bogor", "Kota Bandung", "Kota Bogor")

    @Test fun geocoderNames() {
        assertEquals("Jawa Barat", Shalat.match(prov, "Jawa Barat"))
        assertEquals("DKI Jakarta", Shalat.match(prov, "Daerah Khusus Ibukota Jakarta"))
        assertEquals("DKI Jakarta", Shalat.match(prov, "Jakarta"))
        assertEquals("D.I. Yogyakarta", Shalat.match(prov, "Daerah Istimewa Yogyakarta"))
        assertEquals("Kota Jakarta", Shalat.match(listOf("Kab. Kepulauan Seribu", "Kota Jakarta"), "Kota Jakarta Selatan"))
        assertEquals("Kab. Bogor", Shalat.match(jabar, "Kabupaten Bogor"))
        assertEquals("Kota Bogor", Shalat.match(jabar, "Kota Bogor"))
        assertEquals("Kab. Bandung Barat", Shalat.match(jabar, "Kabupaten Bandung Barat"))
        assertEquals(null, Shalat.match(jabar, "Kota Depok"))
        assertEquals(null, Shalat.match(jabar, null))
    }
}
