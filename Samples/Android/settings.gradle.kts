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
        // 本地 LiteAVSDK VoiceAI aar（仓库根目录 libs/，app 与 VoiceAIKit 共用）
        flatDir {
            dirs(File(settingsDir, "libs").absolutePath)
        }
    }
}

rootProject.name = "VoiceAIDemo"
include(":app")
include(":VoiceAIKit")
