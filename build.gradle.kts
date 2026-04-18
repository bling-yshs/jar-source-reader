plugins {
    kotlin("jvm") version "2.3.20"
    application
    id("com.gradleup.shadow") version "9.4.1"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.github.javaparser:javaparser-core:3.28.0")
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
    testImplementation(kotlin("test"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    jvmToolchain(8)
}

application {
    mainClass.set("com.yshs.jsr.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
