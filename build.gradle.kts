plugins {
    id("com.hayden.no-main-class")
    id("com.hayden.kotlin")
    id("com.hayden.messaging")
    id("com.hayden.ai")
    id("com.hayden.security")
}

description = "utilitymodule"

java {
    version = JavaVersion.VERSION_22
}

dependencies {
    implementation("com.squareup:javapoet:1.13.0")
    annotationProcessor(project(":tracing_apt")) {
        exclude("org.junit")
    }
    api(project(":tracing_apt"))
    annotationProcessor(project(":inject_fields"))
    testAnnotationProcessor(project(":inject_fields"))
    api(project(":inject_fields"))
}

