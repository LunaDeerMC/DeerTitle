plugins {
    java
    id("com.gradleup.shadow") version "9.3.2"
}

group = "cn.lunadeer"
version = providers.gradleProperty("releaseVersion").orElse("1.0.0-SNAPSHOT").get()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://api.modrinth.com/maven")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.1.build.8-alpha")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { isTransitive = false }
    compileOnly("maven.modrinth:placeholderapi:2.12.3")

    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("com.mysql:mysql-connector-j:9.3.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.papermc.paper:paper-api:26.1.1.build.8-alpha")
    testImplementation("org.slf4j:slf4j-simple:2.0.17")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") { expand("version" to project.version) }
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "512m"
    testLogging { events("failed", "skipped") }
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    relocate("com.zaxxer.hikari", "cn.lunadeer.deertitle.internal.hikari")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.jar { archiveClassifier.set("plain") }
tasks.build { dependsOn(tasks.shadowJar) }

tasks.wrapper {
    gradleVersion = "9.4.1"
    distributionType = Wrapper.DistributionType.BIN
}
