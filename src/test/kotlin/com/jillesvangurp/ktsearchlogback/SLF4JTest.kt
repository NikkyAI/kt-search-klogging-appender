package com.jillesvangurp.ktsearchlogback

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.term
import io.klogging.context.LogContext
import io.klogging.context.logContext
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.timing.eventually
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.longs.shouldBeExactly
import io.kotest.matchers.maps.shouldContain
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import mu.withLoggingContext
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.time.Duration.Companion.seconds

class SLF4JTest() : KLoggingTest() {
    private val log = KotlinLogging.logger {}

    @Test
    fun shouldLogSlf4jAndMDC(): Unit = runBlocking {
        val runId = Random.nextUInt().toString()
        withContext(logContext("test" to "mdc", "run" to runId)) {
            withLoggingContext(
                kotlin.coroutines.coroutineContext[LogContext]
                    ?.getAll()
                    .orEmpty()
                    .mapValues { it.value.toString() }
            ) {
                MDC.putCloseable("runId", "1").use {
                    log.info { "slf4j with MDC runId from LogContext" }
                }
            }

            MDC.putCloseable("runId", "1").use {
                withContext(logContextFromMdc()) {
                    logger.info { "klogging with LogContext runId from MDC" }
                }
            }
            withLoggingContext(
                mapOf("runId" to "1")
            ) {
                withContext(logContextFromMdc()) {
                    logger.info { "klogging with LogContext runId from MDC" }
                }
            }
//            }
//            MDC.setContextMap(mdc)
        }

        eventually(15.seconds) {
            // should not throw because the data stream was created
            client.getIndexMappings(appender.dataStreamName)
            // if our mapping is applied, we should be able to query on context.environment
            val resp = client.search(appender.dataStreamName) {
                resultSize = 100
                query = term("items.run", runId)
            }
            println(resp.total)
            resp.total shouldBeExactly 3
            val hits = resp.parseHits<LogMessage>(DEFAULT_JSON)
            assertSoftly {
                hits.forEach { m ->
                    m!!
                    m.items shouldContain ("environment" to "tests")
                    m.items shouldContain ("runId" to "1")
                    m.items.keys shouldNotContain "exclude"
                    m.items.keys shouldContain "host"
                }
            }
        }
    }
}