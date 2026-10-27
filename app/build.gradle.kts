import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.gpo.cronpaw"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gpo.cronpaw"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "0.4.2"
        // Notion OAuth goes through 51's relay Worker (notion-oauth-relay, app key "cronpaw"):
        // it holds the integration's client secret. Override with notion.oauth.relay in local.properties.
        val localProps = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        val relay = (localProps.getProperty("notion.oauth.relay")
            ?: "https://notion-oauth-relay.p2o51willam.workers.dev/cronpaw").trimEnd('/')
        buildConfigField("String", "NOTION_OAUTH_RELAY", "\"$relay\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "androidx.compose.animation.ExperimentalAnimationApi",
            "androidx.compose.animation.ExperimentalSharedTransitionApi",
            "kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

dependencies {
    implementation(project(":yoin-symbols"))

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.runtime)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.material3.expressive)
    implementation(libs.material3.adaptive)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.androidx.browser)


    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.palette)
    implementation(libs.graphics.shapes)
    implementation("androidx.glance:glance-appwidget:1.2.0")
    // Glance runs its sessions on WorkManager; pinned to the version already in the Gradle cache (offline builds).
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    testImplementation("junit:junit:4.13.2")
    constraints {
        implementation("androidx.room:room-runtime:2.8.4")
    }
}
