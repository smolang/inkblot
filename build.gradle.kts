import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.21"
    kotlin("plugin.serialization") version "1.8.0"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    application
}

group = "net.rec0de.inkblot"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("MainKt")
}

sourceSets{
    create("lib") {
        java {
            srcDirs("src/main/kotlin/net/rec0de/inkblot/runtime")
        }
    }
}

tasks.register<Jar>("runtimeJar") {
    from(sourceSets["lib"].output)
    archiveFileName.set("inkblot-runtime.jar")
}

val libImplementation by configurations.getting {}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.apache.jena:jena-core:4.4.0")
    implementation("org.apache.jena:apache-jena-libs:4.4.0")
    implementation("com.github.ajalt.clikt:clikt:3.5.0")
    implementation("org.slf4j:slf4j-nop:2.0.6")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0-RC")
    libImplementation("org.apache.jena:apache-jena-libs:4.4.0")
}