package cc.mcstory.lmvip.service

import cc.mcstory.lmvip.config.GuiRuntimeConfig
import cc.mcstory.lmvip.config.GuiSlots
import cc.mcstory.lmvip.config.LanguageRuntimeConfig
import cc.mcstory.lmvip.config.ExecutionFeedbackConfig
import cc.mcstory.lmvip.config.VipRuntimeConfig
import cc.mcstory.lmvip.model.ClaimCommandStatus
import cc.mcstory.lmvip.model.ClaimDispatchStatus
import cc.mcstory.lmvip.model.ClaimType
import cc.mcstory.lmvip.model.ClaimWriteResult
import cc.mcstory.lmvip.model.RewardRule
import cc.mcstory.lmvip.model.VipLevel
import cc.mcstory.lmvip.model.VipSnapshot
import cc.mcstory.lmvip.storage.H2DatabaseService
import cc.mcstory.lmvip.storage.JdbcVipRepository
import cc.mcstory.lmvip.time.PeriodService
import java.time.DayOfWeek
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RewardServiceClaimRetryTest {

    @Test
    fun `retry resumes failed command without replaying succeeded command`() {
        val repository = repository()
        val executed = mutableListOf<String>()
        val service = RewardService(
            config = config(listOf("say first", "say fixed-second")),
            repository = repository,
            commandExecutor = object : RewardCommandExecutor {
                override fun execute(context: RewardCommandContext, commandTemplate: String): Boolean {
                    executed += commandTemplate
                    return true
                }
            },
            serverThreadDispatcher = { _, task -> task() }
        )
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000031")
        val periodKey = service.periodKey(ClaimType.DAILY)
        val claim = assertIs<ClaimWriteResult.Inserted>(
            repository.beginClaim(
                playerId,
                "tester",
                "season-1",
                ClaimType.DAILY.dbKey,
                1,
                periodKey,
                listOf("say first", "say broken-second")
            )
        ).claim
        repository.updateClaimCommandStatus(claim.id, 0, ClaimCommandStatus.SUCCEEDED, "say first")
        repository.updateClaimCommandStatus(claim.id, 1, ClaimCommandStatus.FAILED, "say broken-second", "failed")
        repository.updateClaimStatus(claim.id, ClaimDispatchStatus.FAILED, "failed")

        val result = service.retryClaim(playerId, "tester", snapshot(playerId), ClaimType.DAILY)

        assertTrue(result.success)
        assertEquals(listOf("say fixed-second"), executed)
        assertEquals(ClaimDispatchStatus.CLAIMED, repository.findClaim(playerId, "season-1", ClaimType.DAILY.dbKey, 1, periodKey)?.status)
        val commands = repository.listClaimCommands(claim.id)
        assertEquals(ClaimCommandStatus.SUCCEEDED, commands[0].status)
        assertEquals(ClaimCommandStatus.SUCCEEDED, commands[1].status)
        assertEquals("say fixed-second", commands[1].commandTemplate)
    }

    @Test
    fun `retry refuses failed legacy claim without command state`() {
        val repository = repository()
        val service = RewardService(
            config = config(listOf("say first")),
            repository = repository,
            commandExecutor = object : RewardCommandExecutor {
                override fun execute(context: RewardCommandContext, commandTemplate: String): Boolean = true
            },
            serverThreadDispatcher = { _, task -> task() }
        )
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000032")
        val periodKey = service.periodKey(ClaimType.DAILY)
        val claim = assertIs<ClaimWriteResult.Inserted>(
            repository.beginClaim(playerId, "tester", "season-1", ClaimType.DAILY.dbKey, 1, periodKey)
        ).claim
        repository.updateClaimStatus(claim.id, ClaimDispatchStatus.FAILED, "failed")

        val result = service.retryClaim(playerId, "tester", snapshot(playerId), ClaimType.DAILY)

        assertEquals(false, result.success)
        assertEquals("缺少命令级发放状态，已阻止不安全重试；请人工核对后 reset 或补发", result.message)
    }

    @Test
    fun `retry stops at first failed command and leaves later commands pending`() {
        val repository = repository()
        val executed = mutableListOf<String>()
        val service = RewardService(
            config = config(listOf("say first", "say second", "say third")),
            repository = repository,
            commandExecutor = object : RewardCommandExecutor {
                override fun execute(context: RewardCommandContext, commandTemplate: String): Boolean {
                    executed += commandTemplate
                    return false
                }
            },
            serverThreadDispatcher = { _, task -> task() }
        )
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000033")
        val periodKey = service.periodKey(ClaimType.DAILY)
        val claim = assertIs<ClaimWriteResult.Inserted>(
            repository.beginClaim(
                playerId,
                "tester",
                "season-1",
                ClaimType.DAILY.dbKey,
                1,
                periodKey,
                listOf("say first", "say second", "say third")
            )
        ).claim
        repository.updateClaimCommandStatus(claim.id, 0, ClaimCommandStatus.SUCCEEDED, "say first")
        repository.updateClaimCommandStatus(claim.id, 1, ClaimCommandStatus.FAILED, "say second", "failed")
        repository.updateClaimStatus(claim.id, ClaimDispatchStatus.FAILED, "failed")

        val result = service.retryClaim(playerId, "tester", snapshot(playerId), ClaimType.DAILY)

        assertEquals(false, result.success)
        assertEquals(listOf("say second"), executed)
        val commands = repository.listClaimCommands(claim.id)
        assertEquals(ClaimCommandStatus.SUCCEEDED, commands[0].status)
        assertEquals(ClaimCommandStatus.FAILED, commands[1].status)
        assertEquals(ClaimCommandStatus.PENDING, commands[2].status)
    }

    private fun repository(): JdbcVipRepository {
        val url = "jdbc:h2:mem:lmvip_${UUID.randomUUID()};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        val repository = JdbcVipRepository(
            H2DatabaseService(url),
            PeriodService(ZoneId.of("Asia/Shanghai"), DayOfWeek.MONDAY)
        )
        repository.initialize()
        return repository
    }

    private fun snapshot(playerId: UUID) = VipSnapshot(
        playerId = playerId,
        playerName = "tester",
        seasonId = "season-1",
        seasonName = "第一周目",
        totalPoints = 100,
        seasonPoints = 100,
        monthlyPoints = 100,
        dailyPoints = 100,
        vipLevel = 1,
        vipLevelName = "VIP1",
        nextLevelNeed = 0,
    )

    private fun config(dailyCommands: List<String>) = VipRuntimeConfig(
        databaseProfile = "LmVIP",
        messagePrefix = "",
        language = LanguageRuntimeConfig(
            prefix = "",
            messages = mapOf(
                "reward.claim-success" to "领取成功",
                "reward.retry-success" to "奖励重试发放成功",
                "reward.dispatch-failed" to "奖励发放失败",
                "reward.dispatch-failed-rollback" to "奖励发放失败，领取记录已标记为失败，请联系管理员处理",
                "reward.dispatch-timeout" to "奖励发放超时",
                "reward.retry-missing-command-state" to "缺少命令级发放状态，已阻止不安全重试；请人工核对后 reset 或补发",
                "reward.retry-command-changed" to "奖励命令配置与已发放记录不一致，已阻止重试；请不要重排已部分发放的命令",
            ),
            lists = emptyMap()
        ),
        levels = listOf(
            VipLevel(
                level = 1,
                name = "&aVIP1",
                plainName = "VIP1",
                totalPoints = 100,
                group = "vip1",
                benefits = emptyList(),
                daily = RewardRule(0, dailyCommands),
                weekly = RewardRule(0, emptyList()),
                monthly = RewardRule(0, emptyList()),
                once = RewardRule(0, emptyList()),
            )
        ),
        legacyGroups = emptyList(),
        gui = GuiRuntimeConfig("VIP", 6, GuiSlots(4, 20, 22, 24, listOf(28)), emptyMap()),
        periods = PeriodService(ZoneId.of("Asia/Shanghai"), DayOfWeek.MONDAY),
        snapshotTtlMillis = 30_000,
        cacheRetainAfterQuitMillis = 300_000,
        rewardCommandTimeoutSeconds = 5,
        executionFeedback = ExecutionFeedbackConfig(enabled = false, events = emptyMap()),
    )
}
