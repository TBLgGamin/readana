pluginManagement {
    includeBuild("build-logic")
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
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        // Readium's PDF adapter pulls pdfium bindings from JitPack.
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\..*") }
        }
    }
}

rootProject.name = "readana"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

include(":core:common")
include(":core:model")
include(":core:data")
include(":core:designsystem")
include(":core:testing")

include(":ai:client")

include(":engine:epub")
include(":engine:reflow")
include(":engine:pdf2epub")

include(":benchmark")

include(":source:api")
include(":source:gutenberg")
include(":source:annas")
include(":source:arxiv")
include(":source:openlibrary")
include(":source:opds")
include(":source:trending")

include(":feature:browser")
include(":feature:library")
include(":feature:discover")
include(":feature:downloads")
include(":feature:reader")
include(":feature:settings")
