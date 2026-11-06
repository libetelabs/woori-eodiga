package com.woorieodiga.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsVideosTest {
    @Test
    fun twentyPerKind() {
        VideoKind.entries.forEach { assertEquals(it.label, 20, KidsVideos.of(it).size) }
    }

    @Test
    fun allArePlaylistLinksWithoutDuplicates() {
        val urls = KidsVideos.all.map { it.url }
        urls.forEach { assertTrue(it, it.matches(Regex("https://www\\.youtube\\.com/playlist\\?list=[\\w-]+"))) }
        assertEquals(urls.size, urls.toSet().size)
    }
}
