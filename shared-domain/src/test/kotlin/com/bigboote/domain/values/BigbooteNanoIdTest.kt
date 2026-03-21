package com.bigboote.domain.values

import com.bigboote.domain.testing.StreamNames
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.serialization.json.Json

class BigbooteNanoIdTest : DescribeSpec({

    describe("generate fun") {
        it("generates a valid ID") {
            var result = BigbooteNanoId.generate()

            result.length shouldBe BigbooteNanoId.LENGTH
            result.any { !EXPECTED_ALPHABET.contains(it) } shouldBe false
        }
    }

    describe("require") {
        it("allows a valid ID") {
            BigbooteNanoId.require("AAAAAAAAAAAAAAAAAAAAA")
        }

        badIds.forEach { id ->
            it("throws when a bad ID is provided: \"$id\"") {
                shouldThrow<IllegalArgumentException> { BigbooteNanoId.require(id) }
                    .message shouldBe "Invalid ID: must be $EXPECTED_LENGTH numbers and letters (except lowercase 'l', uppercase 'I', and uppercase 'O')"
            }
        }
    }

    describe("generatePrefixed") {
        it("generates a valid prefixed ID with one character") {
            var result = BigbooteNanoId.generatePrefixed("A")

            result.length shouldBe BigbooteNanoId.LENGTH + 2
            result shouldStartWith "A-"
            result.substring(2).any { !EXPECTED_ALPHABET.contains(it) } shouldBe false
        }


        it("generates a valid prefixed ID with max characters") {
            var prefix = "A".repeat(EXPECTED_MAX_PREFIX_LENGTH)
            var result = BigbooteNanoId.generatePrefixed(prefix)

            result.length shouldBe BigbooteNanoId.LENGTH + prefix.length + 1
            result shouldStartWith "$prefix-"
            result.substring(prefix.length + 1).any { !EXPECTED_ALPHABET.contains(it) } shouldBe false
        }

        badPrefixes.forEach { prefix ->
            it("throws when a bad prefix is provided: \"$prefix\"") {
                shouldThrow<IllegalArgumentException> { BigbooteNanoId.generatePrefixed(prefix) }
                    .message shouldBe "Invalid prefix: must be up to $EXPECTED_MAX_PREFIX_LENGTH uppercase letters"
            }
        }
    }

    describe("requirePrefixed") {
        it("allows a valid ID") {
            BigbooteNanoId.requirePrefixed("AA", "AA-AAAAAAAAAAAAAAAAAAAAA")
        }

        badPrefixes.forEach { prefix ->
            it("throws when a bad prefix is provided: \"$prefix\"") {
                shouldThrow<IllegalArgumentException> { BigbooteNanoId.requirePrefixed(prefix, "$prefix-AAAAAAAAAAAAAAAAAAAAA") }
                    .message shouldBe "Invalid prefix: must be up to $EXPECTED_MAX_PREFIX_LENGTH uppercase letters"
            }
        }

        badPrefixes.forEach { prefix ->
            val case = "$prefix-AAAAAAAAAAAAAAAAAAAAA"
            it("throws when a bad ID is provided: \"$case\"") {
                shouldThrow<IllegalArgumentException> { BigbooteNanoId.requirePrefixed("AA", case) }
                    .message shouldBe "Invalid ID: must be $EXPECTED_LENGTH numbers and letters (except lowercase 'l', uppercase 'I', and uppercase 'O') and must be prefixed with 'AA-'."
            }
        }

        badIds.forEach { id ->
            val case = "AA-$id"
            it("throws when a bad ID is provided: \"$case\"") {
                shouldThrow<IllegalArgumentException> { BigbooteNanoId.requirePrefixed("AA",case) }
                    .message shouldBe "Invalid ID: must be $EXPECTED_LENGTH numbers and letters (except lowercase 'l', uppercase 'I', and uppercase 'O') and must be prefixed with 'AA-'."
            }
        }

    }

}) {
    companion object {

        // These are replicated here to create inertia
        const  val EXPECTED_LENGTH = 21
        const val EXPECTED_MAX_PREFIX_LENGTH = 5
        const val EXPECTED_ALPHABET = "1234567890abcdefghijkmnopqrtuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ"

        private val badPrefixes = listOf(
            "",
            "a",
            "Ab",
            "-",
            "_",
            "0",
            "AAAAAA"
        )

        private val badIds = listOf(
            "",
            "A",
            "_".repeat(EXPECTED_LENGTH),
            "-".repeat(EXPECTED_LENGTH),
            "l".repeat(EXPECTED_LENGTH),
            "O".repeat(EXPECTED_LENGTH),
            "I".repeat(EXPECTED_LENGTH),
            "!".repeat(EXPECTED_LENGTH),
        )
    }
}