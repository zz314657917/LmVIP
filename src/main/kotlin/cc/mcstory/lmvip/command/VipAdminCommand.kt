package cc.mcstory.lmvip.command

import cc.mcstory.lmvip.LmVipServices
import cc.mcstory.lmvip.api.BukkitLmVipApi
import cc.mcstory.lmvip.config.VipConfigManager
import cc.mcstory.lmvip.integration.LmVipPlaceholderExpansion
import cc.mcstory.lmvip.model.PointDimension
import cc.mcstory.lmvip.model.RollbackTransactionResult
import cc.mcstory.lmvip.model.TransactionWriteResult
import cc.mcstory.lmvip.util.BukkitTasks
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.*

@CommandHeader(name = "vipadmin", aliases = ["lmvipadmin"], permission = "lmvip.admin")
object VipAdminCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ -> help(sender) }
    }

    @CommandBody
    val season = subCommand {
        dynamic(comment = "action") {
            dynamic(comment = "seasonId", optional = true) {
                dynamic(comment = "displayName", optional = true) {
                    execute<ProxyCommandSender> { sender, context, _ ->
                        handleSeason(sender, context.args().toList())
                    }
                }
                execute<ProxyCommandSender> { sender, context, _ -> handleSeason(sender, context.args().toList()) }
            }
            execute<ProxyCommandSender> { sender, context, _ -> handleSeason(sender, context.args().toList()) }
        }
    }

    @CommandBody
    val points = subCommand {
        dynamic(comment = "action") {
            dynamic(comment = "playerOrRollbackId", optional = true) {
                dynamic(comment = "arg1", optional = true) {
                    dynamic(comment = "arg2", optional = true) {
                        dynamic(comment = "arg3", optional = true) {
                            dynamic(comment = "arg4", optional = true) {
                                dynamic(comment = "arg5", optional = true) {
                                    execute<ProxyCommandSender> { sender, context, _ -> handlePoints(sender, context.args().toList()) }
                                }
                                execute<ProxyCommandSender> { sender, context, _ -> handlePoints(sender, context.args().toList()) }
                            }
                            execute<ProxyCommandSender> { sender, context, _ -> handlePoints(sender, context.args().toList()) }
                        }
                        execute<ProxyCommandSender> { sender, context, _ -> handlePoints(sender, context.args().toList()) }
                    }
                    execute<ProxyCommandSender> { sender, context, _ -> handlePoints(sender, context.args().toList()) }
                }
                execute<ProxyCommandSender> { sender, context, _ -> handlePoints(sender, context.args().toList()) }
            }
        }
    }

    @CommandBody
    val info = subCommand {
        dynamic(comment = "player") {
            dynamic(comment = "seasonId", optional = true) {
                execute<ProxyCommandSender> { sender, context, _ -> handleInfo(sender, context.args().toList()) }
            }
            execute<ProxyCommandSender> { sender, context, _ -> handleInfo(sender, context.args().toList()) }
        }
    }

    @CommandBody
    val sync = subCommand {
        dynamic(comment = "player") {
            execute<ProxyCommandSender> { sender, context, _ ->
                val player = offline(context.args().getOrNull(1) ?: return@execute)
                val service = LmVipServices.vipService ?: return@execute sender.sendMessage(msg("common.not-ready"))
                BukkitTasks.async({ service.refreshAndSync(player) }) { result ->
                    val snapshot = result.getOrElse { return@async sender.sendMessage(msg("admin.sync-failed", "error" to it.message)) }
                    sender.sendMessage(msg("admin.sync-success", "player" to player.name, "level" to snapshot.vipLevel))
                }
            }
        }
    }

    @CommandBody
    val recalc = subCommand {
        dynamic(comment = "player") {
            execute<ProxyCommandSender> { sender, context, _ -> handleInfo(sender, listOf("info", context.args()[1])) }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            val plugin = taboolib.platform.BukkitPlugin.getInstance()
            val current = LmVipServices.vipService
            VipConfigManager.ensureDefaults(plugin)
            val loaded = VipConfigManager.load(plugin)
            LmVipServices.updateConfig(loaded)
            current?.updateConfig(loaded)
            (LmVipServices.api as? BukkitLmVipApi)?.clearInFlight()
            LmVipPlaceholderExpansion.clear()
            sender.sendMessage(msg("admin.reload-success"))
        }
    }

    @CommandBody
    val cache = subCommand {
        dynamic(comment = "action") {
            dynamic(comment = "player", optional = true) {
                execute<ProxyCommandSender> { sender, context, _ -> handleCache(sender, context.args().toList()) }
            }
            execute<ProxyCommandSender> { sender, context, _ -> handleCache(sender, context.args().toList()) }
        }
    }

    private fun handleSeason(sender: ProxyCommandSender, args: List<String>) {
        val repository = LmVipServices.repository ?: return sender.sendMessage(msg("common.not-ready"))
        val action = args.getOrNull(1)?.lowercase() ?: return helpSeason(sender)
        val operator = sender.name
        BukkitTasks.async({
            when (action) {
                "create" -> {
                    val id = requireArg(args, 2, "seasonId")
                    val name = args.getOrNull(3) ?: id
                    raw("admin.season.create", "id" to id, "result" to repository.createSeason(id, name, operator))
                }
                "activate" -> {
                    val id = requireArg(args, 2, "seasonId")
                    raw("admin.season.activate", "id" to id, "result" to repository.activateSeason(id, operator))
                }
                "start" -> {
                    val id = requireArg(args, 2, "seasonId")
                    val name = args.getOrNull(3) ?: id
                    raw("admin.season.start", "id" to id, "result" to repository.startSeason(id, name, operator))
                }
                "current" -> repository.activeSeason()
                    ?.let { raw("admin.season.current", "id" to it.seasonId, "name" to it.displayName) }
                    ?: raw("reward.no-active-season")
                "list" -> repository.listSeasons().joinToString("\n") {
                    raw("admin.season.list-line", "mark" to if (it.active) "*" else "-", "id" to it.seasonId, "name" to it.displayName)
                }
                "info" -> {
                    val id = requireArg(args, 2, "seasonId")
                    repository.listSeasons().firstOrNull { it.seasonId == id }
                        ?.let { raw("admin.season.info", "id" to it.seasonId, "name" to it.displayName, "active" to it.active) }
                        ?: raw("admin.season.not-found")
                }
                else -> raw("admin.season.unknown")
            }
        }) { result -> sender.sendMessage(result.getOrElse { msg("admin.operation-failed", "error" to it.message) }) }
    }

    private fun handlePoints(sender: ProxyCommandSender, args: List<String>) {
        val service = LmVipServices.vipService ?: return sender.sendMessage(msg("common.not-ready"))
        val action = args.getOrNull(1)?.lowercase() ?: return helpPoints(sender)
        val operator = sender.name
        if (action == "rollback") {
            val id = args.getOrNull(2)?.toLongOrNull() ?: return sender.sendMessage(msg("admin.points.rollback-usage"))
            val reason = args.getOrNull(3) ?: "rollback"
            BukkitTasks.async({ service.rollback(id, operator, reason) }) { result ->
                sender.sendMessage(rollbackMessage(result))
            }
            return
        }
        if (action == "season") {
            val op = args.getOrNull(2)?.lowercase() ?: return helpPoints(sender)
            val player = offline(args.getOrNull(3) ?: return helpPoints(sender))
            val seasonId = args.getOrNull(4) ?: return helpPoints(sender)
            val amount = args.getOrNull(5)?.toLongOrNull()
            val reason = if (op == "reset") {
                args.getOrNull(5) ?: "season-reset"
            } else {
                args.getOrNull(6) ?: "season-$op"
            }
            BukkitTasks.async({
                when (op) {
                    "add" -> service.adjustSeason(player, seasonId, null, requireNotNull(amount), operator, reason)
                    "take" -> service.adjustSeason(player, seasonId, null, -requireNotNull(amount), operator, reason)
                    "set" -> service.adjustSeason(player, seasonId, requireNotNull(amount), null, operator, reason)
                    "reset" -> service.adjustSeason(player, seasonId, 0L, null, operator, reason)
                    else -> null
                }
            }) { result -> sender.sendMessage(transactionMessage(raw("admin.transaction.label.season"), result)) }
            return
        }
        val player = offline(args.getOrNull(2) ?: return helpPoints(sender))
        if (action == "add" && args.getOrNull(3).equals("recharge", true)) {
            val amount = args.getOrNull(4)?.toLongOrNull() ?: return helpPoints(sender)
            val source = args.getOrNull(5) ?: return helpPoints(sender)
            val order = args.getOrNull(6) ?: return helpPoints(sender)
            val reason = args.getOrNull(7) ?: "recharge"
            BukkitTasks.async({ service.addRecharge(player, amount, source, order, operator, reason) }) { result ->
                sender.sendMessage(transactionMessage(raw("admin.transaction.label.recharge"), result, raw("admin.transaction.text.duplicate-or-failed")))
            }
            return
        }
        val dimension = PointDimension.parse(args.getOrNull(3) ?: "") ?: return helpPoints(sender)
        val amount = args.getOrNull(4)?.toLongOrNull()
        val reason = if (action == "reset") {
            args.getOrNull(4) ?: "reset-${dimension.dbKey}"
        } else {
            args.getOrNull(5) ?: "$action-${dimension.dbKey}"
        }
        BukkitTasks.async({
            when (action) {
                "add" -> service.adjustDimension(player, dimension, null, requireNotNull(amount), operator, reason)
                "take" -> service.adjustDimension(player, dimension, null, -requireNotNull(amount), operator, reason)
                "set" -> service.adjustDimension(player, dimension, requireNotNull(amount), null, operator, reason)
                "reset" -> service.adjustDimension(player, dimension, 0L, null, operator, reason)
                else -> null
            }
        }) { result -> sender.sendMessage(transactionMessage(raw("admin.transaction.label.points"), result)) }
    }

    private fun handleInfo(sender: ProxyCommandSender, args: List<String>) {
        val service = LmVipServices.vipService ?: return sender.sendMessage(msg("common.not-ready"))
        val player = offline(args.getOrNull(1) ?: return sender.sendMessage(msg("admin.info-usage")))
        val seasonId = args.getOrNull(2)
        BukkitTasks.async({ service.snapshot(player, seasonId, force = true) }) { result ->
            val snapshot = result.getOrElse { return@async sender.sendMessage(msg("admin.info-failed", "error" to it.message)) }
            sender.sendMessage(msg("admin.info.header", "player" to player.name))
            sender.sendMessage(raw("admin.info.season", "id" to (snapshot.seasonId ?: "-"), "name" to (snapshot.seasonName ?: "")))
            sender.sendMessage(raw("admin.info.vip", "level_name" to snapshot.vipLevelName, "total" to snapshot.totalPoints))
            sender.sendMessage(raw("admin.info.points", "season" to snapshot.seasonPoints, "monthly" to snapshot.monthlyPoints, "daily" to snapshot.dailyPoints))
        }
    }

    private fun handleCache(sender: ProxyCommandSender, args: List<String>) {
        val service = LmVipServices.vipService ?: return sender.sendMessage(msg("common.not-ready"))
        when (args.getOrNull(1)?.lowercase()) {
            "stats" -> {
                val stats = LmVipPlaceholderExpansion.stats()
                val apiInFlight = (LmVipServices.api as? BukkitLmVipApi)?.inFlightCount() ?: 0
                LmVipServices.messageList(
                    "admin.cache.stats",
                    "papi_size" to stats.size,
                    "papi_refreshing" to stats.refreshing,
                    "api_inflight" to apiInFlight,
                    "hits" to stats.hits,
                    "misses" to stats.misses,
                    "stale_hits" to stats.staleHits,
                    "refresh_success" to stats.refreshSuccess,
                    "refresh_failure" to stats.refreshFailure,
                    "refresh_skipped" to stats.refreshSkipped,
                    "last_error" to (stats.lastError ?: "-")
                ).forEach(sender::sendMessage)
            }
            "clear" -> {
                val playerName = args.getOrNull(2)
                if (playerName == null) {
                    service.clearCache()
                    (LmVipServices.api as? BukkitLmVipApi)?.clearInFlight()
                    sender.sendMessage(msg("admin.cache.clear-all"))
                } else {
                    val player = offline(playerName)
                    service.invalidateCache(player.uniqueId)
                    (LmVipServices.api as? BukkitLmVipApi)?.clearInFlight(player.uniqueId)
                    sender.sendMessage(msg("admin.cache.clear-player", "player" to playerName))
                }
            }
            "warm" -> {
                val playerName = args.getOrNull(2) ?: return sender.sendMessage(msg("admin.cache.usage"))
                val player = offline(playerName)
                sender.sendMessage(msg("admin.cache.warm-submitted", "player" to playerName))
                BukkitTasks.async({ service.snapshot(player, force = true) }) { result ->
                    result.fold(
                        onSuccess = {
                            LmVipPlaceholderExpansion.refresh(it.playerId, it.playerName)
                            sender.sendMessage(msg("admin.cache.warm-success", "player" to playerName, "level" to it.vipLevel))
                        },
                        onFailure = {
                            sender.sendMessage(msg("admin.cache.warm-failed", "player" to playerName, "error" to (it.message ?: it.javaClass.simpleName)))
                        }
                    )
                }
            }
            null -> sender.sendMessage(msg("admin.cache.usage"))
            else -> sender.sendMessage(msg("admin.cache.unknown"))
        }
    }

    private fun offline(name: String): OfflinePlayer = Bukkit.getOfflinePlayer(name)

    private fun requireArg(args: List<String>, index: Int, label: String): String {
        return args.getOrNull(index) ?: throw IllegalArgumentException("missing $label")
    }

    private fun <T : TransactionWriteResult?> transactionMessage(prefix: String, result: Result<T>, emptyText: String? = null): String {
        val noChangeText = emptyText ?: raw("admin.transaction.text.no-change")
        return result.fold(
            onSuccess = { writeResult ->
                when (writeResult) {
                    is TransactionWriteResult.Inserted -> msg("admin.transaction.inserted", "prefix" to prefix, "id" to writeResult.transactionId)
                    is TransactionWriteResult.DuplicateOrder -> msg("admin.transaction.duplicate", "prefix" to prefix, "source" to writeResult.source, "order" to writeResult.orderId)
                    TransactionWriteResult.NoChange,
                    null -> msg("admin.transaction.no-change", "prefix" to prefix, "text" to noChangeText)
                }
            },
            onFailure = { msg("admin.transaction.failed", "prefix" to prefix, "error" to (it.message ?: it.javaClass.simpleName)) }
        )
    }

    private fun rollbackMessage(result: Result<RollbackTransactionResult?>): String {
        return result.fold(
            onSuccess = { rollback ->
                rollback?.writeResult?.let {
                    transactionMessage(raw("admin.transaction.label.rollback"), Result.success(it), raw("admin.transaction.text.duplicate-or-failed"))
                }
                    ?: msg("admin.rollback.not-found")
            },
            onFailure = { msg("admin.rollback.failed", "error" to (it.message ?: it.javaClass.simpleName)) }
        )
    }

    private fun help(sender: ProxyCommandSender) {
        LmVipServices.messageList("admin.help").forEach(sender::sendMessage)
    }

    private fun helpSeason(sender: ProxyCommandSender) = sender.sendMessage(msg("admin.season.usage"))
    private fun helpPoints(sender: ProxyCommandSender) = sender.sendMessage(msg("admin.points.usage"))

    private fun msg(key: String, vararg placeholders: Pair<String, Any?>): String {
        return LmVipServices.message(key, *placeholders)
    }

    private fun raw(key: String, vararg placeholders: Pair<String, Any?>): String {
        return LmVipServices.rawMessage(key, *placeholders)
    }
}
