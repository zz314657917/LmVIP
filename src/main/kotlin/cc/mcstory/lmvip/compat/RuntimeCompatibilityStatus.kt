package cc.mcstory.lmvip.compat

import java.util.Properties

data class ArtifactMetadata(
    val target: String,
    val javaTarget: String,
) {
    companion object {
        const val LEGACY_TARGET = "1.12.2"
        const val MODERN_TARGET = "1.20.1"

        fun load(): ArtifactMetadata {
            val properties = Properties()
            ArtifactMetadata::class.java.classLoader
                .getResourceAsStream("lmvip-artifact.properties")
                ?.use { properties.load(it) }
            return ArtifactMetadata(
                target = properties.getProperty("target", "unknown"),
                javaTarget = properties.getProperty("javaTarget", "unknown"),
            )
        }
    }
}

data class RuntimeCompatibilityStatus(
    val artifactTarget: String,
    val artifactJavaTarget: String,
    val javaRuntime: String,
    val serverType: ServerType,
    val serverVersion: String,
    val bukkitVersion: String,
    val lmCore: DependencyState,
    val luckPerms: DependencyState,
    val placeholderApi: DependencyState,
    val verdict: CompatibilityVerdict,
) {
    fun summary(): String {
        return "artifact=$artifactTarget/java$artifactJavaTarget, runtimeJava=$javaRuntime, " +
            "server=$serverType/$bukkitVersion, deps=LmCore:$lmCore LuckPerms:$luckPerms PAPI:$placeholderApi, verdict=$verdict"
    }
}

enum class ServerType {
    PAPER,
    ARCLIGHT,
    CATSERVER,
    SPIGOT,
    CRAFTBUKKIT,
    UNKNOWN,
}

enum class DependencyState {
    ENABLED,
    DISABLED,
    MISSING,
}

enum class CompatibilityVerdict {
    SUPPORTED,
    DEGRADED,
    BLOCKED,
}
