package cc.mcstory.lmvip.compat

import org.bukkit.Bukkit

object PlatformCompatibility {
    fun detect(artifact: ArtifactMetadata = ArtifactMetadata.load()): RuntimeCompatibilityStatus {
        val serverVersion = runCatching { Bukkit.getVersion() }.getOrDefault("unknown")
        val bukkitVersion = runCatching { Bukkit.getBukkitVersion() }.getOrDefault("unknown")
        val serverType = detectServerType(serverVersion)
        val javaRuntime = System.getProperty("java.version", "unknown")
        val lmCoreState = pluginState("LmCore")
        val luckPermsState = pluginState("LuckPerms")
        val placeholderApiState = pluginState("PlaceholderAPI")
        val verdict = verdict(artifact, bukkitVersion, serverType, lmCoreState, luckPermsState)
        return RuntimeCompatibilityStatus(
            artifactTarget = artifact.target,
            artifactJavaTarget = artifact.javaTarget,
            javaRuntime = javaRuntime,
            serverType = serverType,
            serverVersion = serverVersion,
            bukkitVersion = bukkitVersion,
            lmCore = lmCoreState,
            luckPerms = luckPermsState,
            placeholderApi = placeholderApiState,
            verdict = verdict,
        )
    }

    private fun detectServerType(version: String): ServerType {
        val normalized = version.lowercase()
        return when {
            "arclight" in normalized -> ServerType.ARCLIGHT
            "paper" in normalized -> ServerType.PAPER
            "catserver" in normalized -> ServerType.CATSERVER
            "spigot" in normalized -> ServerType.SPIGOT
            "craftbukkit" in normalized -> ServerType.CRAFTBUKKIT
            else -> ServerType.UNKNOWN
        }
    }

    private fun pluginState(name: String): DependencyState {
        val plugin = Bukkit.getPluginManager().getPlugin(name)
        return when {
            plugin == null -> DependencyState.MISSING
            plugin.isEnabled -> DependencyState.ENABLED
            else -> DependencyState.DISABLED
        }
    }

    private fun verdict(
        artifact: ArtifactMetadata,
        bukkitVersion: String,
        serverType: ServerType,
        lmCore: DependencyState,
        luckPerms: DependencyState,
    ): CompatibilityVerdict {
        if (lmCore != DependencyState.ENABLED || luckPerms != DependencyState.ENABLED) {
            return CompatibilityVerdict.BLOCKED
        }
        if (artifact.target == ArtifactMetadata.MODERN_TARGET && !bukkitVersion.startsWith("1.20.1")) {
            return CompatibilityVerdict.DEGRADED
        }
        if (artifact.target == ArtifactMetadata.LEGACY_TARGET && !bukkitVersion.startsWith("1.12.2")) {
            return CompatibilityVerdict.DEGRADED
        }
        if (serverType == ServerType.ARCLIGHT) {
            return CompatibilityVerdict.DEGRADED
        }
        return CompatibilityVerdict.SUPPORTED
    }
}
