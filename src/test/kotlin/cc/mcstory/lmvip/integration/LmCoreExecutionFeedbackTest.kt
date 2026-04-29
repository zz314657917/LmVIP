package cc.mcstory.lmvip.integration

import cc.mcstory.lmvip.config.ExecutionFeedbackConfig
import cc.mcstory.lmvip.config.ExecutionFeedbackEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LmCoreExecutionFeedbackTest {

    @Test
    fun `build spec carries required execution metadata`() {
        val spec = LmCoreExecutionFeedback.buildSpec(
            config = feedback(
                LmCoreExecutionFeedback.REASON_RECHARGE_SUCCESS,
                "[message]&aRecharge +%amount%",
                "[sound]ENTITY_PLAYER_LEVELUP:1.0:1.0"
            ),
            reason = LmCoreExecutionFeedback.REASON_RECHARGE_SUCCESS,
            traceId = "trace-1",
            values = mapOf("amount" to "100")
        )

        assertNotNull(spec)
        assertEquals(LmCoreExecutionFeedback.SOURCE, spec.source)
        assertEquals(LmCoreExecutionFeedback.REASON_RECHARGE_SUCCESS, spec.reason)
        assertEquals("trace-1", spec.traceId)
        assertEquals(listOf("[message]&aRecharge +100", "[sound]ENTITY_PLAYER_LEVELUP:1.0:1.0"), spec.steps)
    }

    @Test
    fun `disabled feedback returns no request`() {
        val spec = LmCoreExecutionFeedback.buildSpec(
            config = ExecutionFeedbackConfig(
                enabled = false,
                events = mapOf(
                    LmCoreExecutionFeedback.REASON_RECHARGE_SUCCESS to ExecutionFeedbackEvent(
                        enabled = true,
                        steps = listOf("[message]ok")
                    )
                )
            ),
            reason = LmCoreExecutionFeedback.REASON_RECHARGE_SUCCESS,
            traceId = "trace-2",
            values = emptyMap()
        )

        assertNull(spec)
    }

    @Test
    fun `unsafe command step is rejected before execution`() {
        assertFailsWith<IllegalArgumentException> {
            LmCoreExecutionFeedback.buildSpec(
                config = feedback(LmCoreExecutionFeedback.REASON_RECHARGE_SUCCESS, "[command]say unsafe"),
                reason = LmCoreExecutionFeedback.REASON_RECHARGE_SUCCESS,
                traceId = "trace-3",
                values = emptyMap()
            )
        }
    }

    private fun feedback(reason: String, vararg steps: String): ExecutionFeedbackConfig {
        return ExecutionFeedbackConfig(
            enabled = true,
            events = mapOf(
                reason to ExecutionFeedbackEvent(
                    enabled = true,
                    steps = steps.toList()
                )
            )
        )
    }
}
