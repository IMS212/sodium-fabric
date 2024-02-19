plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

architectury {
    platformSetupLoomIde()
    forge()
}

repositories {
    maven {
        url = uri("https://maven.neoforged.net/releases")
    }

    mavenLocal()
}

val common: Configuration by configurations.creating
val shadowCommon: Configuration by configurations.creating
val developmentForge: Configuration by configurations.getting

val MINECRAFT_VERSION: String by rootProject.extra
val NEOFORGE_VERSION: String by rootProject.extra
base.archivesName.set("sodium-forge")

loom {
    silentMojangMappingsLicense()

    accessWidenerPath = project(":common").loom.accessWidenerPath

    forge {
        convertAccessWideners = true

        mixinConfigs(
                "sodium.mixins.json"
        )
    }
}

configurations {
    compileOnly.configure { extendsFrom(common) }
    runtimeOnly.configure { extendsFrom(common) }
    developmentForge.extendsFrom(common)
}

tasks.shadowJar {
    exclude("fabric.mod.json")
    configurations = listOf(shadowCommon)
    archiveClassifier.set("dev-shadow")
}

tasks.remapJar {
    injectAccessWidener.set(true)
    inputFile.set(tasks.shadowJar.get().archiveFile)
    dependsOn(tasks.shadowJar)
    archiveClassifier.set(null as String?)
    atAccessWideners.add("sodium.accesswidener")
}

tasks.jar {
    archiveClassifier.set("dev")
}

components.getByName("java") {
    this as AdhocComponentWithVariants
    this.withVariantsFromConfiguration(project.configurations["shadowRuntimeElements"]) {
        skip()
    }
}

dependencies {
    forge("net.neoforged:forge:${NEOFORGE_VERSION}")

    include(implementation(group = "com.lodborg", name = "interval-tree", version = "1.0.0"))
    forgeRuntimeLibrary(group = "com.lodborg", name = "interval-tree", version = "1.0.0")
    compileOnly("io.github.llamalad7:mixinextras-common:0.3.5")
    annotationProcessor("io.github.llamalad7:mixinextras-common:0.3.5")
    include("io.github.llamalad7:mixinextras-forge:0.3.5")
    implementation("io.github.llamalad7:mixinextras-forge:0.3.5")
    common(project(":common", "namedElements")) { isTransitive = false }
    shadowCommon(project(":common", "transformProductionForge")) { isTransitive = false }
}

tasks.processResources {
    filesMatching("META-INF/mods.toml") {
        expand(mapOf("version" to project.version))
    }
}

tasks {
    jar {
        from("${rootProject.projectDir}/COPYING")
        from("${rootProject.projectDir}/COPYING.LESSER")

        manifest.attributes["Main-Class"] = "net.caffeinemc.mods.sodium.desktop.LaunchWarn"
    }
}