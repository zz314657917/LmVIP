package cc.mcstory.lmvip.service

import cc.mcstory.lmvip.model.ClaimDispatchStatus

object ClaimDispatchPolicy {
    fun blocksPlayerClaim(status: ClaimDispatchStatus): Boolean = when (status) {
        ClaimDispatchStatus.PENDING,
        ClaimDispatchStatus.CLAIMED,
        ClaimDispatchStatus.FAILED -> true
    }

    fun canRetry(status: ClaimDispatchStatus): Boolean = status == ClaimDispatchStatus.FAILED

    fun canReset(status: ClaimDispatchStatus): Boolean = status == ClaimDispatchStatus.PENDING || status == ClaimDispatchStatus.FAILED
}
