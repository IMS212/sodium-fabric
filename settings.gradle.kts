rootProject.name = "sodium"

pluginManagement {
    repositories {
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.architectury.dev/") }
        maven { url = uri("https://files.minecraftforge.net/maven/") }
        maven { url = uri("https://repo.spongepowered.org/repository/maven-public/") }
        gradlePluginPortal()
    }
}

include("common")
include("fabric")