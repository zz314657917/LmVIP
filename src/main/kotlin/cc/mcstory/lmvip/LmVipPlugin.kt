package cc.mcstory.lmvip

import cc.mcstory.lmvip.config.VipConfigManager
import cc.mcstory.lmvip.integration.LuckPermsGroupSync
import cc.mcstory.lmvip.storage.JdbcVipRepository
import cc.mcstory.lmvip.service.RewardService
import cc.mcstory.lmvip.service.VipService
import org.bukkit.Bukkit
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.info
import taboolib.platform.BukkitPlugin

object LmVipPlugin : Plugin() {

    override fun onEnable() {
        try {
            val plugin = BukkitPlugin.getInstance()
            VipConfigManager.ensureDefaults(plugin)
            val config = VipConfigManager.load(plugin)
            val repository = JdbcVipRepository.fromLmCore(config.databaseProfile, config.periods)
            repository.initialize()

            val groupSync = LuckPermsGroupSync(config.levels)
            val rewardService = RewardService(config, repository)
            val vipService = VipService(config, repository, groupSync, rewardService)
            LmVipServices.enable(config, repository, groupSync, rewardService, vipService)
            info("[LmVIP] enabled with LmCore database profile '${config.databaseProfile}'.")
        } catch (exception: Exception) {
            exception.printStackTrace()
            Bukkit.getPluginManager().disablePlugin(BukkitPlugin.getInstance())
        }
    }

    override fun onDisable() {
        LmVipServices.disable()
        info("[LmVIP] disabled.")
    }
}
