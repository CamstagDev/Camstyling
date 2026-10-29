import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// La versión vive en gradle.properties (camstyling.version=X.Y.Z).
// versionCode sale de ella: 1.2.3 -> 10203, así cada versión es mayor que la anterior.
val versionDeLaApp = providers.gradleProperty("camstyling.version").get()
val (mayor, menor, parche) = versionDeLaApp.split(".").map { it.toInt() }

// La llave de firma NO está en git: keystore.properties y el .jks viven solo
// en esta PC (ver README, "Firma"). Sin ellos, la versión release no se puede
// firmar y solo se compila la debug.
val propiedadesFirma = Properties().apply {
    val archivo = rootProject.file("keystore.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}

android {
    namespace = "com.camstagdev.camstyling"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.camstagdev.camstyling"
        minSdk = 29
        targetSdk = 36
        versionCode = mayor * 10000 + menor * 100 + parche
        versionName = versionDeLaApp
    }

    signingConfigs {
        if (propiedadesFirma.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(propiedadesFirma.getProperty("storeFile"))
                storePassword = propiedadesFirma.getProperty("storePassword")
                keyAlias = propiedadesFirma.getProperty("keyAlias")
                keyPassword = propiedadesFirma.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
}
