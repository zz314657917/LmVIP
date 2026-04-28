package cc.mcstory.lmvip.api

import cc.mcstory.lmvip.model.VipSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class VipApiSnapshotsTest {
    @Test
    fun `api snapshot exposes vip level and total points`() {
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val snapshot = VipSnapshot(
            playerId = playerId,
            playerName = "tester",
            seasonId = "season-1",
            seasonName = "第一周目",
            totalPoints = 1500,
            seasonPoints = 500,
            monthlyPoints = 300,
            dailyPoints = 100,
            vipLevel = 3,
            vipLevelName = "VIP3",
            nextLevelNeed = 500,
        )

        val apiSnapshot = VipApiSnapshots.from(snapshot)

        assertEquals(playerId, apiSnapshot.playerId)
        assertEquals("tester", apiSnapshot.playerName)
        assertEquals("season-1", apiSnapshot.seasonId)
        assertEquals("第一周目", apiSnapshot.seasonName)
        assertEquals(1500, apiSnapshot.totalPoints)
        assertEquals(500, apiSnapshot.seasonPoints)
        assertEquals(300, apiSnapshot.monthlyPoints)
        assertEquals(100, apiSnapshot.dailyPoints)
        assertEquals(3, apiSnapshot.vipLevel)
        assertEquals("VIP3", apiSnapshot.vipLevelName)
        assertEquals(500, apiSnapshot.nextLevelNeed)
    }
}
