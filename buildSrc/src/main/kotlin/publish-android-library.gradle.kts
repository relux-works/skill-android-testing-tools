// Convention plugin for publishing Android libraries to GitHub Packages

plugins {
    id("maven-publish")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = project.group.toString()
                artifactId = project.name
                version = project.version.toString()

                pom {
                    name.set(project.name)
                    description.set("Android UI Testing Tools - ${project.name}")
                    url.set("https://github.com/ivalx1s/android-ui-testing-tools")

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }

                    developers {
                        developer {
                            id.set("ivalx1s")
                            name.set("ivalx1s")
                            url.set("https://github.com/ivalx1s")
                        }
                    }

                    scm {
                        url.set("https://github.com/ivalx1s/android-ui-testing-tools")
                        connection.set("scm:git:git://github.com/ivalx1s/android-ui-testing-tools.git")
                        developerConnection.set("scm:git:ssh://github.com/ivalx1s/android-ui-testing-tools.git")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/ivalx1s/android-ui-testing-tools")
                credentials {
                    username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String? ?: ""
                    password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.key") as String? ?: ""
                }
            }
        }
    }
}
