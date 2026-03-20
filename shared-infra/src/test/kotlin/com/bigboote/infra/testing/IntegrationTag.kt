package com.bigboote.infra.testing

import io.kotest.core.Tag

/**
 * KoTest tag for integration tests that require Docker containers.
 *
 * Run only unit tests:
 *   ./gradlew test -Dkotest.tags="!Integration"
 *
 * Run only integration tests:
 *   ./gradlew test -Dkotest.tags="Integration"
 *
 * Run everything (default):
 *   ./gradlew test
 */
object Integration : Tag()
