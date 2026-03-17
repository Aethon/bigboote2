dependencies {
    implementation(project(":shared-domain"))

    implementation(libs.kotlinx.serialization)
    implementation(libs.kurrentdb.client)

    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.mockk)
}
