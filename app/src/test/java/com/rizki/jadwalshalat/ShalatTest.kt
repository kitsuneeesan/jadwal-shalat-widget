package com.rizki.jadwalshalat

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
