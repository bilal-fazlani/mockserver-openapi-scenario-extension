plugins {
    `java-library`
    id("com.vanniktech.maven.publish") version "0.36.0"
}

group = "com.bilal-fazlani"
version = providers.gradleProperty("VERSION_NAME").orElse("0.1.0-SNAPSHOT").get()

val mockServerVersion = "7.0.0"
val jacksonVersion = "2.20.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

dependencies {
    compileOnly("org.mock-server:mockserver-netty:$mockServerVersion")

    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:4.0.0-M1")
    testImplementation("org.mock-server:mockserver-netty:$mockServerVersion")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Jar>("runtimeJar") {
    group = "build"
    description = "Builds a Docker /libs friendly jar including runtime dependencies but excluding MockServer."
    archiveClassifier.set("all")
    archiveFileName.set("mockserver-openapi-scenario-extension-all.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
}

mavenPublishing {
    coordinates(
        groupId = "com.bilal-fazlani",
        artifactId = "mockserver-openapi-scenario-extension",
        version = project.version.toString(),
    )

    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set("MockServer OpenAPI Scenario Extension")
        description.set("MockServer expectation initializer that maps x-mockserver-scenarios OpenAPI extensions to executable expectations.")
        inceptionYear.set("2026")
        url.set("https://github.com/bilal-fazlani/mockserver-openapi-scenario-extension")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("bilal-fazlani")
                name.set("Bilal Fazlani")
                url.set("https://bilal-fazlani.com")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/bilal-fazlani/mockserver-openapi-scenario-extension.git")
            developerConnection.set("scm:git:ssh://git@github.com/bilal-fazlani/mockserver-openapi-scenario-extension.git")
            url.set("https://github.com/bilal-fazlani/mockserver-openapi-scenario-extension")
        }
    }
}
