import org.gradle.plugins.ide.idea.model.IdeaModule

plugins {
    id("idea")
    id("maven-publish")
    id("net.neoforged.gradle.userdev") version "7.0.81"
    id("java-library")
}

val MINECRAFT_VERSION: String by rootProject.extra
val NEOFORGE_VERSION: String by rootProject.extra

base {
    archivesName = "sodium-neoforge-${MINECRAFT_VERSION}"
}

sourceSets {
    val service = create("service")
    val main = getByName("main")

    service.apply {
        java {
            srcDir("src/service/java")
        }

        compileClasspath += main.compileClasspath
    }

    main.apply {
        //runtimeClasspath -= output
    }
}

// Automatically enable neoforge AccessTransformers if the file exists
// This location is hardcoded in FML and can not be changed.
// https://github.com/neoforged/FancyModLoader/blob/a952595eaaddd571fbc53f43847680b00894e0c1/loader/src/main/java/net/neoforged/fml/loading/moddiscovery/ModFile.java#L118
if (file("src/main/resources/META-INF/accesstransformer.cfg").exists()) {
    minecraft.accessTransformers.file("src/main/resources/META-INF/accesstransformer.cfg")
}

runs {
    configureEach {
        modSource(project.sourceSets.main.get())
    }

    create("client") {
        workingDirectory(project.file("run"))
        dependencies {
            runtime("com.lodborg:interval-tree:1.0.0")

        }
        //displayName = "Client"
        //setProperty("mixin.env.remapRefMap", "true")
        //setProperty("mixin.env.refMapRemappingFile", "${projectDir}/build/createSrgToMcp/output.srg")
        //mods {
        //    create("modRun") {
        //        source(sourceSets.main.get())
        //        source(project(":common").sourceSets.main.get())
        //    }
        //}
    }
}

sourceSets.main.get().resources { srcDir("src/generated/resources") }

dependencies {
    implementation("net.neoforged:neoforge:${NEOFORGE_VERSION}")
    compileOnly(project(":common"))
    implementation(files("fabric_renderer_api_v1-1.0.0.jar"))
    implementation(group = "com.lodborg", name = "interval-tree", version = "1.0.0")
}

// NeoGradle implementations the game, but we don"t want to add our common code to the game"s code
val notNeoTask: (Task) -> Boolean = { it : Task -> !it.name.startsWith("neo") }

tasks.withType<JavaCompile>().matching(notNeoTask).configureEach {
    source(project(":common").sourceSets.main.get().allSource)
    source(project(":common").sourceSets.getByName("api").allSource)
}

tasks.withType<Javadoc>().matching(notNeoTask).configureEach {
    source(project(":common").sourceSets.main.get().allJava)
    source(project(":common").sourceSets.getByName("api").allJava)
}

tasks.withType<ProcessResources>().matching(notNeoTask).configureEach {
    from(project(":common").sourceSets.main.get().resources)
    from(project(":common").sourceSets.getByName("api").resources)
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)
publishing {
    publications {
       // mavenJava(MavenPublication) {
       //     artifactId base.archivesName.get()
       //     from components.java
       // }
    }
    repositories {
        maven(
                "file://"+System.getenv("local_maven"))
    }
}