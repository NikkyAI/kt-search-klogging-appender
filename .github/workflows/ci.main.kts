#!/usr/bin/env kotlin

@file:DependsOn("it.krzeminski:github-actions-kotlin-dsl:0.31.0")

import it.krzeminski.githubactions.actions.actions.CheckoutV3
import it.krzeminski.githubactions.actions.actions.GithubScriptV6
import it.krzeminski.githubactions.actions.actions.SetupJavaV3
import it.krzeminski.githubactions.actions.gradle.GradleBuildActionV2
import it.krzeminski.githubactions.domain.RunnerType.UbuntuLatest
import it.krzeminski.githubactions.domain.triggers.PullRequest
import it.krzeminski.githubactions.domain.triggers.Push
import it.krzeminski.githubactions.dsl.expressions.expr
import it.krzeminski.githubactions.dsl.workflow
import it.krzeminski.githubactions.yaml.writeToFile

val workflow = workflow(
    name = "CI",
    on = listOf(
        Push(
            branches = listOf("main")
        ),
        PullRequest(
            branches = listOf("main")
        ),
    ),
    sourceFile = __FILE__.toPath(),
) {
    job(id = "build", runsOn = UbuntuLatest) {
        uses(
            name = "Checkout",
            action = CheckoutV3()
        )
        uses(
            name = "setup java",
            action = SetupJavaV3(
                javaVersion = "17",
                distribution = SetupJavaV3.Distribution.Adopt,
                cache = SetupJavaV3.BuildPlatform.Gradle,
            )
        )
        val gradleBuildStep = uses(
            name = "build with gradle",
            action = GradleBuildActionV2(
                arguments = "clean check build -PdoFailFast --scan",
            )
        )
        uses(
            name = "comment build scan url",
            condition = "github.event_name == 'pull_request' && failure()",
            action = GithubScriptV6(
                script = """
                    github.rest.issues.createComment({
                      issue_number: context.issue.number,
                      owner: context.repo.owner,
                      repo: context.repo.repo,
                      body: '❌ ${expr("github.workflow")} ${expr("job.name")} failed: ${expr(gradleBuildStep.outputs.buildScanUrl)}'
                    })
                """.trimIndent()
            )
        )
    }
}

workflow.writeToFile(
    addConsistencyCheck = true
)