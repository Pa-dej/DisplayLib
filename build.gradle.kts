plugins {
    java
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "padej"
version = "1.21.11+2.0.0"

repositories {
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("com.mojang:authlib:1.5.21") {
        exclude(group = "org.apache.logging.log4j", module = "log4j-core")
        exclude(group = "log4j", module = "log4j")
    }
    implementation("commons-io:commons-io:2.15.1")
}

tasks {
    runServer {
        minecraftVersion("1.21")
    }

    named("build") { // nyhehe
        doLast {
            println("Сборка завершена!")
            copy {
                from(layout.buildDirectory.dir("libs"))
                into("C:\\Users\\User\\Desktop\\servers\\paper-1.21.11\\plugins")
                include("*-${version}.jar")
                exclude("*-sources.jar")
            }
        }
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}

java {
    val javaVersion = JavaVersion.toVersion(21)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion = JavaLanguageVersion.of(21)
    }
}