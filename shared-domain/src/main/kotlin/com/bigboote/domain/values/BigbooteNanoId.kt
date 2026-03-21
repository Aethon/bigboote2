package com.bigboote.domain.values

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import java.util.Random

/**
 * Manages Nano ID generation and validation for the Bigboote service.
 */
object BigbooteNanoId {

    /**
     * The length of a Bigboote Nano ID.
     */
    const val LENGTH = 21

    /**
     * The maximum length of a Bigboote Nano ID prefix.
     */
    const val MAX_PREFIX_LENGTH = 5

    /** The alphabet used by Bigboote Nano IDs:
     *  * numbers
     *  * lowercase letters except `l`
     *  * uppercase letters except `I` and `O`
     */
    const val ALPHABET = "1234567890abcdefghijkmnopqrtuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ"

    private val alphabetChars = ALPHABET.toCharArray()
    private val pattern = Regex("^[${ALPHABET}]{$LENGTH}$")
    private const val ERROR_MESSAGE =
        "Invalid ID: must be $LENGTH numbers and letters (except lowercase 'l', uppercase 'I', and uppercase 'O')"

    private val prefixPattern = Regex("^[A-Z]{1,$MAX_PREFIX_LENGTH}")

    private val random = Random()

    /**
     * Generates a new Bigboote Nano ID.
     */
    fun generate(): String {
        return NanoIdUtils.randomNanoId(random, alphabetChars, LENGTH)
    }

    /**
     * Generates a new Bigboote Nano ID with a prefix.
     *
     * @param prefix the prefix to use.
     */
    fun generatePrefixed(prefix: String): String {
        requirePrefix(prefix)
        return "$prefix-${generate()}"
    }

    fun require(value: String) {
        require(pattern.matches(value)) { ERROR_MESSAGE }
    }

    fun requirePrefixed(prefix: String, value: String) {
        requirePrefix(prefix)
        require(value.startsWith(prefix) && value[prefix.length] == '-') { prefixedError(prefix) }
        require(
            pattern.matches(
                value.subSequence(
                    prefix.length + 1,
                    value.length
                )
            )
        ) { prefixedError(prefix) }
    }

    private fun requirePrefix(prefix: String) {
        require(prefix.matches(prefixPattern)) { "Invalid prefix: must be up to $MAX_PREFIX_LENGTH uppercase letters" }
    }

    private fun prefixedError(prefix: String) = "$ERROR_MESSAGE and must be prefixed with '$prefix-'."
}