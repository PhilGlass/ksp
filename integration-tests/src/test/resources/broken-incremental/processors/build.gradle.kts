val kspVersion: String by project
val testRepo: String by project

plugins {
    kotlin("jvm")
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    maven(testRepo)
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap/")
}

dependencies {
    implementation(project(":annotations"))
    implementation("com.squareup:kotlinpoet-ksp:1.10.2")
    implementation("com.google.devtools.ksp:symbol-processing-api:$kspVersion")
}
