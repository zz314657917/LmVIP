package cc.mcstory.lmvip.cache

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class SingleFlight<K, V> {
    private val inFlight = ConcurrentHashMap<K, CompletableFuture<V>>()

    fun getOrStart(key: K, supplier: () -> CompletableFuture<V>): CompletableFuture<V> {
        while (true) {
            inFlight[key]?.let { return it }
            val placeholder = CompletableFuture<V>()
            val existing = inFlight.putIfAbsent(key, placeholder)
            if (existing != null) {
                return existing
            }
            try {
                supplier().whenComplete { value, error ->
                    if (error != null) {
                        placeholder.completeExceptionally(error)
                    } else {
                        placeholder.complete(value)
                    }
                    inFlight.remove(key, placeholder)
                }
            } catch (error: Throwable) {
                placeholder.completeExceptionally(error)
                inFlight.remove(key, placeholder)
            }
            return placeholder
        }
    }

    fun clear(key: K) {
        inFlight.remove(key)
    }

    fun clear() {
        inFlight.clear()
    }

    fun inFlightCount(): Int = inFlight.size
}
