import net.fabricmc.loom.task.RemapJarTask
import net.fabricmc.loom.task.RemapSourcesJarTask
import net.fabricmc.loom.task.RunGameTask
import org.gradle.kotlin.dsl.named
import kotlin.collections.component1
import kotlin.collections.component2

plugins {
    id("multiloader-platform")

    id("net.fabricmc.fabric-loom") version ("1.15.1")
}

base {
    archivesName = "sodium-fabric"
}

val configurationApiModJava: Configuration = configurations.create("apiJava") {
    isCanBeResolved = true
}

val configurationCommonModJava: Configuration = configurations.create("commonJava") {
    isCanBeResolved = true
}

val configurationFrapiModJava: Configuration = configurations.create("frapiJava") {
    isCanBeResolved = true
}

val configurationApiModSources: Configuration = configurations.create("apiSources") {
    isCanBeResolved = true
}

val configurationCommonModResources: Configuration = configurations.create("commonResources") {
    isCanBeResolved = true
}

val configurationFrapiModResources: Configuration = configurations.create("frapiResources") {
    isCanBeResolved = true
}

dependencies {
    configurationCommonModJava(project(path = ":common", configuration = "commonMainJava"))
    configurationApiModJava(project(path = ":common", configuration = "commonApiJava"))
    configurationCommonModJava(project(path = ":common", configuration = "commonBootJava"))
    if (BuildConfig.SUPPORT_FRAPI) configurationFrapiModJava(project(path = ":frapi", configuration = "frapiMainJava"))

    configurationApiModSources(project(path = ":common", configuration = "commonApiSources"))

    configurationCommonModResources(project(path = ":common", configuration = "commonMainResources"))
    configurationCommonModResources(project(path = ":common", configuration = "commonApiResources"))
    configurationCommonModResources(project(path = ":common", configuration = "commonBootResources"))
    if (BuildConfig.SUPPORT_FRAPI) configurationFrapiModResources(project(path = ":frapi", configuration = "frapiMainResources"))
}

