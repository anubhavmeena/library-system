plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.targetzone.library"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.targetzone.library"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // BASE_URL (ApiClient.kt) is hardcoded to the production API — this app
        // has no sandbox backend to talk to, so the Cashfree SDK must always be
        // in PRODUCTION mode to match. Previously defaulted to "sandbox" when
        // CASHFREE_ENV wasn't set in the build shell (no CI script ever set it
        // for Android, unlike the web frontend/other backends), which caused a
        // sandbox-mode SDK session to reject a production-mode payment_session_id
        // from the backend with "token is not present".
        buildConfigField("String", "CASHFREE_ENV", "\"${System.getenv("CASHFREE_ENV") ?: "production"}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.razorpay.checkout)
    implementation(libs.cashfree.pg)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.ucrop)
    implementation(libs.zxing.core)
    debugImplementation(libs.androidx.ui.tooling)
}
