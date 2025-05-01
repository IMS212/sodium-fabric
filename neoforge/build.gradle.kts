plugins {
    id("multiloader-platform")

    id("net.neoforged.moddev") version("2.0.42-beta")
}

base {
    archivesName = "sodium-neoforge"
}

repositories {
    maven("https://maven.pkg.github.com/ims212/FRAPI-Testing") {
        credentials {
            username = "IMS212"
            // Read only token
            password = "ghp_" + "DEuGv0Z56vnSOYKLCXdsS9svK4nb9K39C1Hn"
        }
    }
    maven {
        name = "TauMC"
        url = uri("https://maven.taumc.org/releases")
    }
    maven("https://maven.su5ed.dev/releases")
    maven("https://maven.neoforged.net/releases/")
}

sourceSets {
    create("service")
}

val configurationCommonModJava: Configuration = configurations.create("commonModJava") {
    isCanBeResolved = true
}
val configurationCommonModResources: Configuration = configurations.create("commonModResources") {
    isCanBeResolved = true
}

val configurationCommonServiceJava: Configuration = configurations.create("commonServiceJava") {
    isCanBeResolved = true
}
val configurationCommonServiceResources: Configuration = configurations.create("commonServiceResources") {
    isCanBeResolved = true
}

dependencies {
    configurationCommonModJava(project(path = ":common", configuration = "commonMainJava"))
    configurationCommonModJava(project(path = ":common", configuration = "commonApiJava"))
    configurationCommonServiceJava(project(path = ":common", configuration = "commonEarlyLaunchJava"))

    configurationCommonModResources(project(path = ":common", configuration = "commonMainResources"))
    configurationCommonModResources(project(path = ":common", configuration = "commonApiResources"))
    configurationCommonServiceResources(project(path = ":common", configuration = "commonEarlyLaunchResources"))

    fun addEmbeddedFabricModule(dependency: String) {
        dependencies.implementation(dependency)
        dependencies.jarJar(dependency)
    }

    addEmbeddedFabricModule("org.sinytra.forgified-fabric-api:fabric-api-base:0.4.42+d1308ded19")
    implementation("net.caffeinemc:fabric-renderer-api-v1:6.0.0")
    jarJar("net.caffeinemc:fabric-renderer-api-v1:6.0.0")
    addEmbeddedFabricModule("org.sinytra.forgified-fabric-api:fabric-rendering-data-attachment-v1:0.3.48+73761d2e19")
    addEmbeddedFabricModule("org.sinytra.forgified-fabric-api:fabric-block-view-api-v2:1.0.10+9afaaf8c19")
    compileOnly("org.lwjgl:lwjgl-vulkan:3.3.3")
    additionalRuntimeClasspath("org.lwjgl:lwjgl-vulkan:3.3.3")
    compileOnly("org.lwjgl:lwjgl-spvc:3.3.3")
    additionalRuntimeClasspath("org.lwjgl:lwjgl-spvc:3.3.3")
    additionalRuntimeClasspath("org.lwjgl:lwjgl-spvc:3.3.3:natives-linux")
    additionalRuntimeClasspath("org.lwjgl:lwjgl-shaderc:3.3.3:natives-linux")
    compileOnly("org.lwjgl:lwjgl-shaderc:3.3.3")
    additionalRuntimeClasspath("org.lwjgl:lwjgl-shaderc:3.3.3")
    jarJar(project(":neoforge", "service"))
    additionalRuntimeClasspath("org.taumc:glsl-transformation-lib:0.2.0-20.ge3cb096")
}

val serviceJar = tasks.register<Jar>("serviceJar") {
    from(configurationCommonServiceJava)
    from(configurationCommonServiceResources)

    from(sourceSets["service"].output)

    from(rootDir.resolve("LICENSE.md"))

    manifest.attributes["FMLModType"] = "LIBRARY"

    archiveClassifier = "service"
}

val configurationService: Configuration = configurations.create("service") {
    isCanBeConsumed = true
    isCanBeResolved = true

    outgoing {
        artifact(serviceJar)
    }
}

sourceSets {
    named("service") {
        compileClasspath = sourceSets["main"].compileClasspath
        runtimeClasspath = sourceSets["main"].runtimeClasspath

        compileClasspath += configurationCommonServiceJava
        runtimeClasspath += configurationCommonServiceJava
    }

    main {
        compileClasspath += configurationCommonModJava
        runtimeClasspath += configurationCommonModJava
    }
}

neoForge {
    version = BuildConfig.NEOFORGE_VERSION

    if (BuildConfig.PARCHMENT_VERSION != null) {
        parchment {
            minecraftVersion = BuildConfig.MINECRAFT_VERSION
            mappingsVersion = BuildConfig.PARCHMENT_VERSION
        }
    }

    runs {
        create("Client") {
            client()
            ideName = "NeoForge/Client"
        }
        create("ClientRD") {
            client()
            ideName = "NeoForge/Client"
            environment("LD_PRELOAD", "/usr/lib/librenderdoc.so")
        }
    }

    mods {
        create("sodium") {
            sourceSet(sourceSets["main"])
            sourceSet(project(":common").sourceSets["main"])
            sourceSet(project(":common").sourceSets["api"])
        }

        create("sodium-service") {
            sourceSet(sourceSets["service"])
            sourceSet(project(":common").sourceSets["workarounds"])
        }
    }
}

tasks {
    jar {
        from(configurationCommonModJava)
        destinationDirectory.set(file(rootProject.layout.buildDirectory).resolve("mods"))
    }

    processResources {
        from(configurationCommonModResources)
    }
}

