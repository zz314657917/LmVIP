package cc.mcstory.lmvip.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RefreshingValueCacheTest {

    @Test
    fun `cache hit returns value without starting refresh`() {
        var now = 1_000L
        val cache = RefreshingValueCache<String, String>({ 5_000L }, { now })

        cache.put("player", "VIP1")
        val read = cache.read("player")

        assertEquals("VIP1", read.value)
        assertFalse(read.refreshStarted)
        assertEquals(1, cache.stats().hits)
        assertEquals(0, cache.stats().refreshStarted)
    }

    @Test
    fun `expired cache returns stale value and starts only one refresh`() {
        var now = 0L
        val cache = RefreshingValueCache<String, String>({ 1_000L }, { now })
        cache.put("player", "VIP1")

        now = 2_000L
        val first = cache.read("player")
        val second = cache.read("player")

        assertEquals("VIP1", first.value)
        assertTrue(first.refreshStarted)
        assertEquals("VIP1", second.value)
        assertFalse(second.refreshStarted)
        assertEquals(2, cache.stats().staleHits)
        assertEquals(1, cache.stats().refreshStarted)
        assertEquals(1, cache.stats().refreshSkipped)
    }

    @Test
    fun `missing cache returns null and starts only one refresh`() {
        val cache = RefreshingValueCache<String, String>({ 1_000L }, { 0L })

        val first = cache.read("player")
        val second = cache.read("player")

        assertNull(first.value)
        assertTrue(first.refreshStarted)
        assertNull(second.value)
        assertFalse(second.refreshStarted)
        assertEquals(2, cache.stats().misses)
        assertEquals(1, cache.stats().refreshStarted)
        assertEquals(1, cache.stats().refreshSkipped)
    }

    @Test
    fun `clear removes cached values and refresh state`() {
        val cache = RefreshingValueCache<String, String>({ 1_000L }, { 0L })
        cache.put("player", "VIP1")
        cache.read("missing")

        cache.clear()

        assertEquals(0, cache.size())
        assertEquals(0, cache.refreshingSize())
    }
}
