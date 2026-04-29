package cc.mcstory.lmvip.integration

import cc.mcstory.lmvip.model.RewardRule
import cc.mcstory.lmvip.model.VipLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class LuckPermsGroupSyncTest {

    @Test
    fun `managed groups include configured groups and legacy groups`() {
        val sync = LuckPermsGroupSync(
            levels = listOf(level(1, "vip1"), level(2, "vip2")),
            legacyGroups = listOf("vip_old", "vip2", "")
        )

        assertEquals(setOf("vip1", "vip2", "vip_old"), sync.managedGroups())
    }

    private fun level(level: Int, group: String) = VipLevel(
        level = level,
        name = "VIP$level",
        plainName = "VIP$level",
        totalPoints = level * 100L,
        group = group,
        benefits = emptyList(),
        daily = RewardRule(0, emptyList()),
        weekly = RewardRule(0, emptyList()),
        monthly = RewardRule(0, emptyList()),
        once = RewardRule(0, emptyList()),
    )
}
