import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

// Version comes from the single source, <repo>/version.properties (also read by iOS and web).
// -PkmpVersionCode overrides the code, e.g. for a CI run number.
val versionProps = Properties().apply {
    load(
        StringReader(
            providers.fileContents(rootProject.layout.projectDirectory.file("version.properties")).asText.get()
        )
    )
}
val appVersionName: String = requireNotNull(versionProps.getProperty("VERSION_NAME")?.trim()?.takeIf { it.isNotEmpty() }) {
    "VERSION_NAME missing in version.properties"
}
val appVersionCode: Int = run {
    val raw = providers.gradleProperty("kmpVersionCode").orNull ?: versionProps.getProperty("VERSION_CODE")
    raw?.trim()?.toIntOrNull()
        ?: error("VERSION_CODE must be an integer (version.properties or -PkmpVersionCode)")
}

// Optional backend for the dev flavor only: -PkmpBackendUrl=http://10.0.2.2:8081/api/v1/
// Empty keeps the in-app demo backend. stage and prod never read it.
val backendUrl: String = providers.gradleProperty("kmpBackendUrl").orElse("").get().trim()
require(backendUrl.isEmpty() || Regex("^https?://[^\\s\"\\\\$]+$").matches(backendUrl)) {
    "kmpBackendUrl must start with http:// or https:// and contain no spaces, quotes, backslashes or '$'"
}

// Firebase config files are not in the repo (docs/crash-reporting.md). A flavor without its
// google-services.json warns instead of failing, so builds without the files (CI, outside
// contributors) stay green and the SDK stays inert: AC-19.
googleServices {
    missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.koin.android)

    // Crashlytics SDK. Without a google-services.json no FirebaseApp exists and the SDK stays
    // inert, so crash reporting falls back to the no-op reporter.
    implementation(libs.firebase.crashlytics)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.anksoft.myapplication"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.anksoft.myapplication"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName
    }
    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            isDefault = true
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "MyApplication Dev")
            buildConfigField("String", "ENVIRONMENT", "\"DEV\"")
            buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
            buildConfigField("boolean", "DEMO_ALLOWED", "true")
        }
        create("stage") {
            dimension = "env"
            applicationIdSuffix = ".stage"
            resValue("string", "app_name", "MyApplication Stage")
            buildConfigField("String", "ENVIRONMENT", "\"STAGE\"")
            buildConfigField("String", "BACKEND_URL", "\"\"")
            buildConfigField("boolean", "DEMO_ALLOWED", "false")
        }
        create("prod") {
            dimension = "env"
            resValue("string", "app_name", "MyApplication")
            buildConfigField("String", "ENVIRONMENT", "\"PROD\"")
            buildConfigField("String", "BACKEND_URL", "\"\"")
            buildConfigField("boolean", "DEMO_ALLOWED", "false")
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}