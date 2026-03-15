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

    // Authlib для TextureCube (скины игроков)
    implementation("com.mojang:authlib:1.5.21") {
        exclude(group = "org.apache.logging.log4j", module = "log4j-core")
        exclude(group = "log4j", module = "log4j")
    }

    implementation("commons-io:commons-io:2.15.1")
    implementation("org.luaj:luaj-jse:3.0.1")
}

tasks {
    runServer {
        minecraftVersion("1.21")
    }

    jar {
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    named("build") {
        doLast {
            println("FINISH!")
            
            // Copy JAR to server (with overwrite)
            copy {
                from(layout.buildDirectory.dir("libs"))
                into("C:\\Users\\User\\Desktop\\servers\\paper-1.21.11\\plugins")
                include("*-${version}.jar")
                exclude("*-sources.jar")
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
            }
            
            // Copy example files to server plugin directory
            val serverPluginDir = "C:\\Users\\User\\Desktop\\servers\\paper-1.21.11\\plugins\\DisplayLib"
            
            // Create directories
            file("${serverPluginDir}\\screens").mkdirs()
            file("${serverPluginDir}\\scripts").mkdirs()
            
            // Copy example screens (with overwrite)
            copy {
                from("src/main/resources/examples/screens")
                into("${serverPluginDir}\\screens")
                include("*.yml", "*.yaml")
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
            }
            
            // Copy example scripts (with overwrite)
            copy {
                from("src/main/resources/examples/scripts")
                into("${serverPluginDir}\\scripts")
                include("*.lua")
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
            }
            
            println("Example files copied to server!")
        }
    }

    // Task to copy only examples without rebuilding
    register("copyExamples") {
        group = "development"
        description = "Copy example files to server without rebuilding"
        
        doLast {
            val serverPluginDir = "C:\\Users\\User\\Desktop\\servers\\paper-1.21.11\\plugins\\DisplayLib"
            
            // Create directories
            file("${serverPluginDir}\\screens").mkdirs()
            file("${serverPluginDir}\\scripts").mkdirs()
            
            // Copy example screens (with overwrite)
            copy {
                from("src/main/resources/examples/screens")
                into("${serverPluginDir}\\screens")
                include("*.yml", "*.yaml")
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
            }
            
            // Copy example scripts (with overwrite)
            copy {
                from("src/main/resources/examples/scripts")
                into("${serverPluginDir}\\scripts")
                include("*.lua")
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
            }
            
            println("Example files copied to server!")
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