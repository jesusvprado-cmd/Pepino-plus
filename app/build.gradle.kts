plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Número de compilación que pone GitHub (el mismo que el de la versión publicada): así la app sabe si hay una más nueva.
val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.suso.miiptv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.suso.pepinoplus"
        minSdk = 21
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.$buildNumber"

        // Teles, boxes y móviles usan procesadores ARM: así el APK pesa menos.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    // Firma de la app: la llave (pepino.p12) va cifrada y su contraseña NO está en el código; la pone GitHub
    // desde el secreto SIGNING_PASSWORD (o, en un ordenador, la propiedad -PsigningPassword=...).
    val signingPass: String? = System.getenv("SIGNING_PASSWORD")?.takeIf { it.isNotBlank() }
        ?: (project.findProperty("signingPassword") as String?)
    signingConfigs {
        create("propia") {
            storeFile = file("pepino.p12")
            storePassword = signingPass
            keyAlias = "pepino"
            keyPassword = signingPass
            storeType = "pkcs12"
        }
    }

    // Los logos hechos a mano (carpeta «logos») van dentro de la app, en assets/logos/.
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets", "build/generated/ownLogos")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (signingPass != null) signingConfigs.getByName("propia") else null
        }
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
    testOptions {
        // La prueba de logos lee los datos y los logos propios que van en assets.
        unitTests.isIncludeAndroidResources = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.8.0")
    // Decodificador de audio (AC3, E-AC3, DTS...) para los equipos que no lo traen.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.8.0+1")
    // VPN WireGuard (WARP y perfiles propios).
    implementation("com.wireguard.android:tunnel:1.0.20230706")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Fila «Continuar viendo» de la pantalla de inicio de Android TV.
    implementation("androidx.tvprovider:tvprovider:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Prueba automática de logos (se ejecuta en GitHub antes de publicar cada versión).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    // Capturas de pantalla de prueba (móvil, tablet y tele) para revisar el diseño sin el aparato.
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    // Servidor de prueba para las pruebas de lectura de listas (M3U y Xtream).
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Copia la carpeta «logos» del proyecto a assets/logos/ dentro del APK.
val copyOwnLogos by tasks.registering(Copy::class) {
    from(rootProject.file("logos"))
    into(file("build/generated/ownLogos/logos"))
}
tasks.named("preBuild") { dependsOn(copyOwnLogos) }
