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

val genTestSources by tasks.registering {
    println("Generating test sources...")
    doLast {
        exec {
            workingDir = buildDir
            executable = "java"
            args = mutableListOf("-jar", "libs/inkblot-1.0-SNAPSHOT-all.jar", "generate", "-p", "gen", "../src/test/resources/bike-example.json", "../src/test/kotlin/gen")
        }
    }
}

genTestSources {
    dependsOn(tasks.shadowJar)
    outputs.upToDateWhen { false }
}

tasks.compileTestKotlin {
    dependsOn(genTestSources)
}



dependencies {
    testImplementation(kotlin("test"))
    implementation("org.apache.jena:jena-core:4.4.0")
    implementation("org.apache.jena:apache-jena-libs:4.4.0")
    implementation("com.github.ajalt.clikt:clikt:3.5.0")
    implementation("org.slf4j:slf4j-nop:2.0.6")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0-RC")
}