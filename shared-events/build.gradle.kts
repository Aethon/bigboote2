plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":shared-domain"))

    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kurrentdb.client)

    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.mockk)
}