sourceSets.apply {
    main {
        compileClasspath += configurationCommonModJava
        compileClasspath += configurationApiModJava
        runtimeClasspath += configurationCommonModJava
        runtimeClasspath += configurationApiModJava
        if (BuildConfig.SUPPORT_FRAPI) {
            runtimeClasspath += configurationFrapiModJava
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${BuildConfig.MINECRAFT_VERSION}")

    implementation("net.fabricmc:fabric-loader:${BuildConfig.FABRIC_LOADER_VERSION}")

    fun addEmbeddedFabricModule(name: String) {
        val module = fabricApi.module(name, BuildConfig.FABRIC_API_VERSION)
        implementation(module)
        include(module)
    }

    // Fabric API modules
    addEmbeddedFabricModule("fabric-api-base")
    addEmbeddedFabricModule("fabric-block-getter-api-v2")

    if (BuildConfig.SUPPORT_FRAPI) {
        addEmbeddedFabricModule("fabric-renderer-api-v1")
    }

    addEmbeddedFabricModule("fabric-rendering-fluids-v1")
    addEmbeddedFabricModule("fabric-resource-loader-v0")
    addEmbeddedFabricModule("fabric-resource-loader-v1")
    addEmbeddedFabricModule("fabric-transitive-access-wideners-v1")
}

// POSIX-ish quoting for the *inner* --args string that ngfx will parse.
fun q(s: String): String = buildString {
    append('"')
    for (c in s) when (c) {
        '\\', '"' -> { append('\\'); append(c) }
        else -> append(c)
    }
    append('"')
}

val ngfxBin = providers.gradleProperty("ngfx")
        .orElse("/opt/nvidia/nsight-graphics/host/linux-desktop-nomad-x64/ngfx.bin")

fun RunGameTask.buildJavaCommand(): Pair<String, String> {
    val javaExe = javaLauncher.get().executablePath.asFile.absolutePath
    val args = buildString {
        allJvmArgs.forEach { append(it).append(' ') }
        append("-cp ").append(classpath.asPath).append(' ')
        append(mainClass.get())
    }
    return javaExe to args
}

fun Exec.inheritRunEnvironment(run: RunGameTask) {
    run.environment.forEach { (k, v) ->
        environment(k, v)
    }
    workingDir = run.workingDir
    standardInput = System.`in`
}

tasks.register<Exec>("nsightGpuTrace") {
    group = "profiling"
    dependsOn("classes")

    val runTask = tasks.named<RunGameTask>("runClient").get()
    val (javaExe, javaArgs) = runTask.buildJavaCommand()

    environment("vblank_mode", "0")
    environment("NV_ALLOW_RAYTRACING_VALIDATION", "1")
    environment("MESA_VK_WSI_PRESENT_MODE", "immediate")

    inheritRunEnvironment(runTask)

    commandLine(
            "C:\\Program Files\\NVIDIA Corporation\\Nsight Graphics 2026.1.0\\host\\windows-desktop-nomad-x64\\ngfx.exe",
            "--activity=GPU Trace Profiler",
            "--exe=$javaExe",
            "--args=$javaArgs",
            "--dir=D:\\sodium-opengl\\fabric\\run",
            "--start-after-hotkey",
            "--multi-pass-metrics",
            "--auto-export",
            "--max-duration-ms=50"
    )
}


tasks.register<Exec>("nsightGraphicsCapture") {
    group = "profiling"
    dependsOn("classes")

    val runTask = tasks.named<RunGameTask>("runClient").get()
    val (javaExe, javaArgs) = runTask.buildJavaCommand()

    environment("NV_ALLOW_RAYTRACING_VALIDATION", "1")

    inheritRunEnvironment(runTask)

    commandLine(
            "C:\\Program Files\\NVIDIA Corporation\\Nsight Graphics 2025.5.0\\host\\windows-desktop-nomad-x64\\ngfx.exe",
            "--activity=Graphics Capture",
            "--exe=$javaExe",
            "--args=$javaArgs",
            "--dir=D:\\sodium-opengl\\fabric\\run",
            "--no-timeout"
    )

    /*
        commandLine(
            "\"C:\\Program Files\\NVIDIA Corporation\\Nsight Graphics 2025.5.0\\host\\windows-desktop-nomad-x64\\ngfx-capture.exe\"",
            "--exe=$javaExe",
            "--args=$javaArgs",
            "--wd=D:\\Aperture\\fabric\\run",
            "--no-vulkan-private-data-lookups",
            "--embed-logging-verbose",
            "--no-bundle-replayer",
            "--no-block-on-interfering-application",
    )
     */
}

loom {
    accessWidenerPath.set(file("src/main/resources/sodium-fabric.accesswidener"))

    mixin {
        useLegacyMixinAp = false
    }

    runs {
        named("client") {
            client()
            configName = "Fabric/Client"
            appendProjectPathToConfigName = false
            ideConfigGenerated(true)
            runDir("run")
            vmArg("-Dorg.lwjgl.system.stackSize=1024")
        }
    }
}

tasks {
    jar {
        from(configurationCommonModJava)
        from(configurationApiModJava)
        if (BuildConfig.SUPPORT_FRAPI) {
            from(configurationFrapiModJava)
        }
    }

    val apiJar = register<org.gradle.jvm.tasks.Jar>("apiJar") {
        archiveClassifier.set("api-dev")
        from(configurationApiModJava)
        from(sourceSets.main.get().resources)
        destinationDirectory.set(file(rootProject.layout.buildDirectory).resolve("api"))
    }

    val apiSourcesJar = register<org.gradle.jvm.tasks.Jar>("apiSourcesJar") {
        archiveClassifier.set("api-sources-dev")
        from(configurationApiModSources)
        from(sourceSets.main.get().resources)
        destinationDirectory.set(file(rootProject.layout.buildDirectory).resolve("api-sources"))
    }

    jar {
        destinationDirectory.set(file(rootProject.layout.buildDirectory).resolve("mods"))
    }

    processResources {
        from(configurationCommonModResources)
        if (BuildConfig.SUPPORT_FRAPI) {
            from(configurationFrapiModResources)
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group as String
            artifactId = rootProject.name + "-" + project.name
            version = version

            from(components["java"])
        }

        create<MavenPublication>("mavenApi") {
            groupId = project.group as String
            artifactId = rootProject.name + "-" + project.name + "-api"
            version = version

            artifact(tasks.named("apiJar")) {
                classifier = null
            }

            artifact(tasks.named("apiSourcesJar")) {
                classifier = "sources"
            }

            pom.packaging = "jar"
        }
    }
}