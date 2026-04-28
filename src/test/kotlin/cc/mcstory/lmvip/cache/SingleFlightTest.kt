package cc.mcstory.lmvip.cache

import org.junit.jupiter.api.Assertions.assertSame
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SingleFlightTest {

    @Test
    fun `concurrent calls for same key share one future`() {
        val singleFlight = SingleFlight<String, Int>()
        val gate = CompletableFuture<Int>()
        var loads = 0

        val first = singleFlight.getOrStart("player") {
            loads++
            gate
        }
        val second = singleFlight.getOrStart("player") {
            loads++
            CompletableFuture.completedFuture(2)
        }

        assertSame(first, second)
        assertEquals(1, loads)
        assertEquals(1, singleFlight.inFlightCount())

        gate.complete(1)

        assertEquals(1, first.get())
        assertEquals(0, singleFlight.inFlightCount())
    }

    @Test
    fun `completed future is removed so later calls can reload`() {
        val singleFlight = SingleFlight<String, Int>()
        var loads = 0

        val first = singleFlight.getOrStart("player") {
            loads++
            CompletableFuture.completedFuture(1)
        }
        assertEquals(1, first.get())

        val second = singleFlight.getOrStart("player") {
            loads++
            CompletableFuture.completedFuture(2)
        }

        assertEquals(2, second.get())
        assertEquals(2, loads)
    }

    @Test
    fun `supplier runs once when calls arrive concurrently`() {
        val singleFlight = SingleFlight<String, Int>()
        val executor = Executors.newFixedThreadPool(8)
        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        val gate = CompletableFuture<Int>()
        val loads = AtomicInteger()
        val results = (1..8).map {
            executor.submit<CompletableFuture<Int>> {
                ready.countDown()
                start.await(5, TimeUnit.SECONDS)
                singleFlight.getOrStart("player") {
                    loads.incrementAndGet()
                    gate
                }
            }
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS))
        start.countDown()
        val futures = results.map { it.get(5, TimeUnit.SECONDS) }
        assertEquals(1, loads.get())
        futures.drop(1).forEach { assertSame(futures.first(), it) }

        gate.complete(3)

        futures.forEach { assertEquals(3, it.get(5, TimeUnit.SECONDS)) }
        executor.shutdownNow()
    }
}
