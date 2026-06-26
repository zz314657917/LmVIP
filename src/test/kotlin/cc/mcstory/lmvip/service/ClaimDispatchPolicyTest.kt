package cc.mcstory.lmvip.service

import cc.mcstory.lmvip.model.ClaimDispatchStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClaimDispatchPolicyTest {

    @Test
    fun `missing legacy status is treated as claimed`() {
        assertEquals(ClaimDispatchStatus.CLAIMED, ClaimDispatchStatus.parse(null))
        assertEquals(ClaimDispatchStatus.CLAIMED, ClaimDispatchStatus.parse(""))
    }

    @Test
    fun `failed and pending claims block player repeat claim`() {
        assertTrue(ClaimDispatchPolicy.blocksPlayerClaim(ClaimDispatchStatus.PENDING))
        assertTrue(ClaimDispatchPolicy.blocksPlayerClaim(ClaimDispatchStatus.RUNNING))
        assertTrue(ClaimDispatchPolicy.blocksPlayerClaim(ClaimDispatchStatus.FAILED))
        assertTrue(ClaimDispatchPolicy.blocksPlayerClaim(ClaimDispatchStatus.CLAIMED))
        assertTrue(ClaimDispatchPolicy.blocksPlayerClaim(ClaimDispatchStatus.MANUAL_REVIEW))
    }

    @Test
    fun `only failed claims can be retried`() {
        assertTrue(ClaimDispatchPolicy.canRetry(ClaimDispatchStatus.FAILED))
        assertFalse(ClaimDispatchPolicy.canRetry(ClaimDispatchStatus.PENDING))
        assertFalse(ClaimDispatchPolicy.canRetry(ClaimDispatchStatus.RUNNING))
        assertFalse(ClaimDispatchPolicy.canRetry(ClaimDispatchStatus.CLAIMED))
        assertFalse(ClaimDispatchPolicy.canRetry(ClaimDispatchStatus.MANUAL_REVIEW))
    }

    @Test
    fun `only failed and pending claims can be reset`() {
        assertTrue(ClaimDispatchPolicy.canReset(ClaimDispatchStatus.FAILED))
        assertTrue(ClaimDispatchPolicy.canReset(ClaimDispatchStatus.PENDING))
        assertFalse(ClaimDispatchPolicy.canReset(ClaimDispatchStatus.RUNNING))
        assertFalse(ClaimDispatchPolicy.canReset(ClaimDispatchStatus.CLAIMED))
        assertFalse(ClaimDispatchPolicy.canReset(ClaimDispatchStatus.MANUAL_REVIEW))
    }
}
