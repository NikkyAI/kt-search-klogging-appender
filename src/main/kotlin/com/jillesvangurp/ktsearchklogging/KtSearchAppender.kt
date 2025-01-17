package com.jillesvangurp.ktsearchklogging

import com.jillesvangurp.ktsearch.DEFAULT_JSON
import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient
import io.klogging.Klogging
import io.klogging.events.LogEvent
import io.klogging.rendering.RenderString
import io.klogging.rendering.evalTemplate
import io.klogging.sending.SendString
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlin.time.Duration.Companion.days

class KtSearchAppender(
    val verbose: Boolean = false,
    val logElasticSearchCalls: Boolean = false,
    val host: String = "localhost",
    val port: Int = 9200,
    val userName: String? = null,
    val password: String? = null,
    val ssl: Boolean = false,

    val flushSeconds: Int = 1,
    val bulkMaxPageSizw: Int = 200,
    val createDataStream: Boolean = false,

    // Elasticsearch only feature, leave disabled for opensearch
    val configureIlm: Boolean = false,

    val dataStreamName: String = "applogs",
    val hotRollOverGb: Int = 2,
    val numberOfReplicas: Int = 1,
    val numberOfShards: Int = 1,
    val warmMinAgeDays: Int = 3,
    val deleteMinAgeDays: Int = 30,
    val warmShrinkShards: Int = 1,
    val warmSegments: Int = 1,
    val contextVariableFilter: Regex? = null,
    val contextVariableExclude: Regex? = null,
    val context: Map<String, String?> = emptyMap(),
) : Klogging {
    var logIndexer: LogIndexer
        private set

    val client = SearchClient(
        KtorRestClient(
            host = host,
            port = port,
            user = userName,
            password = password,
            https = ssl,
            logging = logElasticSearchCalls
        )
    )

    init {
        runBlocking {

            log("starting")
            log("connecting to $host:$port using ssl $ssl with user: $userName and password: ${password?.map { 'x' }}")
            if (createDataStream) {
                runBlocking {
                    val created = try {
                        client.manageDataStream(
                            prefix = dataStreamName,
                            hotRollOverGb = hotRollOverGb,
                            numberOfReplicas = numberOfReplicas,
                            numberOfShards = numberOfShards,
                            warmMinAge = warmMinAgeDays.days,
                            deleteMinAge = deleteMinAgeDays.days,
                            warmShrinkShards = warmShrinkShards,
                            warmSegments = warmSegments,
                            configureIlm = configureIlm
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                    log("data stream created: $created")
                }
            }

            logIndexer = LogIndexer(
                client = client,
                index = dataStreamName,
                bulkMaxPageSize = bulkMaxPageSizw,
                flushSeconds = flushSeconds,
                verbose = verbose
            )
            log("started log indexer")
            // so you can detect application restarts
            logger.info { "log appender init" }
            Runtime.getRuntime().addShutdownHook(Thread {
                // does not seem to ever get called otherwise
                logIndexer.stop()
            })
        }
    }

    private suspend fun log(message: String) {
        if (verbose) {
            println("kt-search_klogging-appender: $message")
        }
    }

    val sender: SendString = { s ->
        s.lines().filterNot { it.isBlank() }.forEach { line ->
            try {
                logIndexer.eventChannel.send(line)
            } catch (e: Exception) {
                e.printStackTrace()
                error("failed to send $line")
            }
        }
    }
    val renderer: RenderString = { logEvent ->
        try {
            val logMessage = logEvent.toLogMessage(contextVariableFilter, contextVariableExclude, context)
            try {
                DEFAULT_JSON.encodeToString(LogMessage.serializer(), logMessage)
            } catch (e: SerializationException) {
                println("problem serializing message: $logMessage")
                throw e
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("rendering error: ${e.message}")
            throw e
        }
    }
}
