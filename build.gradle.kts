plugins {
    kotlin("jvm") version "1.7.10"
    kotlin("plugin.serialization") version "1.7.10"

    id("net.mamoe.mirai-console") version "2.13.0-M1"
    id("me.him188.maven-central-publish") version "1.0.0-dev-3"
}

group = "io.github.gnuf0rce"
version = "1.2.5"

mavenCentralPublish {
    useCentralS01()
    singleDevGithubProject("gnuf0rce", "rss-helper", "cssxsh")
    licenseFromGitHubProject("AGPL-3.0")
    workingDir = System.getenv("PUBLICATION_TEMP")?.let { file(it).resolve(projectName) }
        ?: buildDir.resolve("publishing-tmp")
    publication {
        artifact(tasks["buildPlugin"])
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-client-okhttp:2.1.1") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "org.slf4j")
    }
    implementation("io.ktor:ktor-client-encoding:2.1.1") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "org.slf4j")
    }
    implementation("io.ktor:ktor-client-content-negotiation:2.1.1") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "org.slf4j")
    }
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.1.1") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "org.slf4j")
    }
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.10.0") {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation("com.rometools:rome:1.18.0") {
        exclude(group = "org.slf4j")
    }
    implementation("org.jsoup:jsoup:1.15.3")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    // test
    testImplementation(kotlin("test"))
    testImplementation("org.slf4j:slf4j-simple:2.0.1")
    testImplementation("net.mamoe:mirai-logging-slf4j:2.13.0-M1")
}

mirai {
    jvmTarget = JavaVersion.VERSION_11
}

tasks {
    test {
        useJUnitPlatform()
    }
}