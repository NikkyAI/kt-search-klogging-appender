package com.jillesvangurp.ktsearchlogback

import com.jillesvangurp.jsondsl.withJsonDsl
import com.jillesvangurp.ktsearch.*
import kotlinx.datetime.Clock
import kotlin.time.Duration

suspend fun SearchClient.dataStreamExists(name: String): Boolean {
    val response = restClient.get {
        path(name)
    }
    val e = response.exceptionOrNull()
    return if(e!=null) {
        if(e is RestException) {
            if(e.status == 404) {
                false
            } else {
                throw e
            }
        } else {
            throw e
        }
    } else {
        println(response.getOrNull()?.text)
        true
    }
}

suspend fun SearchClient.manageDataStream(
    prefix: String,
    hotRollOverGb:Int,
    numberOfReplicas: Int,
    numberOfShards: Int,
    warmMinAge: Duration,
    deleteMinAge: Duration,
    warmShrinkShards: Int,
    warmSegments: Int,
    configureIlm: Boolean,
): Boolean {
    if(dataStreamExists(prefix)) {
        // don't recreate
        println("not recreating")
        return false
    }
    if(configureIlm) {
        setIlmPolicy("$prefix-ilm-policy") {
            hot {
                actions {
                    rollOver(hotRollOverGb)
                }
            }
            warm {
                minAge(warmMinAge)
                actions {
                    shrink(warmShrinkShards)
                    forceMerge(warmSegments)
                }
            }
            delete {
                minAge(deleteMinAge)
                actions {
                    delete()
                }
            }
        }
    }
    // using component templates is a good idea
    updateComponentTemplate("$prefix-template-settings") {
        settings {
            replicas = numberOfReplicas
            shards = numberOfShards
            put("index.lifecycle.name", "$prefix-ilm-policy")
        }
    }
    updateComponentTemplate("$prefix-template-mappings") {
        dynamicTemplate("keywords") {
            match = "*"
            // this works on the mdc and context fields where set turn dynamic to true
            mapping("keyword") {
                ignoreAbove="256"
            }
        }
        mappings(false) {
            text("text")
            keyword(LogMessage::id)
            date("@timestamp")
            keyword(LogMessage::host)
            keyword(LogMessage::logger) {
                copyTo = listOf("text")
            }
            keyword(LogMessage::context) {
                copyTo = listOf("text")
            }
            keyword(LogMessage::level)
            text(LogMessage::template) {
                copyTo = listOf("text")
            }
            text(LogMessage::templateEvaluated) {
                copyTo = listOf("text")
            }
            text(LogMessage::message) {
                fields {
                    keyword("keyword") {
                        ignoreAbove="256"
                    }
                }
                copyTo = listOf("text")
            }
            text(LogMessage::stackTrace) {
                copyTo = listOf("text")
            }
            objField(LogMessage::items, dynamic = "true") {}
        }
        meta {
            put("created_by","kt-search-klogging-appender")
            put("created_at", Clock.System.now().toString())
        }
    }
    println("creating index template")
    // now create the template
    createIndexTemplate("$prefix-template") {
        indexPatterns = listOf("$prefix*")
        // make sure to specify an empty object for data_stream
        dataStream = withJsonDsl {
            // the elastic docs are a bit vague on what goes here
        }
        // make sure we outrank elastics own stuff
        priority=300
        composedOf = listOf("$prefix-template-settings", "$prefix-template-mappings")
    }
    // create the data stream
    println("creating data stream")
    createDataStream(prefix)
    return true
}