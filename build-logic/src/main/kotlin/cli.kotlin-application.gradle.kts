plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    application
}

group = "io.github.boarnasia.cli"

kotlin {
    jvmToolchain(25)
}

// sqlite-jdbc がネイティブライブラリを読み込むため。指定しないと JDK 25 が毎回警告を出す
val nativeAccess = "--enable-native-access=ALL-UNNAMED"

application {
    applicationDefaultJvmArgs = listOf(nativeAccess)
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testImplementation(libs.findLibrary("kotest-assertions").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs(nativeAccess)
}
