package com.bigboote.coordinator.api.error

import kotlinx.serialization.Serializable

/**
 * Standard error response envelope returned by all API surfaces.
 * See API Design doc Section 6.
 */
@Serializable
data class ApiError(
    val error: ErrorDetail,
)

@Serializable
data class ErrorDetail(
    val code: String,
    val message: String,
    val detail: String? = null,
)
