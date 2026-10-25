plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// GitHub repository (owner/name) that "Report an issue" opens, kept out of the sources: the repository running the
// CI build, or the one of the local `origin` remote. Empty (the entry is hidden) when neither is known.
val issuesRepository: String = providers.environmentVariable("GITHUB_REPOSITORY").orNull ?: runCatching {
    val origin = providers.exec {
        commandLine("git", "remote", "get-url", "origin")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
    Regex("""github\.com[:/]([^/]+/[^/]+?)(\.git)?$""").find(origin)?.groupValues?.get(1)
}.getOrNull().orEmpty()

// Upload key that signs the release build for the Play Store (Play App Signing), never in the repository: Gradle
// properties, set locally in ~/.gradle/gradle.properties and in CI through ORG_GRADLE_PROJECT_* variables. Without
// them, the release build is unsigned.
val uploadStoreFile: String? = providers.gradleProperty("microreviewUploadStoreFile").orNull
val uploadStorePassword: String? = providers.gradleProperty("microreviewUploadStorePassword").orNull

android {
    namespace = "com.orlikalabs.microreview"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.orlikalabs.microreview"
        minSdk = 26
        targetSdk = 36
        versionCode = 51
        versionName = "0.4.0-rc1.14"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        resValue("string", "issues_repository", issuesRepository)
    }

    signingConfigs {
        if (uploadStoreFile != null && uploadStorePassword != null) {
            create("upload") {
                storeFile = file(uploadStoreFile)
                storePassword = uploadStorePassword
                keyAlias = providers.gradleProperty("microreviewUploadKeyAlias").getOrElse("upload")
                keyPassword = providers.gradleProperty("microreviewUploadKeyPassword").getOrElse(uploadStorePassword)
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("upload")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        resValues = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// The project's LICENSE, shown in Settings › Help › Open-source licenses: copied into the app's assets, so the
// copyright lines stay in LICENSE only.
abstract class CopyLicense : DefaultTask() {
    @get:InputFile
    abstract val license: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        license.get().asFile.copyTo(outputDir.file("LICENSE.txt").get().asFile, overwrite = true)
    }
}

val copyLicense = tasks.register<CopyLicense>("copyLicense") {
    license.set(rootProject.layout.projectDirectory.file("LICENSE"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyLicense, CopyLicense::outputDir)
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.09.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // AnkiDroid's API library (JitPack): only the store assets setup uses it, to create the demo deck. The app
    // talks to AnkiDroid's ContentProvider directly (AnkiDroidContract).
    androidTestImplementation("com.github.ankidroid:Anki-Android:v2.17alpha8")
    implementation("androidx.work:work-runtime:2.10.5")

}