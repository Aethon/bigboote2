plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-test-fixtures`
}

dependencies {
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.jnanoid)
    implementation(libs.arrow.core)

    implementation(libs.xemanticAiToolSchema)
    implementation(libs.xemanticAiMoney)
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)

    testFixturesImplementation(libs.kotlinx.serialization)
    testFixturesImplementation(libs.kotlinx.datetime)
}
