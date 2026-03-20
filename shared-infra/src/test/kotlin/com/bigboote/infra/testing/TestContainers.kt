package com.bigboote.infra.testing

import com.bigboote.infra.config.DatabaseConfig
import com.bigboote.infra.config.S3Config
import com.eventstore.dbclient.EventStoreDBClient
import com.eventstore.dbclient.EventStoreDBConnectionString
import com.eventstore.dbclient.EventStoreDBPersistentSubscriptionsClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

/**
 * Shared, lazily-started TestContainers singletons for integration tests.
 *
 * Each container starts at most once per JVM (test run) and is stopped by
 * Ryuk when the JVM exits.  Sharing containers across specs dramatically
 * reduces integration-test wall-clock time.
 *
 * Usage in a KoTest spec:
 * ```kotlin
 * class MyIntegrationTest : StringSpec({
 *     val dbConfig = SharedPostgres.databaseConfig()
 *     val esClient = SharedKurrentDb.client()
 *     val s3Client = SharedLocalStack.s3Client()
 *     // …
 * })
 * ```
 */

// ---------------------------------------------------------------------------
// PostgreSQL
// ---------------------------------------------------------------------------
object SharedPostgres {

    private val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("bigboote")
            .withUsername("bigboote")
            .withPassword("bigboote")
            .also { it.start() }
    }

    /** A [DatabaseConfig] pointing at the running container. */
    fun databaseConfig(): DatabaseConfig = DatabaseConfig(
        jdbcUrl = container.jdbcUrl,
        username = container.username,
        password = container.password,
        maxPoolSize = 2,
    )
}

// ---------------------------------------------------------------------------
// KurrentDB (EventStoreDB)
// ---------------------------------------------------------------------------
object SharedKurrentDb {

    private val container: GenericContainer<*> by lazy {
        GenericContainer(DockerImageName.parse("eventstore/eventstore:24.10"))
            .withExposedPorts(2113)
            .withEnv("EVENTSTORE_INSECURE", "true")
            .withEnv("EVENTSTORE_MEM_DB", "true")
            .withEnv("EVENTSTORE_CLUSTER_SIZE", "1")
            .withEnv("EVENTSTORE_RUN_PROJECTIONS", "None")
            .also { it.start() }
    }

    /** ESDB connection string for the running container (e.g. `esdb://localhost:32771?tls=false`). */
    fun connectionString(): String =
        "esdb://${container.host}:${container.getMappedPort(2113)}?tls=false"

    /** Create a fresh [EventStoreDBClient]. Caller is responsible for shutdown. */
    fun client(): EventStoreDBClient {
        val settings = EventStoreDBConnectionString.parseOrThrow(connectionString())
        return EventStoreDBClient.create(settings)
    }

    /** Create a fresh persistent-subscriptions client. Caller is responsible for shutdown. */
    fun persistentSubscriptionsClient(): EventStoreDBPersistentSubscriptionsClient {
        val settings = EventStoreDBConnectionString.parseOrThrow(connectionString())
        return EventStoreDBPersistentSubscriptionsClient.create(settings)
    }
}

// ---------------------------------------------------------------------------
// LocalStack (S3)
// ---------------------------------------------------------------------------
object SharedLocalStack {

    private const val BUCKET_NAME = "bigboote-documents"

    private val container: LocalStackContainer by lazy {
        LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
            .withServices(Service.S3)
            .also { it.start() }
    }

    /** An [S3Config] pointing at the running LocalStack container. */
    fun s3Config(): S3Config = S3Config(
        endpoint = container.getEndpointOverride(Service.S3).toString(),
        region = container.region,
        bucket = BUCKET_NAME,
        accessKeyId = container.accessKey,
        secretAccessKey = container.secretKey,
    )

    /**
     * Create an [S3Client] configured for the running LocalStack container.
     * Also ensures the test bucket exists.
     * Caller is responsible for closing the client.
     */
    fun s3Client(): S3Client {
        val client = S3Client.builder()
            .endpointOverride(container.getEndpointOverride(Service.S3))
            .region(Region.of(container.region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(container.accessKey, container.secretKey)
                )
            )
            .forcePathStyle(true)
            .build()

        // Ensure test bucket exists (idempotent)
        runCatching {
            client.createBucket { it.bucket(BUCKET_NAME) }
        }

        return client
    }
}
