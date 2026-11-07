plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
}

kotlin {
    jvmToolchain(
        libs.versions.jvmToolchain
            .get()
            .toInt(),
    )
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.components.resources)
        }

        jvmMain.dependencies {
            api(project(":jewel"))
            api("io.github.arieldaniely:seforim-open-siddur:0.1.0")
            implementation(compose.desktop.currentOs) {
                exclude(group = "org.jetbrains.compose.material")
            }
            implementation(libs.kosherkotlin)
            implementation(libs.zmanim)
            implementation(libs.kotlinx.datetime)
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
