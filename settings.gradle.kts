rootProject.name = "bigboote"

// Gradle 9.x auto-detects gradle/libs.versions.toml as the "libs" catalog by convention.

include(":shared-domain")
include(":shared-events")
include(":shared-infra")
include(":coordinator")
include(":agent-service")
