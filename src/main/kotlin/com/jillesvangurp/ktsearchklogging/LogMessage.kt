package com.jillesvangurp.ktsearchklogging

import io.klogging.Level
import io.klogging.events.*
import io.klogging.rendering.evalTemplate
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Opinionated representation of logback/klogging log messages optimized for indexing in a sane way
 */
@Serializable
data class LogMessage(
//    val id: String,
    /** [Message template](https://messagetemplates.org), if any, used to construct the message. */
    val template: String? = null,
    /** Message describing the event. */
    val message: String,
    /** Name of the logger that emitted the event. */
    val logger: String,
    /** Name of the thread or similar context identifier where the event was emitted. */
    val thread: String? = null,
    /** Severity [Level] of the event. */
    val level: Level,
    /** When the event occurred, to microsecond or better precision. */
    @SerialName("@timestamp")
    val timestamp: Instant = timestampNow(),
    /**
     * Map of items current at the time of the event, to be displayed as structured data.
     *
     * If the message string was constructed from a template, there is one item per
     * hole in the template.
     */
    val mdc: Map<String, String> = mapOf(),
    val context: Map<String, String> = mapOf(),
    val contextName: String? = null,
    /**
     *  String stack trace information that may be included if an error or exception is
     *  associated with the event.
     */
    val exceptionList: List<LogException>? = null,
)

@Serializable
data class LogException(val className: String, val message: String, val stackTrace: List<String>?)

fun LogEvent.toLogMessage(
    variableFilter: Regex?,
    variableExclude: Regex?,
    contextMap: Map<String, String?>,
): LogMessage {
    val items = items
        .filter { (k, _) ->
            if (variableFilter != null) {
                k.matches(variableFilter)
            } else {
                true
            }
        }
        .filter { (k, _) ->
            if (variableExclude != null) {
                !k.matches(variableExclude)
            } else {
                true
            }
        }
        .mapValues { it.value.toString() }

    val exceptions = stackTrace?.let { stacktrace ->
        stacktrace.split("Caused by: ").map { stacktrace ->
            val lines = stacktrace.lines()
            LogException(
                className = lines.first().substringBefore(":"),
                message = lines.first().substringAfter(":"),
                stackTrace = lines
            )
        }
    }

    return LogMessage(
        timestamp = timestamp,
        logger = logger.substringBefore("$$"),
        thread = context,
        level = level,
        message = evalTemplate(),
        exceptionList = exceptions,
        mdc = items,
        context = contextMap.mapValues { (k, v) -> v.toString() },
    )
}
