dependencies {
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.jnanoid)
    implementation(libs.arrow.core)

    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
}
