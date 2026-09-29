plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseSigningEnvironmentNames = listOf(
    "WATTNOW_KEYSTORE",
    "WATTNOW_KEY_ALIAS",
    "WATTNOW_STORE_PASSWORD",
    "WATTNOW_KEY_PASSWORD"
)
val releaseBuildRequested = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true)
}
val releaseSigningEnvironment = if (releaseBuildRequested) {
    val values = releaseSigningEnvironmentNames.associateWith(System::getenv)
    val missing = values.filterValues { it.isNullOrBlank() }.keys
    if (missing.isNotEmpty()) {
        throw GradleException(
            "Release signing is missing required environment variables: ${missing.joinToString()}"
        )
    }
    values.mapValues { it.value.orEmpty() }
} else {
    emptyMap()
}

android {
    namespace = "com.jerry.wattnow"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jerry.wattnow"
        minSdk = 29
        targetSdk = 34
        versionCode = 8
        versionName = "1.2.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (releaseBuildRequested) {
            create("release") {
                val keystoreFile = file(releaseSigningEnvironment.getValue("WATTNOW_KEYSTORE"))
                if (!keystoreFile.isFile) {
                    throw GradleException(
                        "Release signing environment variable WATTNOW_KEYSTORE does not point to an existing file"
                    )
                }
                storeFile = keystoreFile
                keyAlias = releaseSigningEnvironment.getValue("WATTNOW_KEY_ALIAS")
                storePassword = releaseSigningEnvironment.getValue("WATTNOW_STORE_PASSWORD")
                keyPassword = releaseSigningEnvironment.getValue("WATTNOW_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseBuildRequested) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.04.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
