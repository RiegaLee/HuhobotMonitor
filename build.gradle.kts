import java.util.zip.ZipFile

plugins {
    java
}

group = "org.huhobot.addons"
version = "0.2.0"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

fun dependencyJar(propertyName: String, environmentName: String, candidates: List<File>): File {
    val configured = providers.gradleProperty(propertyName)
        .orElse(providers.environmentVariable(environmentName))
        .orNull
        ?.trim()
        .orEmpty()
    if (configured.isNotEmpty()) return file(configured)
    return candidates.firstOrNull { it.isFile } ?: candidates.first()
}

// Compile against the current upstream HuHoBot mainline Addon/QClient ABI.
// The host provides these classes at runtime and they must not be bundled in this addon.
val huhobotQqSdkJar = dependencyJar(
    "huhobotQqSdkJar",
    "HUHOBOT_QQ_SDK_JAR",
    listOf(file("../PenguinClient-Main/common/Bot/build/libs/common-Bot-1.5.0.jar"))
)

dependencies {
    compileOnly(files(huhobotQqSdkJar))
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.2.20")
    compileOnly("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")

    testImplementation(files(huhobotQqSdkJar))
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.20")
    testImplementation("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(8)
}

tasks.register<JavaExec>("renderPreview") {
    group = "verification"
    description = "Render the default HuHoBot performance dashboard preview."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.huhobot.monitor.preview.PerformancePreview")
    systemProperty("java.awt.headless", "true")
    args(layout.buildDirectory.file("preview/performance-preview-v2.png").get().asFile.absolutePath)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    exclude("performance/**")
    val values = mapOf("version" to project.version)
    inputs.properties(values)
    filesMatching("plugin.yml") {
        expand(values)
    }
}

tasks.jar {
    archiveFileName.set("HuhobotMonitor-${project.version}.jar")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
}

val verifyAddonJar by tasks.registering {
    group = "verification"
    description = "Checks that the addon is packaged without HuHoBot host implementation classes."
    dependsOn(tasks.jar)

    doLast {
        check(huhobotQqSdkJar.isFile) {
            "Build PenguinClient-Main common:Bot first; expected ${huhobotQqSdkJar.absolutePath}"
        }
        val jarFile = tasks.jar.get().archiveFile.get().asFile
        ZipFile(jarFile).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()
            check("plugin.yml" in entries) { "Addon JAR is missing plugin.yml" }
            check("org/huhobot/monitor/HuhobotMonitorPlugin.class" in entries) {
                "Addon JAR is missing the HuhobotMonitor entry point"
            }
            val classes = entries.filter { it.endsWith(".class") }
            check(classes.all { it.startsWith("org/huhobot/monitor/") }) {
                "Addon JAR must contain only HuhobotMonitor implementation classes"
            }
            val descriptor = zip.getInputStream(zip.getEntry("plugin.yml")).bufferedReader().use { it.readText() }
            check("HuhobotMonitor Contributors" in descriptor) {
                "Addon metadata must use the public contributor identity"
            }
        }
    }
}

tasks.build {
    dependsOn(verifyAddonJar)
}
