import io.izzel.taboolib.gradle.Basic
import io.izzel.taboolib.gradle.Bukkit
import io.izzel.taboolib.gradle.BukkitHook
import io.izzel.taboolib.gradle.BukkitUI
import io.izzel.taboolib.gradle.BukkitUtil
import io.izzel.taboolib.gradle.CommandHelper
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

plugins {
    base
    id("io.izzel.taboolib") version "2.0.27" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.0" apply false
}

group = "cc.mcstory"
version = "1.0.0"

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenLocal()
        mavenCentral()
        maven {
            name = "papermc"
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
    }
}

fun javaExecutableFromHome(home: String?): File? {
    if (home.isNullOrBlank()) return null
    val executable = File(home, "bin/java.exe")
    return executable.takeIf { it.isFile }
}

fun javaExecutableFromPath(): File? {
    val path = System.getenv("PATH").orEmpty()
    return path.split(File.pathSeparator)
        .asSequence()
        .filter { it.isNotBlank() }
        .map { File(it, "java.exe") }
        .firstOrNull { it.isFile }
}

fun modernTestJavaExecutable(): File? {
    return javaExecutableFromHome(System.getenv("LMVIP_JAVA17_HOME"))
        ?: javaExecutableFromHome(System.getenv("JAVA17_HOME"))
        ?: javaExecutableFromHome(System.getenv("JDK17_HOME"))
        ?: javaExecutableFromPath()
}

fun stripUnsupportedPluginMetadata(jarFile: File) {
    val tempFile = File(jarFile.parentFile, "${jarFile.name}.tmp")
    ZipInputStream(jarFile.inputStream().buffered()).use { input ->
        ZipOutputStream(tempFile.outputStream().buffered()).use { output ->
            var entry = input.nextEntry
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (entry != null) {
                output.putNextEntry(ZipEntry(entry.name).also { it.time = entry.time })
                if (!entry.isDirectory) {
                    if (entry.name == "plugin.yml") {
                        val filtered = input.readBytes()
                            .toString(Charsets.UTF_8)
                            .lineSequence()
                            .filterNot { it.trim().startsWith("folia-supported:") }
                            .joinToString("\n", postfix = "\n")
                        output.write(filtered.toByteArray(Charsets.UTF_8))
                    } else {
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
                output.closeEntry()
                input.closeEntry()
                entry = input.nextEntry
            }
        }
    }
    Files.move(tempFile.toPath(), jarFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
}

fun Project.configureLmVipModule(
    artifactTarget: String,
    archiveName: String,
    javaVersion: JavaVersion,
    jvmTarget: JvmTarget,
    bukkitApiVersion: String?,
) {
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "io.izzel.taboolib")

    extensions.configure<SourceSetContainer>("sourceSets") {
        named("main") {
            java.srcDirs(rootProject.file("src/main/kotlin"))
            resources.srcDirs(rootProject.file("src/main/resources"))
            if (project.name == "lmvip-modern") {
                java.srcDir(rootProject.file("src/modern/kotlin"))
                resources.srcDir(rootProject.file("src/modern/resources"))
            }
        }
        named("test") {
            java.srcDirs(rootProject.file("src/test/kotlin"))
            resources.srcDirs(rootProject.file("src/test/resources"))
        }
    }

    extensions.configure<JavaPluginExtension>("java") {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    extensions.configure<io.izzel.taboolib.gradle.TabooLibExtension>("taboolib") {
        env {
            install(Basic)
            install(Bukkit)
            install(BukkitHook)
            install(BukkitUI)
            install(BukkitUtil)
            install(CommandHelper)
        }
        description {
            name = "LmVIP"
            desc("Season based VIP ledger and rewards system.")
            bukkitApi(bukkitApiVersion)
            contributors {
                name("Administrator")
            }
            dependencies {
                name("LmCore").forceDepend()
                name("LuckPerms").forceDepend()
                name("PlaceholderAPI").optional(true)
            }
        }
        version {
            taboolib = "6.2.4-8d51195"
        }
    }

    dependencies {
        "compileOnly"(kotlin("stdlib"))
        "compileOnly"("cc.mcstory:lm-core:1.1.0-SNAPSHOT")

        if (project.name == "lmvip-modern") {
            "compileOnly"("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
            "testCompileOnly"("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
            "testRuntimeOnly"("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
        } else {
            "compileOnly"("ink.ptms.core:v12004:12004:mapped")
            "compileOnly"("ink.ptms.core:v12004:12004:universal")
            "testCompileOnly"("ink.ptms.core:v12004:12004:mapped")
            "testCompileOnly"("ink.ptms.core:v12004:12004:universal")
            "testRuntimeOnly"("ink.ptms.core:v12004:12004:mapped")
            "testRuntimeOnly"("ink.ptms.core:v12004:12004:universal")
        }

        "testImplementation"(kotlin("test"))
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.10.2")
        "testImplementation"("com.h2database:h2:2.2.224")
        "testImplementation"("com.google.guava:guava:32.1.3-jre")
        "testImplementation"("cc.mcstory:lm-core:1.1.0-SNAPSHOT")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        sourceCompatibility = javaVersion.toString()
        targetCompatibility = javaVersion.toString()
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            this.jvmTarget.set(jvmTarget)
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }

    tasks.withType<ProcessResources>().configureEach {
        filesMatching("lmvip-artifact.properties") {
            expand(
                "artifactTarget" to artifactTarget,
                "artifactJavaTarget" to javaVersion.majorVersion,
            )
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        if (project.name == "lmvip-modern") {
            val modernJava = modernTestJavaExecutable()
                ?: throw GradleException("lmvip-modern tests require Java 17. Set LMVIP_JAVA17_HOME, JAVA17_HOME, JDK17_HOME, or put Java 17 java.exe on PATH.")
            executable = modernJava.absolutePath
        }
    }

    tasks.withType<Jar>().configureEach {
        archiveFileName.set(archiveName)
        manifest {
            attributes(
                "LmVIP-Artifact-Target" to artifactTarget,
                "LmVIP-Java-Target" to javaVersion.majorVersion,
            )
        }
    }

    val normalizePluginMetadata = tasks.register("normalizePluginMetadata") {
        group = "build"
        description = "Remove unsupported plugin.yml metadata from the final TabooLib jar."
        dependsOn("taboolibMainTask")
        outputs.upToDateWhen { false }
        doLast {
            stripUnsupportedPluginMetadata(layout.buildDirectory.file("libs/$archiveName").get().asFile)
        }
    }
    tasks.named("assemble").configure {
        dependsOn(normalizePluginMetadata)
    }
}

project(":lmvip-legacy") {
    configureLmVipModule(
        artifactTarget = "1.12.2",
        archiveName = "LmVIP-1.12.2.jar",
        javaVersion = JavaVersion.VERSION_1_8,
        jvmTarget = JvmTarget.JVM_1_8,
        bukkitApiVersion = null,
    )
}

project(":lmvip-modern") {
    configureLmVipModule(
        artifactTarget = "1.20.1",
        archiveName = "LmVIP-1.20.1.jar",
        javaVersion = JavaVersion.VERSION_17,
        jvmTarget = JvmTarget.JVM_17,
        bukkitApiVersion = "1.20",
    )
}
