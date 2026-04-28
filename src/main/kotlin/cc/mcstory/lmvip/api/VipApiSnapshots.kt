package cc.mcstory.lmvip.api

import cc.mcstory.lmvip.model.VipSnapshot

object VipApiSnapshots {
    fun from(snapshot: VipSnapshot): VipApiSnapshot {
        return VipApiSnapshot(
            playerId = snapshot.playerId,
            playerName = snapshot.playerName,
            seasonId = snapshot.seasonId,
            seasonName = snapshot.seasonName,
            totalPoints = snapshot.totalPoints,
            seasonPoints = snapshot.seasonPoints,
            monthlyPoints = snapshot.monthlyPoints,
            dailyPoints = snapshot.dailyPoints,
            vipLevel = snapshot.vipLevel,
            vipLevelName = snapshot.vipLevelName,
            nextLevelNeed = snapshot.nextLevelNeed,
        )
    }
}
