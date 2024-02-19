package at.asitplus.gradle

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.authentication.http.HttpHeaderAuthentication
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.credentials


class GitlabPublishing(
    private val handler: RepositoryHandler,
    private val group: Int,
    private val extgit: Boolean,
    private val nameOverride: String?
) {
    infix fun accessTokenFrom(extra: ExtraPropertiesExtension) =
        handler.gitlabMavenRepo(
            runCatching { extra["gitLabPrivateToken"] as String }.getOrNull(),
            group,
            extgit,
            nameOverride
        )

    infix fun withAccessToken(token: String) = handler.gitlabMavenRepo(token, group, extgit, nameOverride)

}

fun RepositoryHandler.gitlab(groupId: Int, nameOverride: String? = null) =
    GitlabPublishing(this, groupId, false, nameOverride)

fun RepositoryHandler.extgit(groupId: Int, nameOverride: String? = null) =
    GitlabPublishing(this, groupId, true, nameOverride)

private fun RepositoryHandler.gitlabMavenRepo(
    gitLabPrivateToken: String?,
    gitLabGroupId: Int,
    extgit: Boolean,
    nameOverride: String?
) {

    val instance = if (extgit) "extgit" else "gitlab"
    if (System.getenv("CI_JOB_TOKEN") != null || gitLabPrivateToken != null) {
        println("  Adding $instance maven repo${nameOverride?.let { " with name $it" } ?: ""} to project")
        maven {
            name = nameOverride ?: instance
            url = java.net.URI("https://$instance.iaik.tugraz.at/api/v4/groups/$gitLabGroupId/-/packages/maven")
            if (gitLabPrivateToken != null) {
                credentials(HttpHeaderCredentials::class) {
                    name = "Private-Token"
                    value = gitLabPrivateToken
                }
            } else if (System.getenv("CI_JOB_TOKEN") != null) {
                credentials(HttpHeaderCredentials::class) {
                    name = "Job-Token"
                    value = System.getenv("CI_JOB_TOKEN")
                }
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    } else {
        println("  Warning: neither CI_JOB_TOKEN nor gitlabPrivateToken configured. Not adding $instance maven repo to project!")
        println("  If you want to locally fetch dependencies published to $instance be sure to add gitlabPrivateToken to either ~/.gradle/gradle.properties or \${project.rootDir}/local.properties!")
    }
}

fun PublishingExtension.gitLab(projectId: Int, nameOverride: String? = null) =
    gitLabPublishRepo(projectId, false, nameOverride)

fun PublishingExtension.extgit(projectId: Int, nameOverride: String? = null) =
    gitLabPublishRepo(projectId, true, nameOverride)

private fun PublishingExtension.gitLabPublishRepo(
    gitLabProjectId: Int,
    extgit: Boolean,
    nameOverride: String?
) {
    val instance = if (extgit) "extgit" else "gitlab"
    if (System.getenv("CI_JOB_TOKEN") != null) {
        println("  Adding publishing $instance maven repo${nameOverride?.let { " with name $it" } ?: ""} to project")
        repositories.maven {
            name = nameOverride ?: instance
            url =
                java.net.URI("https://$instance.iaik.tugraz.at/api/v4/projects/$gitLabProjectId/packages/maven")
            credentials(HttpHeaderCredentials::class) {
                name = "Job-Token"
                value = System.getenv("CI_JOB_TOKEN")
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    } else println("  CI_JOB_TOKEN not found. This is fine for local builds")
}