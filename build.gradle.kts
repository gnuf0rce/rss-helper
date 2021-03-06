plugins {
    kotlin("jvm") version Versions.kotlin
    kotlin("plugin.serialization") version Versions.kotlin
    id("net.mamoe.mirai-console") version Versions.mirai
}

group = "io.github.gnuf0rce"
version = "1.0.0-dev-6"

mirai {
    jvmTarget = JavaVersion.VERSION_11
    configureShadow {
        exclude {
            it.path.startsWith("kotlin")
        }
    }
}

repositories {
    mavenLocal()
    maven(url = "https://maven.aliyun.com/repository/public")
    mavenCentral()
    jcenter()
    maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
    gradlePluginPortal()
}

kotlin {
    sourceSets {
        all {
            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
            languageSettings.useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")
            languageSettings.useExperimentalAnnotation("io.ktor.util.KtorExperimentalAPI")
            languageSettings.useExperimentalAnnotation("net.mamoe.mirai.console.util.ConsoleExperimentalApi")
        }
        test {
            languageSettings.useExperimentalAnnotation("net.mamoe.mirai.console.ConsoleFrontEndImplementation")
        }
    }
}

dependencies {
    implementation(ktor("client-encoding", Versions.ktor)) {
        exclude(group = "io.ktor", module = "client-core")
    }
    // compileOnly(okhttp3("okhttp", Versions.okhttp))
    implementation(okhttp3("okhttp-dnsoverhttps", Versions.okhttp)) {
        exclude(group = "com.squareup.okhttp3")
    }
    implementation(rome(Versions.rome))
    // implementation(rome("modules", Versions.rome))
    implementation(jsoup(Versions.jsoup))
    // test
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter", version = Versions.junit)
}

tasks {
    test {
        useJUnitPlatform()
    }
}