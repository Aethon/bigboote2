plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.ktor)
    application
}

application {
    mainClass.set("com.bigboote.coordinator.ApplicationKt")
}

ktor {
    fatJar {
        archiveFileName.set("coordinator.jar")
    }
}

dependencies {
    implementation(project(":shared-domain"))
    implementation(project(":shared-events"))
    implementation(project(":shared-infra"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.sse)
    implementation(libs.ktor.server.content.neg)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.neg)
    implementation(libs.koin.ktor)
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logback.classic)

    // Exposed (needed for DatabaseFactory return type and future Projections in Phase 6)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.postgresql)
    implementation(libs.hikari)

    // KurrentDB (needed for EventStore subscriptions used by Reactors/Projections)
    implementation(libs.kurrentdb.client)

    // AWS SDK v2 — Phase 14: Document S3 storage
    implementation(libs.aws.sdk.java.s3)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.koin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.localstack)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks {
    named("distZip") { dependsOn("shadowJar") }
    named("distTar") { dependsOn("shadowJar") }
    named("startScripts") { dependsOn("shadowJar") }
    named("startShadowScripts") { dependsOn("jar") }
}
