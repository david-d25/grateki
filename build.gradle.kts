group = "space.davids_digital.grateki"
version = "0.0.0"

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
    application
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
}

dependencies {
    // CLI
    implementation(libs.picocli.core)
    kapt(libs.picocli.codegen)

    // Gradle Tooling API
    implementation(libs.gradle.toolingApi)
    runtimeOnly(libs.slf4j.simple)

    // MapStruct
    implementation(libs.mapstruct.core)
    kapt(libs.mapstruct.processor)

    // JSON serialization
    implementation(libs.kotlinx.serialization.json)

    // Logging
    implementation(libs.slf4j.simple)
}

kapt {
    arguments {
        arg("project", "${project.group}/${project.name}")
    }
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("space.davids_digital.grateki.AppKt")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "space.davids_digital.grateki.AppKt"
    }
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}