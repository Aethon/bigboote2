package com.bigboote.infra.config

/**
 * Configuration for the Fly.io Machines API used by [FlyMachineSpawnStrategy].
 *
 * All fields are loaded from environment variables.  Defaults are provided only
 * for non-sensitive values that are safe to expose in development configurations.
 *
 * Required in production:
 *   - BIGBOOTE_FLY_API_TOKEN  — Fly.io personal or deploy token
 *   - BIGBOOTE_FLY_APP_NAME   — name of the Fly.io app to launch machines in
 *
 * Optional (have sensible defaults):
 *   - BIGBOOTE_FLY_REGION     — primary region for machine placement (default "iad")
 *   - BIGBOOTE_FLY_ORG        — Fly.io organisation slug (default "personal")
 *
 * See Architecture doc Section 20.1.
 */
data class FlyConfig(
    /** Fly.io API token — passed as `Authorization: Bearer <token>`. */
    val apiToken: String,
    /** Name of the Fly.io app that agent machines are launched inside. */
    val appName: String,
    /** Fly.io region code for machine placement, e.g. "iad", "lhr", "nrt". */
    val region: String,
    /** Fly.io organisation slug. Used when creating machines. */
    val org: String,
) {
    companion object {
        fun fromEnvironment(): FlyConfig = FlyConfig(
            apiToken = System.getenv("BIGBOOTE_FLY_API_TOKEN") ?: "",
            appName  = System.getenv("BIGBOOTE_FLY_APP_NAME")  ?: "bigboote",
            region   = System.getenv("BIGBOOTE_FLY_REGION")    ?: "iad",
            org      = System.getenv("BIGBOOTE_FLY_ORG")       ?: "personal",
        )
    }
}
