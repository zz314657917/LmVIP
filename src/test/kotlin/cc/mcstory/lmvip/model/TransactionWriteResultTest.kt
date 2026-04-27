package cc.mcstory.lmvip.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TransactionWriteResultTest {

    @Test
    fun `inserted result exposes transaction id`() {
        val result = TransactionWriteResult.Inserted(42L)

        assertEquals(42L, result.transactionIdOrNull())
        assertEquals("充值流水: 42", result.adminMessage("充值流水"))
    }

    @Test
    fun `duplicate order result has explicit admin message`() {
        val result = TransactionWriteResult.DuplicateOrder("codex", "order-1")

        assertNull(result.transactionIdOrNull())
        assertEquals("充值流水: 重复订单 codex/order-1", result.adminMessage("充值流水"))
    }

    @Test
    fun `no change result has explicit admin message`() {
        val result = TransactionWriteResult.NoChange

        assertNull(result.transactionIdOrNull())
        assertEquals("积分调整: 无需写入", result.adminMessage("积分调整", "无需写入"))
    }
}
