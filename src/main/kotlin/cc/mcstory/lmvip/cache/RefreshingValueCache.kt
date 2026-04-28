package cc.mcstory.lmvip.cache

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class RefreshingValueCache<K, V>(
    private val ttlMillis: () -> Long,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val values = ConcurrentHashMap<K, Entry<V>>()
    private val refreshing = ConcurrentHashMap.newKeySet<K>()
    private val hits = AtomicLong()
    private val misses = AtomicLong()
    private val staleHits = AtomicLong()
    private val refreshStarted = AtomicLong()
    private val refreshSkipped = AtomicLong()
    private val refreshSuccess = AtomicLong()
    private val refreshFailure = AtomicLong()
    private val lastError = AtomicReference<String?>()

    fun read(key: K): CacheRead<V> {
        val entry = values[key]
        if (entry == null) {
            misses.incrementAndGet()
            return CacheRead(null, beginRefresh(key))
        }
        val expired = nowMillis() - entry.createdAt > ttlMillis().coerceAtLeast(0L)
        if (!expired) {
            hits.incrementAndGet()
            return CacheRead(entry.value, false)
        }
        staleHits.incrementAndGet()
        return CacheRead(entry.value, beginRefresh(key))
    }

    fun beginRefresh(key: K): Boolean {
        return if (refreshing.add(key)) {
            refreshStarted.incrementAndGet()
            true
        } else {
            refreshSkipped.incrementAndGet()
            false
        }
    }

    fun put(key: K, value: V) {
        values[key] = Entry(value, nowMillis())
    }

    fun refreshSucceeded(key: K, value: V) {
        put(key, value)
        refreshing.remove(key)
        refreshSuccess.incrementAndGet()
        lastError.set(null)
    }

    fun refreshFailed(key: K, error: Throwable) {
        refreshing.remove(key)
        refreshFailure.incrementAndGet()
        lastError.set(error.message ?: error.javaClass.simpleName)
    }

    fun finishRefreshWithoutValue(key: K) {
        refreshing.remove(key)
    }

    fun invalidate(key: K) {
        values.remove(key)
        refreshing.remove(key)
    }

    fun clear() {
        values.clear()
        refreshing.clear()
    }

    fun size(): Int = values.size

    fun refreshingSize(): Int = refreshing.size

    fun stats(): CacheStats {
        return CacheStats(
            size = size(),
            refreshing = refreshingSize(),
            hits = hits.get(),
            misses = misses.get(),
            staleHits = staleHits.get(),
            refreshStarted = refreshStarted.get(),
            refreshSkipped = refreshSkipped.get(),
            refreshSuccess = refreshSuccess.get(),
            refreshFailure = refreshFailure.get(),
            lastError = lastError.get(),
        )
    }

    private data class Entry<V>(val value: V, val createdAt: Long)
}

data class CacheRead<V>(
    val value: V?,
    val refreshStarted: Boolean,
)

data class CacheStats(
    val size: Int,
    val refreshing: Int,
    val hits: Long,
    val misses: Long,
    val staleHits: Long,
    val refreshStarted: Long,
    val refreshSkipped: Long,
    val refreshSuccess: Long,
    val refreshFailure: Long,
    val lastError: String?,
)
