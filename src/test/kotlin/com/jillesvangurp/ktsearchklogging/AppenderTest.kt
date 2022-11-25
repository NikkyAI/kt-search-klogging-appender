package com.jillesvangurp.ktsearchklogging

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.term
import io.klogging.context.logContext
import io.kotest.assertions.timing.eventually
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import java.lang.IllegalArgumentException
import kotlin.Exception
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.time.Duration.Companion.seconds

val logger = KotlinLogging.logger {}

class AppenderTest: KLoggingTest() {
    @Test
    fun shouldLogSomeStuff() {
        runBlocking {
            val runId = Random.nextUInt().toString()
            withContext(logContext("test" to "appender", "run" to runId)) {
                logger.info { "hello world" }
                //TODO: make log function aliases to capture mdc ?
                MDC.put("test", "value")
                logger.error { "another one" }
                try {
                    try {
                        while (true) {
                            error("oopsie")
                        }
                    } catch (e: Exception) {
                        throw IllegalArgumentException("some exception", e)
                    }
                } catch (e: Exception) {
                    logger.error(e) { "exception" }
                }
                withContext(
                    logContext("exclude" to "THIS SHOULD NOT BE SHOWN")
                ) {
                    logger.error { "meow" }
                }
                logger.warn { "last one" }
            }
            delay(1.seconds)

            eventually(15.seconds) {
                // should not throw because the data stream was created
                client.getIndexMappings(appender.dataStreamName)
                // if our mapping is applied, we should be able to query on context.environment
                val resp = client.search(appender.dataStreamName) {
                    resultSize = 100
                    query = term("mdc.run", runId)
                }
                resp.total shouldBeGreaterThan 0

                val hits = resp.parseHits<LogMessage>(DEFAULT_JSON).filterNotNull()
                println(resp.total)
                println(hits.map {it.message})

                hits shouldHaveSize 5
                hits.first().let { m ->
                    println(m)
                    withClue("$m") {
                        m.context shouldContain ("environment" to "tests")
                        m.mdc.keys shouldNotContain "exclude"
                        m.context.keys shouldContain "host"
                    }
                }
                hits.first { it.message == "exception" }.let { m ->
                    println("exception: ${m.exceptionList}")
                    m.exceptionList shouldNotBe null
                    m.exceptionList?.let { exceptionList ->
                        exceptionList shouldHaveSize 2
                    }
                }
                hits.mapNotNull { it.mdc["exclude"] } shouldHaveSize 0
            }

        }
    }
}