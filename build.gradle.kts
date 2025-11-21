import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.20"
    id("application")
    id("org.openjfx.javafxplugin") version "0.0.13"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.fxmisc.richtext:richtextfx:0.11.0")
    testImplementation(kotlin("test"))
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}

javafx {
    // JavaFX runtime version
    version = "21"
    modules("javafx.controls")
}

kotlin {
    // Use a stable JDK version for JavaFX; install JDK 21 jeśli jeszcze nie masz
    jvmToolchain(21)
}

tasks.withType<KotlinCompile>().configureEach { compilerOptions.jvmTarget.set(JvmTarget.JVM_21) }

tasks.test {
    useJUnitPlatform()
}

application {
    // Fully-qualified name of the main JavaFX Application class (dodamy ją za chwilę)
    mainClass.set("org.example.ScriptRunnerApp")
}
