plugins {
    `java-library`
    checkstyle
}

group = "com.hazelcast.spring"
version = "4.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/snapshot") }
}

// Overridable versions with -Pproperty=value
val springSessionVersion: String by project
val springFrameworkVersion: String by project
val springSecurityVersion: String by project
val hazelcastVersion: String by project

val jakartaServletVersion = "6.1.0"
val junitVersion = "5.12.1"
val mockitoVersion = "5.16.1"
val assertjVersion = "3.27.3"
val testcontainersVersion = "2.0.1"

sourceSets {
    create("integrationTest", Action<SourceSet> {
        java {
            srcDir("src/integration-test/java")
        }
        resources {
            srcDir("src/integration-test/resources")
        }
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    })
}

val mainArtifactFile = configurations.getAt("archives").artifacts.files.singleFile

val integrationTestImplementation by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}
val integrationTestRuntimeOnly by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}

val copyHSSJar = tasks.register<Copy>("copyHSSJar") {
    description = "Copies Hazelcast Spring Session Jar for usage in Docker tests"
    group = "verification"

    dependsOn(tasks.jar)

    from(mainArtifactFile)
    rename { "HSS.jar" }
    destinationDir = file(layout.buildDirectory.file("forDocker"))
}
val copySpringJars = tasks.register<Copy>("copySpringJars") {
    description = "Copies Spring Jars for usage in Docker tests"
    group = "verification"

    dependsOn(tasks.jar)

    from(configurations.runtimeClasspath) {
        // Optionally, filter files if needed
        include("spring*.jar")
    }
    destinationDir = file(layout.buildDirectory.file("forDocker"))
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath + files(layout.buildDirectory.dir("forDocker"))
    dependsOn(copyHSSJar, copySpringJars)
    shouldRunAfter(tasks.test)

    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.check {
    dependsOn(integrationTest)
}

dependencies {
    api("org.springframework.session:spring-session-core:$springSessionVersion")

    api("com.hazelcast:hazelcast:$hazelcastVersion")

    // Spring Framework
    api("org.springframework:spring-context:$springFrameworkVersion")
    implementation("org.springframework:spring-beans:$springFrameworkVersion")
    implementation("org.springframework:spring-core:$springFrameworkVersion")

    // Test dependencies
    testImplementation("jakarta.servlet:jakarta.servlet-api:$jakartaServletVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    testImplementation("org.mockito:mockito-junit-jupiter:$mockitoVersion")
    testImplementation("org.springframework.security:spring-security-core:$springSecurityVersion")
    testImplementation("org.springframework:spring-test:$springFrameworkVersion")
    testImplementation("org.springframework:spring-web:$springFrameworkVersion")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    integrationTestImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
