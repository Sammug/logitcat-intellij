plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij") version "1.17.3"
}

group = "com.sammug"
version = "0.1.0"

repositories {
    mavenCentral()
}

intellij {
    version.set("2023.1.6")
    type.set("IC")
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
    patchPluginXml {
        sinceBuild.set("231")
        untilBuild.set("263.*")
    }
    signPlugin {
        certificateChain.set("")
        privateKey.set("")
        password.set("")
    }
    publishPlugin {
        token.set("")
    }
}