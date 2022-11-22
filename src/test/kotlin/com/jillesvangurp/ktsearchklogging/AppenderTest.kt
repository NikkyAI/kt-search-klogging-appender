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
import java.lang.Exception
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
                MDC.put("test", "value")
                logger.error { "another one" }
                try {
                    error("oopsie")
                } catch (e: Exception) {
                    logger.error(e) { "stacktrace" }
                }
                withContext(logContext("exclude" to "THIS SHOULD NOT BE SHOWN")
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
                    query = term("items.run", runId)
                }
                resp.total shouldBeGreaterThan 0

                val hits = resp.parseHits<LogMessage>(DEFAULT_JSON).filterNotNull()
                println(resp.total)
                println(hits.map {it.message})

                hits shouldHaveSize 5
                hits.first().let { m ->
                    println(m)
                    withClue("$m") {
                        m.items shouldContain ("environment" to "tests")
                        m.items.keys shouldNotContain "exclude"
                        m.items.keys shouldContain "host"
                    }
                }
                hits.first(){ it.message == "stacktrace" }.let {
                    println("stacktrace: ${it.stackTrace}")
                    it.stackTrace shouldNotBe null
                }
                hits.mapNotNull { it.items["exclude"] } shouldHaveSize 0
            }

        }
    }
}