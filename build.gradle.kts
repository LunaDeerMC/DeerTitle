plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "cn.lunadeer"
version = "0.1.1-SNAPSHOT"

val runtimeLibraries = listOf(
    "com.zaxxer:HikariCP:6.2.1",
    "org.xerial:sqlite-jdbc:3.49.1.0",
    "org.mariadb.jdbc:mariadb-java-client:3.5.3"
)

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://oss.sonatype.org/content/groups/public")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")

    runtimeLibraries.forEach {
        compileOnly(it)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.processResources {
    outputs.upToDateWhen { false }
    filesMatching("**/plugin.yml") {
        expand(
            mapOf(
                "version" to project.version.toString(),
                "libraries" to runtimeLibraries.joinToString("\n") { "  - $it" }
            )
        )
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    archiveFileName.set("${project.name}-${project.version}.jar")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.register("Clean&Build") {
    dependsOn(tasks.clean)
    dependsOn(tasks.shadowJar)
}
