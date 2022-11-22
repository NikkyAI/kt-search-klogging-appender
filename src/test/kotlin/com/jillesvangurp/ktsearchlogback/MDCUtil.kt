package com.jillesvangurp.ktsearchlogback

import io.klogging.context.logContext
import org.slf4j.MDC
import kotlin.coroutines.CoroutineContext

suspend fun logContextFromMdc(): CoroutineContext {
    val mdcContextMap = MDC.getCopyOfContextMap()
        .entries.map { it.key to it.value }
    return logContext(*mdcContextMap.toTypedArray())
}
