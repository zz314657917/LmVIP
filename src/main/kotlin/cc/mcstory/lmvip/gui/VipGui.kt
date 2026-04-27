package cc.mcstory.lmvip.gui

import cc.mcstory.lmvip.LmVipServices
import cc.mcstory.lmvip.config.GuiItemConfig
import cc.mcstory.lmvip.config.VipConfigManager
import cc.mcstory.lmvip.model.ClaimStatus
import cc.mcstory.lmvip.model.ClaimType
import cc.mcstory.lmvip.model.VipLevel
import cc.mcstory.lmvip.model.VipSnapshot
import cc.mcstory.lmvip.util.BukkitTasks
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

object VipGui {

    fun open(player: Player) {
        val service = LmVipServices.vipService ?: run {
            player.sendMessage(LmVipServices.message("common.not-ready"))
            return
        }
        BukkitTasks.async({
            val snapshot = service.snapshot(player, force = true)
            val statuses = ClaimType.values().associateWith { service.rewards.status(snapshot, it) }
            GuiState(snapshot, statuses)
        }) { result ->
            val state = result.getOrElse {
                player.sendMessage(LmVipServices.message("gui.read-failed", "error" to it.message))
                return@async
            }
            val config = LmVipServices.config ?: return@async
            val gui = config.gui
            player.openMenu<Chest>(gui.title) {
                rows(gui.rows)
                set(gui.slots.status, item(gui.items["status"], Material.BOOK, listOf(
                    config.language.raw("gui.status.season", "season" to (state.snapshot.seasonName ?: config.language.raw("common.not-set"))),
                    config.language.raw("gui.status.level", "level_name" to state.snapshot.vipLevelName),
                    config.language.raw("gui.status.total", "total" to state.snapshot.totalPoints),
                    config.language.raw("gui.status.season-points", "season" to state.snapshot.seasonPoints),
                    config.language.raw("gui.status.monthly", "monthly" to state.snapshot.monthlyPoints),
                    config.language.raw("gui.status.daily", "daily" to state.snapshot.dailyPoints),
                    config.language.raw("gui.status.next", "need" to state.snapshot.nextLevelNeed)
                ))) { isCancelled = true }

                set(gui.slots.daily, rewardItem(ClaimType.DAILY, state.statuses.getValue(ClaimType.DAILY), gui.items["daily"])) {
                    isCancelled = true
                    player.closeInventory()
                    claim(player, ClaimType.DAILY)
                }
                set(gui.slots.weekly, rewardItem(ClaimType.WEEKLY, state.statuses.getValue(ClaimType.WEEKLY), gui.items["weekly"])) {
                    isCancelled = true
                    player.closeInventory()
                    claim(player, ClaimType.WEEKLY)
                }
                set(gui.slots.monthly, rewardItem(ClaimType.MONTHLY, state.statuses.getValue(ClaimType.MONTHLY), gui.items["monthly"])) {
                    isCancelled = true
                    player.closeInventory()
                    claim(player, ClaimType.MONTHLY)
                }

                val slots = gui.slots.levels
                config.levels.take(slots.size).forEachIndexed { index, level ->
                    set(slots[index], levelItem(level, state.snapshot.vipLevel >= level.level, gui.items["level"])) { isCancelled = true }
                }
            }
        }
    }

    private fun claim(player: Player, type: ClaimType) {
        val service = LmVipServices.vipService ?: return
        BukkitTasks.async({
            val snapshot = service.snapshot(player, force = true)
            service.rewards.claim(player, snapshot, type)
        }) { result ->
            val operation = result.getOrElse {
                player.sendMessage(LmVipServices.message("command.vip.claim-failed", "error" to it.message))
                return@async
            }
            player.sendMessage((LmVipServices.config?.messagePrefix ?: "") + operation.message)
        }
    }

    private fun rewardItem(type: ClaimType, status: ClaimStatus, itemConfig: GuiItemConfig?): ItemStack {
        val fallbackName = when (type) {
            ClaimType.DAILY -> "&a日累充奖励"
            ClaimType.WEEKLY -> "&bVIP 周礼包"
            ClaimType.MONTHLY -> "&d月累充奖励"
        }
        return item(itemConfig, Material.CHEST, listOf(
            LmVipServices.rawMessage("gui.reward.status", "status" to status.reason),
            if (status.available && !status.claimed) {
                LmVipServices.rawMessage("gui.reward.click")
            } else {
                LmVipServices.rawMessage("gui.reward.unavailable")
            }
        ), fallbackName)
    }

    private fun levelItem(level: VipLevel, owned: Boolean, itemConfig: GuiItemConfig?): ItemStack {
        val lore = mutableListOf<String>()
        lore += LmVipServices.rawMessage("gui.level.total", "total" to level.totalPoints)
        lore += LmVipServices.rawMessage("gui.level.group", "group" to level.group)
        lore += ""
        lore += level.benefits
        lore += ""
        lore += if (owned) LmVipServices.rawMessage("gui.level.owned") else LmVipServices.rawMessage("gui.level.locked")
        val name = itemConfig?.name?.replace("%level_name%", level.name) ?: level.name
        return item(material(itemConfig?.material, Material.PAPER), name, lore)
    }

    private fun item(itemConfig: GuiItemConfig?, fallbackMaterial: Material, lore: List<String>, fallbackName: String? = null): ItemStack {
        val material = material(itemConfig?.material, fallbackMaterial)
        val name = itemConfig?.name ?: fallbackName ?: "&f"
        return item(material, name, lore)
    }

    private fun item(material: Material, name: String, lore: List<String>): ItemStack {
        val stack = ItemStack(material)
        val meta = stack.itemMeta ?: return stack
        meta.setDisplayName(VipConfigManager.color(name))
        meta.lore = lore.map { VipConfigManager.color(it) }
        stack.itemMeta = meta
        return stack
    }

    private fun material(name: String?, fallback: Material): Material {
        return name?.let { Material.matchMaterial(it.uppercase()) } ?: fallback
    }

    private data class GuiState(
        val snapshot: VipSnapshot,
        val statuses: Map<ClaimType, ClaimStatus>,
    )
}
