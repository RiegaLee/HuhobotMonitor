plugins {
    java
}

group = "cn.huohuas001.huhobot"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(8)
}

tasks.register<JavaExec>("renderPreview") {
    group = "verification"
    description = "Render the first HuHoBot performance dashboard preview."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("cn.huohuas001.huhobot.performance.preview.PerformancePreview")
    systemProperty("java.awt.headless", "true")
    args(layout.buildDirectory.file("preview/performance-preview-v1.png").get().asFile.absolutePath)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    // The runtime-core renderer is fully Java2D; the early OnlineList slice experiments
    // remain design references only and must not inflate the released Addon JAR.
    exclude("performance/**")
    val values = mapOf("version" to project.version)
    inputs.properties(values)
    filesMatching("plugin.yml") {
        expand(values)
    }
}

tasks.jar {
    archiveBaseName.set("HuHoBotPerformance")
    // Compile-only ABI stubs; HuHoBot provides the real classes at runtime.
    exclude("cn/huohuas001/bot/**")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
}
