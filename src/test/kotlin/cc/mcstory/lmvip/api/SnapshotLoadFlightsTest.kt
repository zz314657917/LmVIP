package cc.mcstory.lmvip.api

import org.junit.jupiter.api.Assertions.assertSame
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals

class SnapshotLoadFlightsTest {

    @Test
    fun `refresh load does not reuse normal in flight load`() {
        val flights = SnapshotLoadFlights<String, Int>()
        val normalGate = CompletableFuture<Int>()
        val refreshGate = CompletableFuture<Int>()
        var normalLoads = 0
        var refreshLoads = 0

        val normal = flights.getOrStartNormal("player") {
            normalLoads++
            normalGate
        }
        val refresh = flights.getOrStartRefresh("player") {
            refreshLoads++
            refreshGate
        }

        assertEquals(1, normalLoads)
        assertEquals(1, refreshLoads)
        assertEquals(2, flights.inFlightCount())

        normalGate.complete(1)
        refreshGate.complete(2)
        assertEquals(1, normal.get())
        assertEquals(2, refresh.get())
    }

    @Test
    fun `normal load reuses refresh in flight load`() {
        val flights = SnapshotLoadFlights<String, Int>()
        val refreshGate = CompletableFuture<Int>()
        var normalLoads = 0
        var refreshLoads = 0

        val refresh = flights.getOrStartRefresh("player") {
            refreshLoads++
            refreshGate
        }
        val normal = flights.getOrStartNormal("player") {
            normalLoads++
            CompletableFuture.completedFuture(1)
        }

        assertSame(refresh, normal)
        assertEquals(0, normalLoads)
        assertEquals(1, refreshLoads)

        refreshGate.complete(3)
        assertEquals(3, normal.get())
    }
}
