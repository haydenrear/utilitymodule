plugins {
    id("com.hayden.no-main-class")
    id("com.hayden.kotlin")
    id("com.hayden.messaging")
    id("com.hayden.ai")
}

description = "utilitymodule"

java {
    version = JavaVersion.VERSION_21
}

dependencies {
    implementation("com.squareup:javapoet:1.13.0")
    annotationProcessor(project(":tracing_apt")) {
        exclude("org.junit")
    }
    api(project(":tracing_apt"))
}
