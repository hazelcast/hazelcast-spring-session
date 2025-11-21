plugins {
    `java-library`
    checkstyle
    id("com.vanniktech.maven.publish") version "0.34.0"
    id("net.researchgate.release") version "3.1.0"
    jacoco
}

group = "com.hazelcast.spring"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
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
val junitVersion = "6.0.1"
val mockitoVersion = "5.16.1"
val assertjVersion = "3.27.3"
val testcontainersVersion = "2.0.1"

sourceSets {
    create("integrationTest", Action<SourceSet> {
        java {
            srcDirs("src/integration-test/java", "build/generated-classes/java")
        }
        resources {
            srcDir("src/integration-test/resources")
        }
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    })
}

val processBuildContext = tasks.register<ProcessResources>("processBuildContext") {
    println("processing resources from " + project.projectDir.path + "/src/integration-test/resources/")
    from(project.projectDir.path + "/src/integration-test/resources/")
    into(project.projectDir.path + "/src/integration-test/java/com/hazelcast/spring/session/")
    include("**BuildContext.java")
    filesMatching("**BuildContext.java") {
        expand(project.properties)
    }
}

tasks.compileJava.get().dependsOn(processBuildContext)

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
    finalizedBy(tasks.jacocoTestReport, tasks.jacocoTestCoverageVerification)
}

dependencies {
    api("org.springframework.session:spring-session-core:$springSessionVersion")

    api("com.hazelcast:hazelcast:$hazelcastVersion")

    // Spring Framework
    api("org.springframework:spring-context:$springFrameworkVersion")
    implementation("org.springframework:spring-beans:$springFrameworkVersion")
    implementation("org.springframework:spring-core:$springFrameworkVersion")

    // other
    implementation("org.jspecify:jspecify:1.0.0")
    implementation("org.slf4j:slf4j-api:2.0.17")

    // Test dependencies
    testImplementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.2")
    testImplementation("jakarta.servlet:jakarta.servlet-api:$jakartaServletVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:${junitVersion}")
    testImplementation("org.junit.platform:junit-platform-suite:${junitVersion}")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    testImplementation("org.mockito:mockito-junit-jupiter:$mockitoVersion")
    testImplementation("org.springframework.security:spring-security-core:$springSecurityVersion")
    testImplementation("org.springframework:spring-test:$springFrameworkVersion")
    testImplementation("org.springframework:spring-web:$springFrameworkVersion")

    testImplementation("com.hazelcast:hazelcast:$hazelcastVersion:tests")
    // for hazelcast test network assertions
    testImplementation("junit:junit:4.13.2")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    integrationTestImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    integrationTestImplementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.2")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("java.net.preferIPv4Stack", "true")
    systemProperty("hazelcast.phone.home.enabled", "false")
    jvmArgs(
        "--add-exports", "java.base/jdk.internal.ref=ALL-UNNAMED",
        "--add-opens", "java.base/java.nio=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "--add-opens", "java.base/java.util.concurrent=ALL-UNNAMED",
        "--add-opens", "jdk.management/com.sun.management.internal=ALL-UNNAMED",
        "--add-opens", "java.management/sun.management=ALL-UNNAMED",
        "-Djava.net.preferIPv4Stack=true"
    )
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
        csv.required = true
    }
    dependsOn(tasks.test, integrationTest)
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = BigDecimal("0.5")
            }
        }
    }
    dependsOn(tasks.jacocoTestReport)
}

release {
    tagTemplate = "v\${version}"
    newVersionCommitMessage = "Start development"
    preTagCommitMessage = "Release"
    failOnSnapshotDependencies = true
}

tasks {
    afterReleaseBuild {
        dependsOn ("publishToMavenCentral")
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = false)

    signAllPublications()

    pom {
        name = "Hazelcast Spring Session"
        description = "Spring Session implementation using Hazelcast"
        url = "https://github.com/hazelcast/hazelcast-spring-session"

        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }

        developers {
            developer {
                id = "hazelcast"
                name = "Hazelcast Inc."
                email = "info@hazelcast.com"
            }
        }

        scm {
            connection = "scm:git:git://github.com/hazelcast/hazelcast-spring-session.git"
            developerConnection = "scm:git:ssh://github.com:hazelcast/hazelcast-spring-session.git"
            url = "https://github.com/hazelcast/hazelcast-spring-session"
        }
    }
}

tasks.register("printVersion") {
    doLast {
        print(project.version)
    }
}

tasks.register("prepareITs") {
    dependsOn(tasks.assemble, copyHSSJar, copySpringJars);
}
