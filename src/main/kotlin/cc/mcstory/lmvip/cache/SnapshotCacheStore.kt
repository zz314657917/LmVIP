package cc.mcstory.lmvip.cache

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class SnapshotCacheStore<V>(
    private val ttlMillis: () -> Long,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val entries = ConcurrentHashMap<UUID, Entry<V>>()
    private val generations = ConcurrentHashMap<UUID, AtomicLong>()
    private val globalGeneration = AtomicLong()

    fun getFresh(playerId: UUID): V? {
        val entry = entries[playerId] ?: return null
        val now = nowMillis()
        if (now - entry.createdAt > ttlMillis()) {
            entries.remove(playerId, entry)
            return null
        }
        return entry.value
    }

    fun beginLoad(playerId: UUID, force: Boolean): SnapshotCacheToken {
        val playerGeneration = generation(playerId)
        val generation = if (force) playerGeneration.incrementAndGet() else playerGeneration.get()
        return SnapshotCacheToken(playerId, globalGeneration.get(), generation)
    }

    fun putIfCurrent(token: SnapshotCacheToken, value: V): Boolean {
        if (globalGeneration.get() != token.globalGeneration) return false
        if (generation(token.playerId).get() != token.playerGeneration) return false
        entries[token.playerId] = Entry(value, nowMillis())
        return true
    }

    fun invalidate(playerId: UUID) {
        generation(playerId).incrementAndGet()
        entries.remove(playerId)
    }

    fun clear() {
        globalGeneration.incrementAndGet()
        entries.clear()
        generations.clear()
    }

    private fun generation(playerId: UUID): AtomicLong {
        return generations.computeIfAbsent(playerId) { AtomicLong() }
    }

    private data class Entry<V>(val value: V, val createdAt: Long)
}

data class SnapshotCacheToken(
    val playerId: UUID,
    val globalGeneration: Long,
    val playerGeneration: Long,
)
