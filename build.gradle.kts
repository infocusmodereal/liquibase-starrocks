import java.security.MessageDigest

fun computeChecksum(file: File, algorithm: String): String {
    val digest = MessageDigest.getInstance(algorithm)
    file.inputStream().use { stream ->
        val buffer = ByteArray(8192)
        var bytesRead = stream.read(buffer)
        while (bytesRead != -1) {
            digest.update(buffer, 0, bytesRead)
            bytesRead = stream.read(buffer)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

plugins {
    kotlin("jvm") version "1.8.0"
    `java-library`
    `maven-publish`
    signing
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "io.github.infocusmodereal"
val baseVersion = project.findProperty("baseVersion") as String? ?: "0.3.0"
val isRelease = (project.findProperty("isRelease") as String? ?: "false").toBoolean()
version = if (isRelease) baseVersion else "$baseVersion-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // MySQL JDBC driver - StarRocks is compatible with MySQL protocol
    runtimeOnly("com.mysql:mysql-connector-j:8.4.0")

    // Kotlin standard library - explicitly included to avoid runtime errors
    implementation(kotlin("stdlib"))
    implementation(kotlin("stdlib-jdk8"))

    // Liquibase core (provided scope in Maven, compileOnly in Gradle)
    compileOnly("org.liquibase:liquibase-core:5.0.3")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.liquibase:liquibase-core:4.23.0")
    // Mock the executor boundary while exercising the real lock service lifecycle.
    testImplementation("org.mockito:mockito-core:5.5.0")
    testRuntimeOnly("org.liquibase:liquibase-core:${findProperty("testLiquibaseVersion") ?: "5.0.3"}")

}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

tasks.test {
    useJUnitPlatform()
}

// Separate outputs prevent the thin JAR from overwriting the published CLI artifact.
tasks.jar { archiveClassifier.set("thin") }
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// Bundle only Kotlin; Liquibase and JDBC are supplied by the host.
tasks.shadowJar {
    mustRunAfter(tasks.jar)
    mergeServiceFiles()
    archiveClassifier.set("") // Replace the standard JAR with the fat JAR
    dependencies {
        include(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
        include(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk8"))
        include(dependency("org.jetbrains.kotlin:kotlin-stdlib-common"))
    }
}

// Give the integration harness exactly the archive produced by this build.
tasks.register<Sync>("prepareIntegrationJar") {
    dependsOn(tasks.jar, tasks.shadowJar)
    from(tasks.shadowJar.flatMap { it.archiveFile })
    into(layout.buildDirectory.dir("integration"))
    rename { "liquibase-starrocks.jar" }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "liquibase-starrocks"
            from(components["java"])
            setArtifacts(listOf(tasks.shadowJar, tasks.named("sourcesJar"), tasks.named("javadocJar")))
            pom {
                name.set("Liquibase StarRocks Extension")
                description.set("Liquibase extension for StarRocks database")
                url.set("https://github.com/infocusmodereal/liquibase-starrocks")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("infocusmodereal")
                        name.set("Ivan Torres")
                        email.set("ivan@infocusmode.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/infocusmodereal/liquibase-starrocks.git")
                    developerConnection.set("scm:git:ssh://github.com/infocusmodereal/liquibase-starrocks.git")
                    url.set("https://github.com/infocusmodereal/liquibase-starrocks")
                }
            }
        }
    }
}

// Signing configuration – only active for release builds
signing {
    if (!version.toString().endsWith("SNAPSHOT")) {
        val signingPassword = project.findProperty("signing.password") as String?
        val secretKeyFile = file("secret-key.asc")
        if (secretKeyFile.exists() && signingPassword != null) {
            val secretKey = secretKeyFile.readText().trim()
            useInMemoryPgpKeys(secretKey, signingPassword)
            sign(publishing.publications["maven"])
        } else {
            logger.lifecycle("Signing not performed: key file not found or passphrase missing.")
        }
    }
}

// Prepare a signed, inspectable bundle. Upload/publish is a separate maintainer action.
tasks.register("prepareCentralBundle") {
    group = "publishing"
    description = "Stages the signed publication and checksums without publishing it"
    dependsOn("signMavenPublication")
    doLast {
        check(isRelease) { "Use -PisRelease=true for a release bundle" }
        val directory = layout.buildDirectory.dir("central/io/github/infocusmodereal/liquibase-starrocks/${project.version}").get().asFile
        delete(layout.buildDirectory.dir("central"))
        directory.mkdirs()
        val name = "liquibase-starrocks-${project.version}"
        val inputs = mapOf(
            "$name.jar" to tasks.shadowJar.get().archiveFile.get().asFile,
            "$name-sources.jar" to tasks.named<Jar>("sourcesJar").get().archiveFile.get().asFile,
            "$name-javadoc.jar" to tasks.named<Jar>("javadocJar").get().archiveFile.get().asFile,
            "$name.pom" to layout.buildDirectory.file("publications/maven/pom-default.xml").get().asFile
        )
        inputs.forEach { (target, source) ->
            check(source.isFile && file("${source.path}.asc").isFile) { "Missing signed publication artifact: $target" }
            source.copyTo(directory.resolve(target), overwrite = true)
            file("${source.path}.asc").copyTo(directory.resolve("$target.asc"), overwrite = true)
            mapOf("md5" to "MD5", "sha1" to "SHA-1", "sha256" to "SHA-256", "sha512" to "SHA-512").forEach { (extension, algorithm) ->
                directory.resolve("$target.$extension").writeText(computeChecksum(source, algorithm))
            }
        }
    }
}

tasks.register<Zip>("createCentralBundle") {
    group = "publishing"
    dependsOn("prepareCentralBundle")
    from(layout.buildDirectory.dir("central"))
    archiveFileName.set("liquibase-starrocks-${project.version}-central.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}

// Keep the upstream harness isolated from the oldest-runtime unit test classpath.
val harness = sourceSets.create("harness")
harness.compileClasspath += sourceSets.main.get().output
harness.runtimeClasspath += sourceSets.main.get().output

dependencies {
    add(harness.implementationConfigurationName, "org.liquibase:liquibase-test-harness:1.0.12") { isTransitive = false }
    add(harness.implementationConfigurationName, "org.liquibase:liquibase-core:5.0.3")
    add(harness.implementationConfigurationName, "org.apache.groovy:groovy:5.0.6")
    add(harness.implementationConfigurationName, "org.apache.groovy:groovy-json:5.0.6")
    add(harness.implementationConfigurationName, "org.spockframework:spock-core:2.4-groovy-5.0")
    add(harness.implementationConfigurationName, "org.junit.platform:junit-platform-suite:6.1.0")
    add(harness.implementationConfigurationName, "org.junit.jupiter:junit-jupiter:6.1.0")
    add(harness.implementationConfigurationName, "org.skyscreamer:jsonassert:1.5.3")
    add(harness.implementationConfigurationName, "org.yaml:snakeyaml:2.6")
    add(harness.implementationConfigurationName, "commons-io:commons-io:2.22.0")
    add(harness.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher:6.1.0")
    add(harness.runtimeOnlyConfigurationName, "com.mysql:mysql-connector-j:8.4.0")
    add(harness.runtimeOnlyConfigurationName, kotlin("stdlib"))
}

tasks.register<Test>("harnessTest") {
    description = "Runs the upstream Liquibase harness against a disposable StarRocks database"
    group = "verification"
    testClassesDirs = harness.output.classesDirs
    classpath = harness.runtimeClasspath
    useJUnitPlatform()
    systemProperty("dbUrl", providers.gradleProperty("harnessUrl").getOrElse("jdbc:mysql://localhost:9030/harness_test"))
    systemProperty("dbVersion", providers.gradleProperty("harnessStarRocksVersion").getOrElse("4.1.1"))
    systemProperty("changeObjects", "createStarRocksTable")
    systemProperty("revalidateSql", "true")
}

// The Maven publication distributes the shaded JAR, not the Java component's thin variant.
// Publish its explicit POM/artifact contract instead of inconsistent Gradle variants.
tasks.withType<GenerateModuleMetadata>().configureEach { enabled = false }
