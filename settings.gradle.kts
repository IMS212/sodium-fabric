rootProject.name = "sodium"

pluginManagement {
    repositories {
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.architectury.dev/") }
        maven { url = uri("https://files.minecraftforge.net/maven/") }
        maven { url = uri("https://maven.neoforged.net/releases/") }
        maven("https://repo.spongepowered.org/repository/maven-public/") { name = "Sponge Snapshots" }
        gradlePluginPortal()
    }
}

include("common")
include("fabric")
include("neoforge")