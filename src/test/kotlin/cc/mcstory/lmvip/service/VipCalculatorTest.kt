package cc.mcstory.lmvip.service

import cc.mcstory.lmvip.model.RewardRule
import cc.mcstory.lmvip.model.VipLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class VipCalculatorTest {
    private val levels = listOf(
        level(1, 100),
        level(2, 500),
        level(3, 1000),
    )

    @Test
    fun `total points decide permanent vip level`() {
        assertEquals(null, VipCalculator.levelFor(99, levels)?.level)
        assertEquals(1, VipCalculator.levelFor(100, levels)?.level)
        assertEquals(2, VipCalculator.levelFor(999, levels)?.level)
        assertEquals(3, VipCalculator.levelFor(1000, levels)?.level)
    }

    @Test
    fun `next level need returns zero at max level`() {
        assertEquals(1, VipCalculator.nextNeed(99, levels))
        assertEquals(400, VipCalculator.nextNeed(100, levels))
        assertEquals(0, VipCalculator.nextNeed(1000, levels))
    }

    private fun level(level: Int, total: Long) = VipLevel(
        level = level,
        name = "VIP$level",
        plainName = "VIP$level",
        totalPoints = total,
        group = "vip$level",
        benefits = emptyList(),
        daily = RewardRule(0, emptyList()),
        weekly = RewardRule(0, emptyList()),
        monthly = RewardRule(0, emptyList()),
        once = RewardRule(0, emptyList()),
    )
}
