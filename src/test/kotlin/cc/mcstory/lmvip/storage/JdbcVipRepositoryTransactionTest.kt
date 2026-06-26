package cc.mcstory.lmvip.storage

import cc.mcstory.lmvip.model.TransactionWriteResult
import cc.mcstory.lmvip.time.PeriodService
import java.time.DayOfWeek
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertIs

class JdbcVipRepositoryTransactionTest {

    @Test
    fun `duplicate order must match original transaction content`() {
        val repository = repository()
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000061")
        repository.startSeason("season-1", "第一周目", "tester")

        assertIs<TransactionWriteResult.Inserted>(
            repository.addRecharge(playerId, "tester", 100, "codex", "order-1", "tester", "first")
        )
        assertIs<TransactionWriteResult.DuplicateOrder>(
            repository.addRecharge(playerId, "tester", 100, "codex", "order-1", "tester", "retry")
        )
        assertIs<TransactionWriteResult.DuplicateMismatch>(
            repository.addRecharge(playerId, "tester", 200, "codex", "order-1", "tester", "bad retry")
        )
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
}
