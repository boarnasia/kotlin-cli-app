plugins {
    id("cli.kotlin-application")
}

application {
    applicationName = "core-cli"
    mainClass = "io.github.boarnasia.cli.MainKt"
}

dependencies {
    implementation(libs.core.db)
    implementation(libs.core.user)
    implementation(libs.core.staff)
    implementation(libs.clikt)
    implementation(libs.kotlinx.serialization.json)
    // Flyway と jOOQ のログが標準出力の --json に混ざらないよう、ログを捨てる
    runtimeOnly(libs.slf4j.nop)
}
