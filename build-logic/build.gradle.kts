plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradle.api)
    implementation(libs.snakeyaml.engine)
    implementation(libs.commonmark.core)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.gfm.strikethrough)
    implementation(libs.commonmark.ext.autolink)
    implementation(libs.sqlite.jdbc)
    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        register("terlinguaContent") {
            id = "terlingua.content"
            implementationClass = "io.github.terlingua.build.TerlinguaContentPlugin"
        }
    }
}

tasks.test {
    val fixtures = layout.projectDirectory.dir("../app/src/test/resources/fixtures")
    inputs.dir(fixtures).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("terlingua.fixtures", fixtures.asFile.absolutePath)
}
