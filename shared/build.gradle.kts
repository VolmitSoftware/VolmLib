import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    kotlin("jvm") version "2.2.0"
    `maven-publish`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.compileJava {
    options.compilerArgs.add("-parameters")
    options.encoding = "UTF-8"
    options.release.set(21)
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")

    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("it.unimi.dsi:fastutil:8.5.13")
    implementation("com.google.guava:guava:33.2.1-jre")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("com.googlecode.concurrentlinkedhashmap:concurrentlinkedhashmap-lru:1.4.2")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.lz4:lz4-java:1.8.0")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "volmlib-shared"
            from(components["java"])
        }
    }
}
