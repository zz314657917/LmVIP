package cc.mcstory.lmvip.service

import cc.mcstory.lmvip.config.LanguageRuntimeConfig
import cc.mcstory.lmvip.model.RewardRule
import cc.mcstory.lmvip.model.VipLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnceRewardPolicyTest {

    private val language = LanguageRuntimeConfig(
        prefix = "",
        messages = mapOf(
            "reward.status.claimed" to "已领取",
            "reward.status.available" to "可领取",
            "reward.once-not-reached" to "未达到 %level_name%"
        ),
        lists = emptyMap()
    )

    @Test
    fun `once reward is unavailable before target vip level`() {
        val status = OnceRewardPolicy.status(snapshotVipLevel = 1, targetLevel = level(2), claimed = false, language = language)

        assertFalse(status.available)
        assertFalse(status.claimed)
        assertEquals("未达到 VIP2", status.reason)
    }

    @Test
    fun `once reward is available when target vip level reached`() {
        val status = OnceRewardPolicy.status(snapshotVipLevel = 2, targetLevel = level(2), claimed = false, language = language)

        assertTrue(status.available)
        assertFalse(status.claimed)
        assertEquals("可领取", status.reason)
    }

    @Test
    fun `once reward cannot be claimed twice`() {
        val status = OnceRewardPolicy.status(snapshotVipLevel = 3, targetLevel = level(2), claimed = true, language = language)

        assertFalse(status.available)
        assertTrue(status.claimed)
        assertEquals("已领取", status.reason)
    }

    @Test
    fun `once reward period key is stable`() {
        assertEquals("once", RewardService.ONCE_PERIOD_KEY)
        assertEquals("__global__", RewardService.ONCE_SEASON_ID)
    }

    private fun level(level: Int) = VipLevel(
        level = level,
        name = "VIP$level",
        plainName = "VIP$level",
        totalPoints = level * 100L,
        group = "vip$level",
        benefits = emptyList(),
        daily = RewardRule(0, emptyList()),
        weekly = RewardRule(0, emptyList()),
        monthly = RewardRule(0, emptyList()),
        once = RewardRule(0, emptyList()),
    )
}
