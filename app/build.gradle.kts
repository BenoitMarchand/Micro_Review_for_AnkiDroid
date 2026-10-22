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

android {
    namespace = "com.ankidroid.companion"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ankidroid.microreview"
        minSdk = 26
        targetSdk = 36
        versionCode = 38
        versionName = "0.4.0-rc1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        resValue("string", "issues_repository", issuesRepository)
    }

    buildTypes {
        release {
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
    implementation("com.github.ankidroid:Anki-Android:v2.17alpha8")
    implementation("androidx.work:work-runtime:2.10.5")

}