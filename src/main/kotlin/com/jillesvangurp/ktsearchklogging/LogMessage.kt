package com.jillesvangurp.ktsearchklogging

import io.klogging.Level
import io.klogging.events.*
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


/**
 * Opinionated representation of logback log messages optimized for indexing in a sane way
 */
@Serializable
data class LogMessage(
    val id: String,
    /** When the event occurred, to microsecond or better precision. */
    @SerialName("@timestamp")
    val timestamp: Instant = timestampNow(),
    /** Host where the event occurred. */
    val host: String = hostname,
    /** Name of the logger that emitted the event. */
    val logger: String,
    /** Name of the thread or similar context identifier where the event was emitted. */
    val context: String?,
    /** Severity [Level] of the event. */
    val level: Level,
    /** [Message template](https://messagetemplates.org), if any, used to construct the message. */
    val template: String? = null,
    val templateEvaluated: String? = null,
    /** Message describing the event. */
    val message: String,
    /**
     *  String stack trace information that may be included if an error or exception is
     *  associated with the event.
     */
    val stackTrace: String? = null,
    /**
     * Map of items current at the time of the event, to be displayed as structured data.
     *
     * If the message string was constructed from a template, there is one item per
     * hole in the template.
     */
    val items: Map<String, String> = mapOf(),

//    val message: String,
//    val logger: String,
//    val thread: String,
//    val level: String,
//    @SerialName("@timestamp")
//    val timestamp: Instant = Clock.System.now(),
//    val mdc: Map<String, String>? = null,
//    val context: Map<String, String>? = null,
//    val contextName: String? = null,
//    /** exception and any causes stored in a structured way that can be easily mapped and aggregated on */
//    val exceptionList: List<LogException>? = null
)

@Serializable
data class LogException(val className: String, val message: String, val stackTrace: List<String>?)

//fun IThrowableProxy?.toLogException(): List<LogException>? {
//    return this?.let {
//        val l = mutableListOf(LogException(it.className,it.message,it.stackTraceElementProxyArray.map { st -> st.stackTraceElement.toString() }))
//        var c = it.cause
//        val seen = mutableSetOf<IThrowableProxy>()
//        while(c!= null && !seen.contains(c)) {
//            l.add(LogException(c.className,c.className,c.stackTraceElementProxyArray.map { st -> st.stackTraceElement.toString() }))
//            // prevent cycles
//            seen.add(c)
//            c = c.cause
//        }
//        l
//    }
//}

//fun ILoggingEvent.toLogMessage(variableFilter: Regex?): LogMessage {
//    this.marker
//    return LogMessage(
//        message = message,
//        logger = loggerName,
//        thread = threadName,
//        level = level.levelStr,
//        mdc = mdcPropertyMap.takeIf { (it?.size ?: 0) > 0 },
//        contextName = loggerContextVO?.name,
//        exceptionList = throwableProxy.toLogException(),
//        context = this.loggerContextVO?.propertyMap.takeIf { (it?.size ?: 0) > 0 }?.filter { (k,_)->
//            if(variableFilter!=null) {
//                k.matches(variableFilter)
//            } else {
//                true
//            }
//        }
//    )
//}