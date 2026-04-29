package cc.mcstory.lmvip.integration

import cc.mcstory.lmvip.config.ExecutionFeedbackConfig
import cc.mcstory.lmvip.config.VipRuntimeConfig
import cc.mcstory.lmvip.model.VipSnapshot
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.platform.BukkitPlugin
import java.util.Locale
import java.util.UUID
import java.util.logging.Logger

object LmCoreExecutionFeedback {
    const val SOURCE = "lmvip"
    const val REASON_RECHARGE_SUCCESS = "recharge-success"
    const val REASON_LEVEL_CHANGED = "level-changed"
    const val REASON_REWARD_CLAIM_SUCCESS = "reward-claim-success"
    const val REASON_BENEFITS_REFRESH_SUCCESS = "benefits-refresh-success"

    private val allowedTypes = setOf(
        "message",
        "msg",
        "broadcast",
        "bc",
        "messageall",
        "msgall",
        "action",
        "actionbar",
        "actionall",
        "actionbarall",
        "title",
        "titleall",
        "sound",
        "close",
        "delay"
    )

    fun dispatch(
        player: Player?,
        config: VipRuntimeConfig?,
        reason: String,
        snapshot: VipSnapshot?,
        values: Map<String, Any?> = emptyMap(),
    ): Boolean {
        if (player == null || config == null) {
            return false
        }
        val contextValues = contextValues(player, snapshot, values)
        val spec = runCatching {
            buildSpec(config.executionFeedback, reason, traceId(reason, player.uniqueId, contextValues), contextValues)
        }.getOrElse {
            warn("LmCore ExecutionService feedback config is invalid: reason=$reason, error=${it.message}")
            return false
        } ?: return false
        val service = resolveExecutionService() ?: run {
            warn("LmCore ExecutionService is unavailable, feedback skipped: reason=$reason, traceId=${spec.traceId}")
            return false
        }
        val request = runCatching { buildExecutionRequest(spec) }.getOrElse {
            warn("LmCore ExecutionService request build failed: reason=$reason, traceId=${spec.traceId}, error=${it.message}")
            return false
        }
        val context = runCatching { buildExpressionContext(player, contextValues) }.getOrElse {
            warn("LmCore ExecutionService context build failed: reason=$reason, traceId=${spec.traceId}, error=${it.message}")
            return false
        }
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(BukkitPlugin.getInstance(), Runnable {
                executeSafely(service, context, request, spec)
            })
            return true
        }
        return executeSafely(service, context, request, spec)
    }

    internal fun buildSpec(
        config: ExecutionFeedbackConfig,
        reason: String,
        traceId: String,
        values: Map<String, String>,
    ): FeedbackRequestSpec? {
        if (!config.enabled) {
            return null
        }
        val event = config.event(reason)
        if (!event.enabled) {
            return null
        }
        val steps = event.steps
            .filter { it.isNotBlank() }
            .map { renderPercentPlaceholders(it, values) }
            .onEach { requireAllowedStep(it) }
        if (steps.isEmpty()) {
            return null
        }
        return FeedbackRequestSpec(SOURCE, reason, traceId, steps)
    }

    internal fun contextValues(player: Player?, snapshot: VipSnapshot?, values: Map<String, Any?>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        fun put(key: String, value: Any?) {
            if (value != null) {
                result[normalizeKey(key)] = value.toString()
            }
        }
        put("player", player?.name ?: snapshot?.playerName)
        put("player_name", player?.name ?: snapshot?.playerName)
        put("uuid", player?.uniqueId ?: snapshot?.playerId)
        put("player_uuid", player?.uniqueId ?: snapshot?.playerId)
        if (snapshot != null) {
            put("season_id", snapshot.seasonId ?: "")
            put("season_name", snapshot.seasonName ?: "")
            put("total_points", snapshot.totalPoints)
            put("season_points", snapshot.seasonPoints)
            put("monthly_points", snapshot.monthlyPoints)
            put("daily_points", snapshot.dailyPoints)
            put("vip_level", snapshot.vipLevel)
            put("vip_level_name", snapshot.vipLevelName)
            put("next_level_need", snapshot.nextLevelNeed)
        }
        values.forEach { (key, value) -> put(key, value) }
        return result
    }

    internal data class FeedbackRequestSpec(
        val source: String,
        val reason: String,
        val traceId: String,
        val steps: List<String>,
    )

    private fun executeSafely(service: Any, context: Any, request: Any, spec: FeedbackRequestSpec): Boolean {
        return runCatching {
            val serviceType = executionServiceClass()
            val contextType = expressionContextClass()
            val requestType = executionRequestClass()
            val validate = serviceType.getMethod("validate", contextType, requestType)
            val execute = serviceType.getMethod("execute", contextType, requestType)
            val validation = validate.invoke(service, context, request)
            if (!isSuccess(validation)) {
                warn("LmCore ExecutionService validate failed: reason=${spec.reason}, traceId=${spec.traceId}, message=${message(validation)}")
                return false
            }
            val execution = execute.invoke(service, context, request)
            if (!isSuccess(execution)) {
                warn("LmCore ExecutionService execute failed: reason=${spec.reason}, traceId=${spec.traceId}, message=${message(execution)}")
                return false
            }
            true
        }.getOrElse {
            warn("LmCore ExecutionService feedback failed: reason=${spec.reason}, traceId=${spec.traceId}, error=${it.message}")
            false
        }
    }

    private fun resolveExecutionService(): Any? {
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            val serviceType = executionServiceClass() as Class<Any>
            Bukkit.getServicesManager().getRegistration(serviceType)?.provider
        }.getOrNull()
    }

    private fun buildExecutionRequest(spec: FeedbackRequestSpec): Any {
        val stepClass = executionStepClass()
        val parse = stepClass.getMethod("parse", String::class.java)
        val steps = spec.steps.map { parse.invoke(null, it) }
        return executionRequestClass()
            .getMethod("of", String::class.java, String::class.java, String::class.java, java.util.List::class.java)
            .invoke(null, spec.source, spec.reason, spec.traceId, steps)
    }

    private fun buildExpressionContext(player: Player, values: Map<String, String>): Any {
        val builder = expressionContextClass().getMethod("builder").invoke(null)
        builder.javaClass.getMethod("player", Player::class.java).invoke(builder, player)
        builder.javaClass.getMethod("values", Map::class.java).invoke(builder, values)
        return builder.javaClass.getMethod("build").invoke(builder)
    }

    private fun requireAllowedStep(step: String) {
        val trimmed = step.trim()
        require(trimmed.startsWith("[")) { "execution feedback step must start with [type]" }
        val end = trimmed.indexOf(']')
        require(end > 1) { "execution feedback step type is empty" }
        val type = normalizeType(trimmed.substring(1, end))
        require(type in allowedTypes) { "execution feedback step is not allowed: $type" }
    }

    private fun renderPercentPlaceholders(value: String, values: Map<String, String>): String {
        var rendered = value
        values.forEach { (key, replacement) ->
            rendered = rendered.replace("%$key%", replacement)
        }
        return rendered
    }

    private fun traceId(reason: String, playerId: UUID, values: Map<String, String>): String {
        val seed = values["trace_id"].takeUnless { it.isNullOrBlank() } ?: System.currentTimeMillis().toString()
        return "$SOURCE:$reason:$playerId:$seed"
    }

    private fun isSuccess(result: Any?): Boolean {
        return result != null && result.javaClass.getMethod("isSuccess").invoke(result) == true
    }

    private fun message(result: Any?): String {
        return result?.javaClass?.getMethod("getMessage")?.invoke(result)?.toString() ?: "null result"
    }

    private fun executionServiceClass(): Class<*> = Class.forName("cc.mcstory.lmcore.api.ExecutionService")
    private fun executionRequestClass(): Class<*> = Class.forName("cc.mcstory.lmcore.api.ExecutionRequest")
    private fun executionStepClass(): Class<*> = Class.forName("cc.mcstory.lmcore.api.ExecutionStep")
    private fun expressionContextClass(): Class<*> = Class.forName("cc.mcstory.lmcore.api.ExpressionContext")

    private fun normalizeType(value: String): String {
        return value.trim().lowercase(Locale.ENGLISH).replace("-", "").replace("_", "")
    }

    private fun normalizeKey(key: String): String {
        return key.trim().lowercase(Locale.ENGLISH).replace('-', '_')
    }

    private fun warn(message: String) {
        logger().warning("[LmVIP] $message")
    }

    private fun logger(): Logger {
        return runCatching { BukkitPlugin.getInstance().logger }.getOrDefault(Bukkit.getLogger())
    }
}
