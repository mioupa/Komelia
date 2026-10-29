import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose.compiler)
}

group = "io.github.snd-r.komelia"
version = libs.versions.app.version.get()


dependencies {
    implementation(projects.komeliaApp.shared)
    implementation(projects.komeliaUi)
    implementation(projects.komeliaDomain.core)
    implementation(projects.komeliaDomain.offline)
    implementation(projects.komeliaInfra.database.shared)
    implementation(projects.komeliaInfra.database.transaction)
    implementation(projects.komeliaInfra.webview)
    implementation(projects.komeliaInfra.database.sqlite)
    implementation(projects.komeliaInfra.onnxruntime.jvm)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.window)
    implementation(libs.androidx.workManager)
    implementation(libs.androidx.workManager.ktx)
    implementation(libs.kotlin.logging)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.filekit.core)
    implementation(libs.filekit.dialogs)

}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

enum class AndroidVariant {
    STANDALONE,
    FDROID,
    PLAY
}

val androidVariant = runCatching {
    AndroidVariant.valueOf(
        (project.extraProperties["snd.android.variant"] as String).uppercase()
    )
}.getOrDefault(AndroidVariant.STANDALONE)

// release signing config is read from gitignored keystore.properties in the root project dir or from env variables
val keystoreProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties"))
        .asText.orNull?.let { load(it.reader()) }
}

fun signingProperty(key: String, envVariable: String): String? =
    keystoreProperties.getProperty(key) ?: providers.environmentVariable(envVariable).orNull

android {
    namespace = "io.github.snd_r.komelia"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    val manifestFile = when (androidVariant) {
        AndroidVariant.STANDALONE -> "AndroidManifest.xml"
        AndroidVariant.FDROID -> "AndroidManifestFdroid.xml"
        AndroidVariant.PLAY -> "AndroidManifestPlay.xml"
    }
    sourceSets["main"].manifest.srcFile("src/main/$manifestFile")
    sourceSets["main"].res.srcDirs("src/main/res")

    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        applicationId = "io.github.snd_r.komelia"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = libs.versions.android.versionCode.get().toInt()
        versionName = libs.versions.app.version.get()
        versionNameSuffix = "-fork"

        val enableSelfUpdates = when (androidVariant) {
            AndroidVariant.STANDALONE -> "true"
            AndroidVariant.FDROID -> "false"
            AndroidVariant.PLAY -> "false"
        }
        buildConfigField("boolean", "ENABLE_SELF_UPDATES", enableSelfUpdates)
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,README.txt}"
            pickFirsts += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
    dependenciesInfo {
        if (androidVariant != AndroidVariant.PLAY) {
            includeInApk = false
            includeInBundle = false
        }
    }
    signingConfigs {
        val storeFilePath = signingProperty("storeFile", "KOMELIA_KEYSTORE_PATH")
        if (storeFilePath != null) {
            create("release") {
                storeFile = rootProject.file(storeFilePath)
                storePassword = signingProperty("storePassword", "KOMELIA_KEYSTORE_PASSWORD")
                keyAlias = signingProperty("keyAlias", "KOMELIA_KEY_ALIAS")
                keyPassword = signingProperty("keyPassword", "KOMELIA_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")

            // required by playstore. helps to reduce apk/bundle size
            // saves 185KB but mangles stacktraces
            val obfuscationRule = when (androidVariant) {
                AndroidVariant.STANDALONE, AndroidVariant.FDROID -> "dont_obfuscate.pro"
                AndroidVariant.PLAY -> null
            }

            setProguardFiles(
                listOfNotNull(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    obfuscationRule,
                    "android.pro",
                )
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
