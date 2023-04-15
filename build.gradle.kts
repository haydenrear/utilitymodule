plugins {
    id("com.hayden.java-conventions")
    kotlin("jvm") version "1.8.20"
}

description = "utilitymodule"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.squareup:javapoet:1.10.0")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(19)
}