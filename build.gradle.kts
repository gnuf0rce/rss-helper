plugins {
    kotlin("jvm") version Versions.kotlin
    kotlin("plugin.serialization") version Versions.kotlin

    id("net.mamoe.mirai-console") version Versions.mirai
    id("net.mamoe.maven-central-publish") version "0.7.0"
}

group = "io.github.gnuf0rce"
version = "1.1.0"

mavenCentralPublish {
    useCentralS01()
    singleDevGithubProject("gnuf0rce", "rss-helper", "cssxsh")
    licenseFromGitHubProject("AGPL-3.0", "master")
    publication {
        artifact(tasks.getByName("buildPlugin"))
    }
}

mirai {
    jvmTarget = JavaVersion.VERSION_11
    configureShadow {
        exclude {
            it.path.startsWith("kotlin")
        }
        exclude {
            it.path.startsWith("org/intellij")
        }
        exclude {
            it.path.startsWith("org/jetbrains")
        }
        exclude {
            it.path.startsWith("org/slf4j")
        }
    }
}

repositories {
    mavenLocal()
    maven(url = "https://maven.aliyun.com/repository/central")
    mavenCentral()
    maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
    gradlePluginPortal()
}

kotlin {
    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        test {
            languageSettings.optIn("net.mamoe.mirai.console.ConsoleFrontEndImplementation")
        }
    }
}

dependencies {
    implementation(ktor("client-encoding", Versions.ktor)) {
        exclude(group = "io.ktor", module = "ktor-client-core")
    }
    implementation(ktor("client-serialization", Versions.ktor)) {
        exclude(group = "io.ktor", module = "ktor-client-core")
    }
    implementation(okhttp3("okhttp-dnsoverhttps", Versions.okhttp)) {
        exclude(group = "com.squareup.okhttp3")
    }
    implementation(rome(Versions.rome))
    implementation(jsoup(Versions.jsoup))
    // test
    testImplementation(kotlin("test"))
}

tasks {
    test {
        useJUnitPlatform()
    }
}