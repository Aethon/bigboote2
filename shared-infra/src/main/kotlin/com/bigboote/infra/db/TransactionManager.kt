package com.bigboote.infra.db

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

/**
 * Executes an Exposed transaction block within a coroutine context.
 * Uses Dispatchers.IO to avoid blocking the caller's coroutine dispatcher.
 *
 * Usage:
 * ```
 * val result = dbQuery {
 *     MyTable.selectAll().toList()
 * }
 * ```
 */
suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }
