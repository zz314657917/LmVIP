package cc.mcstory.lmvip

import cc.mcstory.lmvip.config.VipRuntimeConfig
import cc.mcstory.lmvip.integration.LuckPermsGroupSync
import cc.mcstory.lmvip.service.RewardService
import cc.mcstory.lmvip.service.VipService
import cc.mcstory.lmvip.storage.JdbcVipRepository

object LmVipServices {
    var config: VipRuntimeConfig? = null
        private set
    var repository: JdbcVipRepository? = null
        private set
    var groupSync: LuckPermsGroupSync? = null
        private set
    var rewardService: RewardService? = null
        private set
    var vipService: VipService? = null
        private set

    val ready: Boolean
        get() = vipService != null

    fun enable(
        config: VipRuntimeConfig,
        repository: JdbcVipRepository,
        groupSync: LuckPermsGroupSync,
        rewardService: RewardService,
        vipService: VipService
    ) {
        this.config = config
        this.repository = repository
        this.groupSync = groupSync
        this.rewardService = rewardService
        this.vipService = vipService
    }

    fun updateConfig(config: VipRuntimeConfig) {
        this.config = config
    }

    fun disable() {
        config = null
        repository = null
        groupSync = null
        rewardService = null
        vipService = null
    }
}
