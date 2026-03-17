plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.ktor)
}

ktor {
    fatJar {
        archiveFileName.set("agent-service.jar")
    }
}

dependencies {
    implementation(project(":shared-domain"))
    implementation(project(":shared-events"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.neg)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.neg)
    implementation(libs.koin.ktor)
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.logback.classic)

    // Anthropic client (xemantic) — add to libs.versions.toml when version confirmed
    // implementation(libs.xemantic.anthropic)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.core)
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}

application {
    mainClass.set("com.bigboote.agent.ApplicationKt")
}

