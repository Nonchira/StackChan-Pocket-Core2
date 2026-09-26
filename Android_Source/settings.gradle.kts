pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral(); maven(url = "https://jitpack.io") } }
rootProject.name = "StackChanPocket"
include(":app")
