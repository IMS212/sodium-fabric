plugins {
    id("net.neoforged.gradle.userdev") version "7.0.97"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    maven {
        url = uri("https://maven.neoforged.net/releases")
    }
    maven {
        url = uri("https://maven.fabricmc.net/")
    }

    mavenLocal()
}

java.toolchain.languageVersion = JavaLanguageVersion.of(17)

sourceSets {
    val service = create("service")
    val shade = create("shade")
    val main = getByName("main")

    service.apply {
        java {
            srcDir("src/service/java")
        }

        compileClasspath += main.compileClasspath
    }

    shade.apply {
        compileClasspath += main.compileClasspath
    }

    main.apply {
        runtimeClasspath -= output
        runtimeClasspath += shade.output
    }
}

val MINECRAFT_VERSION: String by rootProject.extra
val NEOFORGE_VERSION: String by rootProject.extra
base.archivesName.set("sodium-forge")


tasks.shadowJar {
    exclude("fabric.mod.json")
    archiveClassifier.set("dev-shadow")
}

var fullJar = tasks.register<Jar>("fullJar")

fullJar.configure {
    dependsOn(tasks.jar)
    from(sourceSets.getByName("service").output)
    from(project(":common").sourceSets.getByName("desktop").output)
    manifest.from(tasks.jar.get().manifest)
    into("META-INF") {
        from(sourceSets.getByName("main").output.resourcesDir!!.toPath().resolve("META-INF").resolve("mods.toml").toFile())
    }
    from(project(":common").sourceSets.getByName("main").output.resourcesDir!!.toPath().resolve("sodium-icon.png").toFile())
    into("META-INF/jarjar") {
        from(tasks.jar.get().archiveFile.get())
    }

    archiveClassifier = ""

    manifest.attributes["FMLModType"] = "LIBRARY"

    from("${rootProject.projectDir}/COPYING")
    from("${rootProject.projectDir}/COPYING.LESSER")

    manifest.attributes["Main-Class"] = "net.caffeinemc.mods.sodium.desktop.LaunchWarn"
}

var runClientJar = tasks.register<Jar>("runClientJar")

runClientJar.configure {
    dependsOn(tasks.shadowJar)
    from(sourceSets.getByName("service").output)
    manifest.from(tasks.shadowJar.get().manifest)
    into("META-INF") {
        from(sourceSets.getByName("main").output.resourcesDir!!.toPath().resolve("META-INF").resolve("mods.toml").toFile())
    }

    into("META-INF/jarjar") {
        from(tasks.shadowJar.get().archiveFile.get())
    }
    destinationDirectory.set(projectDir.resolve("build").resolve("devlibs"))
    archiveClassifier = "devJar"

    manifest.attributes["FMLModType"] = "LIBRARY"
}

tasks.assemble.configure {
    dependsOn(fullJar)
    dependsOn(runClientJar)
}


//tasks.runClient {
//    classpath += files(runClientJar)
//}

tasks.jar {
    archiveClassifier.set("dev")
    from(sourceSets.getByName("shade").output)

    from("${rootProject.projectDir}/COPYING")
    from("${rootProject.projectDir}/COPYING.LESSER")
}

components.getByName("java") {
    this as AdhocComponentWithVariants
    this.withVariantsFromConfiguration(project.configurations["shadowRuntimeElements"]) {
        skip()
    }
}

/*
// NeoGradle compiles the game, but we don't want to add our common code to the game's code
val notNeoTask = { it : Task -> !it.name.startsWith("neo") } as Spec<Task>

tasks.withType<JavaCompile>().matching(notNeoTask).configureEach {
    source(project(":common").sourceSets.main.allSource)
}

tasks.withType<Javadoc>().matching(notNeoTask).configureEach {
    source(project(":common").sourceSets.main.allJava)
}

tasks.withType<ProcessResources>().matching(notNeoTask).configureEach {
    from(project(":common").sourceSets.main.resources)
}*/


dependencies {
    implementation("net.neoforged:neoforge:${NEOFORGE_VERSION}")

    implementation(group = "com.lodborg", name = "interval-tree", version = "1.0.0")
    //forgeRuntimeLibrary(group = "com.lodborg", name = "interval-tree", version = "1.0.0")

    //modCompileOnly("net.fabricmc.fabric-api:fabric-renderer-api-v1:3.2.9+1172e897d7")
    compileOnly(project(":common", "namedElements")) {
        isTransitive = false
    }
}

tasks.processResources {
    filesMatching("META-INF/mods.toml") {
        expand(mapOf("version" to project.version))
    }
}