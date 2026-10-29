pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CronPaw"
include(":app")

// Icons: Yoin Symbols (io.github.p2o51:yoin-symbols), always built from source: the checkout
// next to this repo, or -PyoinSymbolsDir=<path>. Every app reads the same current symbols this
// way, so there is no Maven Central fallback; without the checkout the build stops here.
val yoinSymbolsDir = providers.gradleProperty("yoinSymbolsDir").getOrElse("../yoin-symbols")
val yoinSymbolsBuild = file("$yoinSymbolsDir/android")
check(yoinSymbolsBuild.isDirectory) {
    "Icons come from Yoin Symbols source: git clone https://github.com/p2o51/yoin-symbols " +
        "${yoinSymbolsBuild.parentFile.absolutePath}, or pass -PyoinSymbolsDir=<path>."
}
includeBuild(yoinSymbolsBuild)
