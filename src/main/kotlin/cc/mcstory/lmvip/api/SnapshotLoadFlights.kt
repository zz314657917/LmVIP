package cc.mcstory.lmvip.api

import cc.mcstory.lmvip.cache.SingleFlight
import java.util.concurrent.CompletableFuture

class SnapshotLoadFlights<K, V> {
    private val normal = SingleFlight<K, V>()
    private val refresh = SingleFlight<K, V>()

    fun getOrStartNormal(key: K, supplier: () -> CompletableFuture<V>): CompletableFuture<V> {
        return refresh.current(key) ?: normal.getOrStart(key, supplier)
    }

    fun getOrStartRefresh(key: K, supplier: () -> CompletableFuture<V>): CompletableFuture<V> {
        return refresh.getOrStart(key, supplier)
    }

    fun clear(key: K) {
        normal.clear(key)
        refresh.clear(key)
    }

    fun clear() {
        normal.clear()
        refresh.clear()
    }

    fun inFlightCount(): Int = normal.inFlightCount() + refresh.inFlightCount()
}
