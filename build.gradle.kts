import com.avast.gradle.dockercompose.ComposeExtension
import java.net.URL

buildscript {
    repositories {
        mavenCentral()
    }
}
repositories {
    mavenCentral()
    maven("https://maven.tryformation.com/releases") {
        content {
            includeGroup("com.jillesvangurp")
        }
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.dokka")
    id("maven-publish")
    kotlin("plugin.serialization")
    id("com.avast.gradle.docker-compose")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

configure<ComposeExtension> {
    buildAdditionalArgs.set(listOf("--force-rm"))
    stopContainers.set(true)
    removeContainers.set(true)
    forceRecreate.set(true)
    useComposeFiles.set(listOf("docker-compose-es-7.yml"))
    setProjectName("kt_search_klogging_test")
}

dependencies {
    api(Kotlin.stdlib.jdk8)
    // use -jvm dependencies here because otherwise kts fails to fetch
    api("com.jillesvangurp:search-client-jvm:_")
    api("io.klogging:klogging-jvm:_")

    // bring your own logging, but we need some in tests
    // we don't generally want to support slf4j and MDC, but we need to..
    testImplementation("io.klogging:slf4j-klogging:_")
    testImplementation("io.github.microutils:kotlin-logging:_")
    testImplementation(KotlinX.coroutines.core)
    testImplementation(KotlinX.coroutines.test)
    testImplementation(Testing.junit.jupiter.api)
    testImplementation(Testing.junit.jupiter.engine)
    testImplementation(Testing.kotest.assertions.core)
}

tasks.withType<Test> {
    val isUp = try {
        URL("http://localhost:9999").openConnection().connect()
        true
    } catch (e: Exception) {
        false
    }
    if (!isUp) {
        dependsOn("composeUp")
    }
    useJUnitPlatform()
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    testLogging.events = setOf(
        org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
        org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
        org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
        org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR,
        org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT
    )
    if (!isUp) {
        this.finalizedBy("composeDown")
    }
}

val artifactName = "kt-search-klogging-appender"
val artifactGroup = "com.github.nikkyai"

val sourceJar = task("sourceJar", Jar::class) {
    dependsOn(tasks["classes"])
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val javadocJar = task("javadocJar", Jar::class) {
    from(tasks["dokkaJavadoc"])
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = artifactGroup
            artifactId = artifactName
            pom {
                description.set("log to elasticsearch datastreams with klogging")
                name.set(artifactId)
                url.set("https://github.com/NikkyAI/kt-search-klogging-appender")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://github.com/NikkyAI/kt-search-klogging-appender/LICENSE")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("jillesvangurp")
                        name.set("Jilles van Gurp")
                    }
                    developer {
                        id.set("NikkyAI")
                        name.set("Nikky")
                    }
                }
                scm {
                    url.set("https://github.com/NikkyAI/kt-search-klogging-appender.git")
                }
            }

            from(components["java"])
            artifact(sourceJar)
            artifact(javadocJar)
        }
    }
    repositories {
        maven {
            url = uri("gcs://mvn-public-tryformation/releases")
        }
    }
}
