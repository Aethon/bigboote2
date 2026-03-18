/**
 * Gradle build script for the vue-ui subproject.
 *
 * Uses the node-gradle plugin (com.github.node-gradle.node) to integrate the
 * Vite/Node.js frontend into the Gradle build lifecycle.
 *
 * Tasks:
 *   npmInstall   — provided by the plugin; runs `npm install` with up-to-date checks
 *   npmBuild     — runs `npm run build` (type-check + Vite bundle); depends on npmInstall
 *   clean        — (base plugin) deletes dist/
 *
 * The Gradle `build` lifecycle task depends on `npmBuild` so that a full
 * `./gradlew build` at the root always produces a fresh UI bundle.
 *
 * CI notes:
 *   - By default the plugin uses the system Node.js / npm (download = false).
 *     Set `node.download = true` and `node.version` to pin and auto-download a
 *     specific Node version without requiring it on the PATH.
 *   - Set NODE_ENV=production for optimised Vite output (tree-shaking, minify).
 *   - To skip the UI build: `./gradlew build -x :vue-ui:npmBuild`
 *
 * See Phase 16 scaffold spec.
 */

import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    alias(libs.plugins.node.gradle)
}

// ---- Node / npm configuration -----------------------------------------

node {
    // Use this version of Node.js for the build.
    download = true
    version = "24.12.0"

    // Ensure the plugin resolves npm commands relative to this subproject.
    nodeProjectDir = projectDir
}

// ---- npmBuild ---------------------------------------------------------

val npmBuild by tasks.registering(NpmTask::class) {
    description = "Type-check and bundle the vue-ui project with Vite."
    group       = "build"

    // The plugin's built-in `npmInstall` task handles `npm install`.
    dependsOn(tasks.npmInstall)

    args = listOf("run", "build")

    environment = mapOf(
        "NODE_ENV" to (System.getenv("NODE_ENV") ?: "production"),
    )

    // Incremental-build inputs / outputs so Gradle skips this task when
    // nothing relevant has changed.
    inputs.dir(projectDir.resolve("src"))
    inputs.files(
        file("package.json"),
        file("vite.config.ts"),
        file("tsconfig.json"),
    )
    outputs.dir(projectDir.resolve("dist"))
}

// ---- Wire into Gradle lifecycle ---------------------------------------

tasks.named("build") {
    dependsOn(npmBuild)
}

// ---- clean ------------------------------------------------------------

tasks.named<Delete>("clean") {
    delete(projectDir.resolve("dist"))
    // node_modules is intentionally NOT deleted on `clean` to avoid slow
    // re-installs; run `./gradlew :vue-ui:npmInstall --rerun` if needed.
}
