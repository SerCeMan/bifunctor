plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose)
}

group = "${providers.gradleProperty("pluginGroup").get()}.storybook"
version = providers.gradleProperty("pluginVersion").get()

// Configure project's dependencies
repositories {
    google()
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/kpm/public/")
    // for IJ icons
    maven("https://www.jetbrains.com/intellij-repository/releases")
    // for the testFramework
    // TODO: there should be a way to get the list from the plugin.
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/")
    maven("https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public/")
    maven("https://download.jetbrains.com/teamcity-repository/")
}

dependencies {
    implementation(project(":"))
    implementation(testFixtures(project(":")))

    implementation(libs.jewel.standalone)
    implementation(libs.jewel.decorated.window)
    // Ensures that the icons are on the classpath
    // see https://github.com/JetBrains/jewel?tab=readme-ov-file#loading-icons-from-the-intellij-platform
    implementation("com.jetbrains.intellij.platform:icons:243.23654.117")
    // for dependencies on mockvirtualfile, etc.
    implementation("com.jetbrains.intellij.platform:test-framework:243.23654.117")
    // Do not bring in Material (we use Jewel) and Coroutines (the IDE has its own)
    api(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
        exclude(group = "org.jetbrains.kotlinx")
    }
}
