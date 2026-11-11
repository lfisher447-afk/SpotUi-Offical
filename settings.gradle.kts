pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "Spotui Music Player"
include(":app")
include(":spotify")
include(":innertube")

// Unused submodules commented out to optimize build performance and prevent assemble timeouts
// include(":betterlyrics")
// project(":betterlyrics").projectDir = file("modules/app/libs/betterlyrics")
// include(":kugou")
// project(":kugou").projectDir = file("modules/app/libs/kugou")
// include(":lastfm")
// project(":lastfm").projectDir = file("modules/app/libs/lastfm")
// include(":lrclib")
// project(":lrclib").projectDir = file("modules/app/libs/lrclib")
// include(":paxsenix")
// project(":paxsenix").projectDir = file("modules/app/libs/paxsenix")
// include(":shazamkit")
// project(":shazamkit").projectDir = file("modules/app/libs/shazamkit")
// include(":simpmusic")
// project(":simpmusic").projectDir = file("modules/app/libs/simpmusic")

 