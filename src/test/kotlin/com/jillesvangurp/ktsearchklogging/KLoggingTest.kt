package com.jillesvangurp.ktsearchklogging

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient
import io.klogging.Klogging
import io.klogging.Level
import io.klogging.config.DEFAULT_CONSOLE
import io.klogging.config.loggingConfiguration
import org.junit.jupiter.api.BeforeAll

open class KLoggingTest : Klogging {
    companion object {
        val appender = KtSearchAppender(
            verbose = true,
            logElasticSearchCalls = false,
            host = "localhost",
            port = 9292,
            createDataStream = true,
            configureIlm = true,
            hotRollOverGb = 1,
            warmMinAgeDays = 1,
            deleteMinAgeDays = 7,
            contextVariableExclude = "(exclude)".toRegex(),
            context = mapOf(
                "environment" to "tests",
                "host" to (System.getenv("HOSTNAME") ?: "")
            )
        )

        @BeforeAll
        @JvmStatic
        fun setupLogger() {
            println("setting up logging")
            loggingConfiguration {
                DEFAULT_CONSOLE()
                sink("ktsearch", appender.renderer, appender.sender)
                logging { fromMinLevel(Level.INFO) { toSink("ktsearch") } }
            }
            logger.info { "logging setup" }
        }

        val client by lazy {
            SearchClient(
                KtorRestClient(
                    appender.host,
                    appender.port
                )
            )
        }
    }
}

