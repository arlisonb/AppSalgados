plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.ionasalgados.app"
    compileSdk = 34

    signingConfigs {
        create("release") {
            storeFile = file("iona-release.keystore")
            storePassword = "ionasalgados"
            keyAlias = "iona"
            keyPassword = "ionasalgados"
        }
    }

    defaultConfig {
        applicationId = "com.ionasalgados.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 15
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        val productionUrl = project.findProperty("PRODUCTION_SERVER_URL") as String?
            ?: "https://iona.meuappagenda.com.br"
        buildConfigField("String", "SOCKET_URL", "\"http://192.168.0.100:3000\"")
        buildConfigField("String", "PRODUCTION_SERVER_URL", "\"$productionUrl\"")
        buildConfigField("Boolean", "USE_PRODUCTION_SERVER", "false")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val productionUrl = project.findProperty("PRODUCTION_SERVER_URL") as String?
                ?: "https://iona.meuappagenda.com.br"
            buildConfigField("String", "SOCKET_URL", "\"$productionUrl\"")
            buildConfigField("Boolean", "USE_PRODUCTION_SERVER", "true")
        }
        debug {
            buildConfigField("Boolean", "USE_PRODUCTION_SERVER", "false")
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
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose + Material 3
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Retrofit + OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // WorkManager (desabilitado — app local sem tarefas em background)
    // implementation("androidx.work:work-runtime-ktx:2.9.0")
    // implementation("androidx.hilt:hilt-work:1.1.0")
    // ksp("androidx.hilt:hilt-compiler:1.1.0")

    // Lifecycle (serviço em primeiro plano)
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")

    // Firebase Cloud Messaging
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Socket.IO
    implementation("io.socket:socket.io-client:2.1.0") {
        exclude(group = "org.json", module = "json")
    }

    // ZXing (QR Code)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Coil (imagens)
    implementation("io.coil-kt:coil-compose:2.5.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
