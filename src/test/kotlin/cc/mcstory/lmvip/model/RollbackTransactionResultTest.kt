package cc.mcstory.lmvip.model

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RollbackTransactionResultTest {

    private val playerId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `recharge and total rollback require vip sync after insert`() {
        assertTrue(result("recharge", TransactionWriteResult.Inserted(2L)).requiresVipSync)
        assertTrue(result("total", TransactionWriteResult.Inserted(3L)).requiresVipSync)
    }

    @Test
    fun `non vip dimensions and duplicate rollback do not require vip sync`() {
        assertFalse(result("season", TransactionWriteResult.Inserted(4L)).requiresVipSync)
        assertFalse(result("recharge", TransactionWriteResult.DuplicateOrder("rollback", "tx-1")).requiresVipSync)
    }

    private fun result(dimension: String, writeResult: TransactionWriteResult): RollbackTransactionResult {
        return RollbackTransactionResult(
            playerId = playerId,
            playerName = "tester",
            dimension = dimension,
            writeResult = writeResult
        )
    }
}
