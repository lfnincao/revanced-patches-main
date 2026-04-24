group = "app.revanced"

patches {
    about {
        name = "ReVanced Patches"
        description = "Patches for ReVanced"
        source = "git@github.com:revanced/revanced-patches.git"
        author = "ReVanced"
        contact = "patches@revanced.app"
        website = "https://revanced.app"
        license = "GNU General Public License v3.0"
    }
}

dependencies {
    // Required due to smali, or build fails. Can be removed once smali is bumped.
    implementation(libs.guava)

    implementation(libs.apksig)

    // Android API stubs defined here.
    compileOnly(project(":patches:stub"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexplicit-backing-fields",
            "-Xcontext-parameters"
        )
    }
}

publishing {
    repositories {
        maven {
            name = "githubPackages"
            url = uri("https://maven.pkg.github.com/${System.getenv("GITHUB_REPOSITORY") ?: "revanced/revanced-patches"}")
            credentials(PasswordCredentials::class)
        }
    }
}

// Personal fork: skip GPG signing of the Maven publication.
tasks.withType<org.gradle.plugins.signing.Sign>().configureEach {
    enabled = false
}

apply(from = "strings-processing.gradle.kts")
