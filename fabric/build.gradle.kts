plugins {
    id("fabric-loom")
    id("com.gradleup.shadow")
}

loom {
    accessWidenerPath = file("src/main/resources/knockbacksync.accesswidener")
}

base {
    archivesName.set("${rootProject.property("archives_base_name")}-fabric")
}

// TODO migrate to only including sourceset for compile, test and javadoc tasks
// Currently must build with gradle build -x test to skip test
tasks.withType<JavaCompile>().configureEach {
    source(project(":common").sourceSets.main.get().allSource)
}

tasks.withType<Javadoc>().configureEach {
    source(project(":common").sourceSets.main.get().allJava)
}

tasks.named<JavaCompile>("compileTestJava") {
    exclude("**/*")
}

repositories {
    maven("https://jitpack.io") // Conditional Mixin
}

dependencies {
    implementation(project(":common"))

    minecraft("com.mojang:minecraft:${rootProject.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${rootProject.property("yarn_mappings")}")
    modImplementation("net.fabricmc:fabric-loader:${rootProject.property("loader_version")}")
    modImplementation(fabricApi.module("fabric-lifecycle-events-v1", "${rootProject.property("fabric_version")}"))
    modImplementation(fabricApi.module("fabric-events-interaction-v0", "${rootProject.property("fabric_version")}"))

    include(modImplementation("me.lucko:fabric-permissions-api:0.3.1")!!)
    include(modImplementation("com.github.retrooper:packetevents-fabric:2.9.5-SNAPSHOT")!!)
    include(modImplementation("org.incendo:cloud-fabric:2.0.0-beta.10")!!)

    include(implementation("org.incendo:cloud-minecraft-extras:2.0.0-beta.10")!!)
    include(implementation("org.yaml:snakeyaml:2.0")!!)
    include(implementation("org.kohsuke:github-api:1.326")!!)
    // Required for org.kohsuke.github
    include(implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")!!)
    include(implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.2")!!)
    include(implementation("com.fasterxml.jackson.core:jackson-core:2.17.2")!!)
    //    Not requires in modern Minecraft. May be needed if fabric version is backported to older versions
    //    include(implementation("org.apache.commons:commons-lang3:3.17.0")!!)
    //    include(implementation("commons-io:commons-io:2.16.1")!!)

    compileOnly("org.geysermc.floodgate:api:2.0-SNAPSHOT")
    compileOnly("org.projectlombok:lombok:1.18.34")
    compileOnly("io.netty:netty-all:4.1.72.Final")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
}

tasks.processResources {
    from(project(":common").sourceSets.main.get().resources)

    inputs.property("version", project.version)
    inputs.property("minecraft_version", rootProject.property("minecraft_version"))
    inputs.property("loader_version", rootProject.property("loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to rootProject.property("minecraft_version"),
            "loader_version" to rootProject.property("loader_version")
        )
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}