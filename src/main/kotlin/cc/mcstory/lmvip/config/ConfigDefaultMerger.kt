package cc.mcstory.lmvip.config

object ConfigDefaultMerger {

    data class PathInfo(
        val path: String,
        val section: Boolean,
    )

    fun missingLeafPaths(defaultPaths: List<PathInfo>, existingPaths: Set<String>): List<String> {
        return defaultPaths
            .filterNot { it.section }
            .map { it.path }
            .filterNot { existingPaths.contains(it) }
    }

    fun shouldMergeExistingResource(resource: String): Boolean {
        return !resource.equals("levels.yml", ignoreCase = true)
    }
}
