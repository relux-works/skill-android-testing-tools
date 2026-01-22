plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.uitesttools.extract.MainKt")
}

dependencies {
    implementation(libs.clikt)
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.uitesttools.extract.MainKt"
    }

    // Create fat jar with all dependencies
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
