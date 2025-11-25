plugins {
    java
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(project(":api"))
    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}