plugins {
    java
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "padej"
// Версия: <Minecraft>+<плагин>. Часть плагина берётся из -PpluginVersion (CI подставляет её из тега v*), иначе дефолт.
val mcVersion = "1.21.11"
val pluginVersion = (findProperty("pluginVersion") as String?)?.removePrefix("v")?.takeIf { it.isNotBlank() } ?: "3.1.0"
version = "$mcVersion+$pluginVersion"

repositories {
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    // Jumper: язык скриптов (.jmp), конфигов (.jmc) и политик доступа (.jma).
    // Без зависимостей, вшивается в JAR плагина. Версия - тег релиза
    // https://github.com/jumper-lang/jumper/releases (JitPack собирает :lang по jitpack.yml).
    implementation("com.github.jumper-lang:jumper:v0.11.2")
}

tasks {
    runServer {
        minecraftVersion("1.21")
    }

    jar {
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    // Локальный деплой на свой сервер: путь к папке plugins/ сервера берётся из
    //   -PserverPlugins=<путь>  или  gradle.properties (не коммитить)  или  переменной DISPLAYLIB_SERVER.
    // В обычный build не входит - CI собирает чистый jar.
    val serverPlugins: String? = (findProperty("serverPlugins") as String?) ?: System.getenv("DISPLAYLIB_SERVER")

    register("deployLocal") {
        group = "development"
        description = "Собрать и скопировать jar + примеры на локальный сервер (-PserverPlugins=<plugins dir>)"
        dependsOn("build")
        doLast {
            val target = serverPlugins ?: error("Укажите -PserverPlugins=<путь к plugins/ сервера> или DISPLAYLIB_SERVER")
            copy {
                from(layout.buildDirectory.dir("libs"))
                into(target)
                include("*-${version}.jar")
                exclude("*-sources.jar")
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
            }
            println("Jar скопирован в $target")
        }
        finalizedBy("copyExamples")
    }

    register("copyExamples") {
        group = "development"
        description = "Скопировать примеры экранов и скриптов на локальный сервер (-PserverPlugins=<plugins dir>)"
        doLast {
            val target = serverPlugins ?: error("Укажите -PserverPlugins=<путь к plugins/ сервера> или DISPLAYLIB_SERVER")
            val dir = file(target).resolve("DisplayLib")
            dir.resolve("screens").mkdirs()
            dir.resolve("scripts").mkdirs()
            copy { from("src/main/resources/examples/screens"); into(dir.resolve("screens")); include("**/*.jmc"); duplicatesStrategy = DuplicatesStrategy.INCLUDE }
            copy { from("src/main/resources/examples/scripts"); into(dir.resolve("scripts")); include("**/*.jmp"); duplicatesStrategy = DuplicatesStrategy.INCLUDE }
            println("Примеры скопированы в $dir")
        }
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    javadoc {
        options {
            this as StandardJavadocDocletOptions
            encoding = "UTF-8"
            windowTitle = "DisplayLib API"
            addStringOption("locale", "ru")
            links("https://docs.oracle.com/en/java/javase/21/docs/api/")
            links("https://jd.papermc.io/paper/1.21/")
            memberLevel = JavadocMemberLevel.PROTECTED
            
            // Настройки для русского языка
            addStringOption("charset", "UTF-8")
            addStringOption("docencoding", "UTF-8")
            
            // Дополнительные опции
            addBooleanOption("author", true)
            addBooleanOption("version", true)
            addBooleanOption("use", true)
            addBooleanOption("splitindex", true)
            addStringOption("doctitle", "DisplayLib API Documentation")
            addStringOption("header", "<b>DisplayLib v${version}</b>")
            addStringOption("footer", "<b>DisplayLib v${version}</b>")
            addStringOption("bottom", "Copyright © 2024 DisplayLib. Все права защищены.")
        }
        
        // Исключаем тестовые классы
        exclude("**/test_events/**")
        
        // Включаем только основные пакеты
        include("**/config/**")
        include("**/script/api/**")
        include("**/ui/**")
        include("**/DisplayLib.java")
        
        setDestinationDir(layout.buildDirectory.dir("docs/javadoc").get().asFile)
        
        doLast {
            println("Javadoc сгенерирован в: ${destinationDir}")
        }
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

// Personal, untracked build tasks (e.g. copying the JAR to your own test server).
// The file is optional and is not committed; see .git/info/exclude or .gitignore.
if (file("local.gradle.kts").exists()) {
    apply(from = "local.gradle.kts")
}
