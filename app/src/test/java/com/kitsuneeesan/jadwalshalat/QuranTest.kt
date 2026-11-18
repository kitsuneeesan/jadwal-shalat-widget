package com.kitsuneeesan.jadwalshalat

import org.junit.Assert.assertEquals
import org.junit.Test

class QuranTest {
    @Test fun walksTheWholeQuran() {
        assertEquals(114, Quran.COUNTS.size)
        assertEquals(6236, Quran.TOTAL)
        assertEquals(1 to 2, Quran.step(1, 1, 1))
        assertEquals(2 to 1, Quran.step(1, 7, 1))          // into the next surah
        assertEquals(1 to 7, Quran.step(2, 1, -1))         // back into the previous one
        assertEquals(1 to 1, Quran.step(114, 6, 1))        // wraps to the start
        assertEquals(114 to 6, Quran.step(1, 1, -1))       // and to the end
        assertEquals(3 to 1, Quran.step(2, 280, 7))        // several days at once
        assertEquals(1 to 1, Quran.step(1, 1, Quran.TOTAL))
    }

    @Test fun linesFillUpToTheWidth() {
        val words = List(12) { "w$it" to "l$it" }
        val fits = { line: List<Pair<String, String>> -> line.size <= 5 }   // pretend five words fill a line
        val lines = Quran.lines(words, fits)
        assertEquals(listOf(5, 5, 2), lines.map { it.size })                 // every line as full as it can be
        assertEquals(words, lines.flatten())                                 // every word, in order
        assertEquals(listOf(listOf(words[0]), listOf(words[1])), Quran.lines(words.take(2)) { it.size <= 0 })
    }
}
