import com.hayden.haydenbomplugin.BuildSrcVersionCatalogCollector

plugins {
    id("com.hayden.no-main-class")
    id("com.hayden.log")
    id("com.hayden.kotlin")
    id("com.hayden.messaging")
    id("com.hayden.ai")
    id("com.hayden.security")
    id("com.hayden.bom-plugin")
}

description = "utilitymodule"

java {
    version = JavaVersion.VERSION_21
}

val vC = project.extensions.getByType(BuildSrcVersionCatalogCollector::class.java)

dependencies {

    vC.bundles.opentelemetryBundle.inBundle()
        .map { implementation(it) }

    implementation("com.squareup:javapoet:1.13.0")
    annotationProcessor(project(":tracing_apt")) {
        exclude("org.junit")
    }
    api(project(":tracing_apt"))
//    annotationProcessor(project(":inject_fields"))
//    testAnnotationProcessor(project(":inject_fields"))
//    api(project(":inject_fields"))
}

