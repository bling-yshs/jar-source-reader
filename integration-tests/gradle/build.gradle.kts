plugins {
    application
}

repositories {
    mavenCentral()
}

val sourceArtifacts by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    implementation("cn.hutool:hutool-core:5.8.36")
    add(sourceArtifacts.name, "cn.hutool:hutool-core:5.8.36:sources@jar")

    if (providers.environmentVariable("JSR_ENABLE_AMBIGUOUS_HUTOOL").orNull == "true") {
        implementation("cn.hutool:hutool-all:5.8.36")
        add(sourceArtifacts.name, "cn.hutool:hutool-all:5.8.36:sources@jar")
    }
}

application {
    mainClass.set("com.yshs.jsr.integration.GradleApplication")
}

tasks.register("downloadSources") {
    doLast {
        sourceArtifacts.files.forEach { sourceArtifact ->
            println(sourceArtifact.absolutePath)
        }
    }
}
