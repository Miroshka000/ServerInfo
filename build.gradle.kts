plugins {
    id("java-library")
    id("org.allaymc.gradle.plugin") version "0.1.2"
}

group = "org.allaymc.serverinfo"
version = "1.4.0"
description = "Show some information through scoreboard"

allay {
    api = "0.17.0"
    apiOnly = true
    server = null

    plugin {
        entrance = ".ServerInfo"
        apiVersion = ">=0.17.0"
        authors += "daoge_cmd"
    }
}

dependencies {
    compileOnly(group = "org.projectlombok", name = "lombok", version = "1.18.34")
    annotationProcessor(group = "org.projectlombok", name = "lombok", version = "1.18.34")
}