package cc.mcstory.lmvip.cache

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SnapshotCacheStoreTest {

    @Test
    fun `stale normal load cannot overwrite newer force refresh`() {
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000041")
        val cache = SnapshotCacheStore<String>({ 30_000L }, { 1_000L })

        val normalToken = cache.beginLoad(playerId, force = false)
        val refreshToken = cache.beginLoad(playerId, force = true)

        assertTrue(cache.putIfCurrent(refreshToken, "refresh-value"))
        assertFalse(cache.putIfCurrent(normalToken, "old-normal-value"))
        assertEquals("refresh-value", cache.getFresh(playerId))
    }

    @Test
    fun `invalidate prevents in flight load from writing cache`() {
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000042")
        val cache = SnapshotCacheStore<String>({ 30_000L }, { 1_000L })
        val token = cache.beginLoad(playerId, force = false)

        cache.invalidate(playerId)

        assertFalse(cache.putIfCurrent(token, "stale"))
        assertNull(cache.getFresh(playerId))
    }

    @Test
    fun `clear prevents all in flight loads from writing cache`() {
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000043")
        val cache = SnapshotCacheStore<String>({ 30_000L }, { 1_000L })
        val token = cache.beginLoad(playerId, force = true)

        cache.clear()

        assertFalse(cache.putIfCurrent(token, "stale"))
        assertNull(cache.getFresh(playerId))
    }
}
