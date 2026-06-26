package cc.mcstory.lmvip.service

import cc.mcstory.lmvip.config.ExecutionFeedbackConfig
import cc.mcstory.lmvip.config.GuiRuntimeConfig
import cc.mcstory.lmvip.config.GuiSlots
import cc.mcstory.lmvip.config.LanguageRuntimeConfig
import cc.mcstory.lmvip.config.VipRuntimeConfig
import cc.mcstory.lmvip.integration.LuckPermsGroupSync
import cc.mcstory.lmvip.model.RewardRule
import cc.mcstory.lmvip.model.VipLevel
import cc.mcstory.lmvip.storage.H2DatabaseService
import cc.mcstory.lmvip.storage.JdbcVipRepository
import cc.mcstory.lmvip.time.PeriodService
import java.lang.reflect.Proxy
import java.time.DayOfWeek
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.bukkit.OfflinePlayer

class VipServiceThreadGuardTest {

    @Test
    fun `refreshAndSync refuses to run on primary thread`() {
        val config = config()
        val repository = repository()
        val service = VipService(
            config = config,
            repository = repository,
            groupSync = LuckPermsGroupSync(config.levels, syncOverride = { _, _ -> true }),
            rewards = RewardService(config, repository),
            primaryThreadCheck = { true }
        )

        val error = assertFailsWith<IllegalStateException> {
            service.refreshAndSync(player(UUID.fromString("00000000-0000-0000-0000-000000000051")))
        }

        assertTrue(error.message.orEmpty().contains("async", ignoreCase = true))
    }

    @Test
    fun `refreshAndSync reports LuckPerms sync failure`() {
        val config = config()
        val repository = repository()
        val service = VipService(
            config = config,
            repository = repository,
            groupSync = LuckPermsGroupSync(config.levels, syncOverride = { _, _ -> false }),
            rewards = RewardService(config, repository),
            primaryThreadCheck = { false }
        )

        val error = assertFailsWith<IllegalStateException> {
            service.refreshAndSync(player(UUID.fromString("00000000-0000-0000-0000-000000000052")))
        }

        assertTrue(error.message.orEmpty().contains("LuckPerms sync failed"))
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

    private fun player(playerId: UUID, name: String = "tester"): OfflinePlayer {
        return Proxy.newProxyInstance(
            OfflinePlayer::class.java.classLoader,
            arrayOf(OfflinePlayer::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getUniqueId" -> playerId
                "getName" -> name
                "isOnline" -> false
                "toString" -> "MockOfflinePlayer($name)"
                else -> defaultReturn(method.returnType)
            }
        } as OfflinePlayer
    }

    private fun defaultReturn(type: Class<*>): Any? {
        return when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> '\u0000'
            java.lang.Void.TYPE -> Unit
            else -> null
        }
    }

    private fun config() = VipRuntimeConfig(
        databaseProfile = "LmVIP",
        messagePrefix = "",
        language = LanguageRuntimeConfig(prefix = "", messages = emptyMap(), lists = emptyMap()),
        levels = listOf(
            VipLevel(
                level = 1,
                name = "&aVIP1",
                plainName = "VIP1",
                totalPoints = 100,
                group = "vip1",
                benefits = emptyList(),
                daily = RewardRule(0, emptyList()),
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
