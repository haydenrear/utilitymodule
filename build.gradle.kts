plugins {
    id("com.hayden.no-main-class")
}

description = "utilitymodule"

java {
    version = JavaVersion.VERSION_21
}

dependencies {
    implementation("com.squareup:javapoet:1.13.0")
}

