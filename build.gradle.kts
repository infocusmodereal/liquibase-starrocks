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
val baseVersion = project.findProperty("baseVersion") as String? ?: "0.1.0"
val isRelease = (project.findProperty("isRelease") as String? ?: "false").toBoolean()
version = if (isRelease) baseVersion else "$baseVersion-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // MySQL JDBC driver - StarRocks is compatible with MySQL protocol
    implementation("com.mysql:mysql-connector-j:8.4.0")

    // Kotlin standard library - explicitly included to avoid runtime errors
    implementation(kotlin("stdlib"))
    implementation(kotlin("stdlib-jdk8"))

    // Liquibase core (provided scope in Maven, compileOnly in Gradle)
    compileOnly("org.liquibase:liquibase-core:4.23.0")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.testcontainers:junit-jupiter:1.19.0")
    testImplementation("org.liquibase:liquibase-core:4.23.0")

    // Other dependencies
    implementation("org.yaml:snakeyaml:2.0")
    implementation("com.typesafe:config:1.4.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withJavadocJar()
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
}

// Configure the Shadow plugin to create a fat JAR with all dependencies
tasks.shadowJar {
    mergeServiceFiles()
    archiveClassifier.set("") // Replace the standard JAR with the fat JAR
    dependencies {
        include(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
        include(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk8"))
        include(dependency("org.jetbrains.kotlin:kotlin-stdlib-common"))
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "liquibase-starrocks"
            from(components["java"])
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
    repositories {
        maven {
            name = "OSSRH"
            url = if (version.toString().endsWith("SNAPSHOT"))
                uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            else
                uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = (project.findProperty("ossrhUsername") as String?)
                    ?: System.getenv("OSSRH_USERNAME")
                password = (project.findProperty("ossrhPassword") as String?)
                    ?: System.getenv("OSSRH_PASSWORD")
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

// Task: Generate checksums and signatures for artifacts
tasks.register("generateChecksumsAndSignatures") {
    group = "publishing"
    description = "Generates MD5, SHA1 checksums and GPG signatures for each artifact."
    doLast {
        // Define the base file name (ensure this matches your artifact naming)
        val baseName = "liquibase-starrocks-${project.version}"
        // Copy the generated POM from the publications folder to build/libs with the desired name.
        val pomSource = file("build/publications/maven/pom-default.xml")
        val pomFile = file("build/libs/$baseName.pom")
        if (pomSource.exists()) {
            pomSource.copyTo(pomFile, overwrite = true)
        }
        // Create a list of files that should be processed.
        val filesToProcess = listOf(
            file("build/libs/$baseName.jar"),
            file("build/libs/$baseName-sources.jar"),
            file("build/libs/$baseName-javadoc.jar"),
            pomFile
        ).filter { it.exists() }

        // Get the signing key ID from properties.
        val signingKeyId = project.findProperty("signing.keyId") as String? ?: throw GradleException("signing.keyId not set")

        filesToProcess.forEach { artifact ->
            // Generate MD5 and SHA1 checksums.
            val md5 = computeChecksum(artifact, "MD5")
            val sha1 = computeChecksum(artifact, "SHA-1")
            file("${artifact.absolutePath}.md5").writeText(md5)
            file("${artifact.absolutePath}.sha1").writeText(sha1)
            // Use GPG to sign the file with the specified key.
            val signingPassword = project.findProperty("signing.password") as String? ?: ""
            exec {
                commandLine(
                    "gpg",
                    "--batch",
                    "--yes",
                    "--pinentry-mode", "loopback",
                    "--passphrase", signingPassword,
                    "--local-user", signingKeyId,
                    "--armor",
                    "--detach-sign",
                    artifact.absolutePath
                )
            }
        }
    }
}

// Task: Create the deployment bundle (ZIP) with proper directory structure and all required files
tasks.register<Zip>("createCentralBundle") {
    group = "publishing"
    description = "Creates a ZIP bundle for deployment including artifacts, checksums, and signatures."
    dependsOn("generateChecksumsAndSignatures")
    archiveFileName.set("central-bundle.zip")
    destinationDirectory.set(file("$buildDir/distributions"))

    // Place files under the Maven directory structure: groupId/artifactId/version
    val artifactDir = "io/github/infocusmodereal/liquibase-starrocks/${project.version}/"

    from("build/libs") {
        include(
            "${project.name}-${project.version}.jar",
            "${project.name}-${project.version}-sources.jar",
            "${project.name}-${project.version}-javadoc.jar",
            "${project.name}-${project.version}.pom",
            "${project.name}-${project.version}.jar.md5",
            "${project.name}-${project.version}.jar.sha1",
            "${project.name}-${project.version}-sources.jar.md5",
            "${project.name}-${project.version}-sources.jar.sha1",
            "${project.name}-${project.version}-javadoc.jar.md5",
            "${project.name}-${project.version}-javadoc.jar.sha1",
            "${project.name}-${project.version}.pom.md5",
            "${project.name}-${project.version}.pom.sha1",
            "${project.name}-${project.version}.jar.asc",
            "${project.name}-${project.version}-sources.jar.asc",
            "${project.name}-${project.version}-javadoc.jar.asc",
            "${project.name}-${project.version}.pom.asc"
        )
    }
    into(artifactDir)
}

// Task: Upload the deployment bundle using the Portal Publisher API via curl
tasks.register<Exec>("uploadToCentral") {
    group = "publishing"
    description = "Uploads the deployment bundle to Maven Central via the Portal Publisher API."
    dependsOn("createCentralBundle")
    val bundleZip = file("$buildDir/distributions/central-bundle.zip")
    val centralToken = (project.findProperty("centralToken") as String?)
        ?: System.getenv("CENTRAL_TOKEN")
        ?: throw GradleException("Central token not provided. Define centralToken in gradle.properties or as CENTRAL_TOKEN env var.")
    commandLine(
        "curl",
        "--request", "POST",
        "--verbose",
        "--header", "Authorization: Bearer $centralToken",
        "--form", "bundle=@${bundleZip.absolutePath}",
        "https://central.sonatype.com/api/v1/publisher/upload"
    )
}
