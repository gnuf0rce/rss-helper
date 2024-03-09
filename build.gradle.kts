plugins {
    kotlin("jvm") version "1.8.22"
    kotlin("plugin.serialization") version "1.8.22"

    id("net.mamoe.mirai-console") version "2.16.0"
    id("me.him188.maven-central-publish") version "1.0.0"
}

group = "io.github.gnuf0rce"
version = "1.4.2"

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
    implementation("org.jsoup:jsoup:1.17.2")
    testImplementation(kotlin("test"))
    //
    implementation(platform("net.mamoe:mirai-bom:2.16.0"))
    testImplementation("net.mamoe:mirai-console-compiler-common")
    testImplementation("net.mamoe:mirai-core-mock")
    testImplementation("net.mamoe:mirai-logging-slf4j")
    //
    implementation(platform("io.ktor:ktor-bom:2.3.9"))
    implementation("io.ktor:ktor-client-okhttp")
    implementation("io.ktor:ktor-client-encoding")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    //
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps")
    //
    implementation(platform("com.rometools:rome-parent:2.1.0"))
    implementation("com.rometools:rome")
    implementation("com.rometools:rome-modules")
    //
    implementation(platform("org.slf4j:slf4j-parent:2.0.12"))
    testImplementation("org.slf4j:slf4j-simple")
}

mirai {
    jvmTarget = JavaVersion.VERSION_11
}

kotlin {
    explicitApi()
}

tasks {
    test {
        useJUnitPlatform()
    }
}