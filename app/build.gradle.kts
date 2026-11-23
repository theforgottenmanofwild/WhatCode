plugins {
    id("com.android.application")
}

// CI passes the release key through environment variables; nothing secret is stored in the repo.
val releaseKeystore: String? = providers.environmentVariable("KEYSTORE").orNull?.takeIf { it.isNotBlank() }

android {
    namespace = "com.whatcodeeditor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.whatcodeeditor"
        minSdk = 26
        targetSdk = 35
        // Override with -PversionName=1.2.3 -PversionCode=42
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?)
            ?: rootProject.file("VERSION").readText().trim()
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Without a release key, sign with the debug key so the APK is still installable.
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
