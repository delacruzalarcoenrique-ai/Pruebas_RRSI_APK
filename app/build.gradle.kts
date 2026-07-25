plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "pe.win.rssi"
    compileSdk = 34

    defaultConfig {
        applicationId = "pe.win.rssi"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    /*
     * Clave de firma fija del repo. Sin esto, cada compilación en GitHub Actions
     * genera una debug.keystore nueva y Android rechaza instalar la actualización
     * sobre la app ya instalada ("firma distinta"). Con una clave estable, el
     * técnico actualiza encima sin desinstalar.
     */
    signingConfigs {
        getByName("debug") {
            storeFile = file("winet-debug.keystore")
            storePassword = "winetsenal"
            keyAlias = "winet"
            keyPassword = "winetsenal"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Sin dependencias: WebView + Activity + WifiManager son de la plataforma.
}

/*
 * Copia web/ del repo dentro de los assets del APK.
 * Así la app trae una copia local de la página y sigue midiendo la señal aunque
 * el sitio del cliente no tenga internet (que es justo cuando va el técnico).
 * Fuente única: se edita solo en web/, aquí se copia en cada build.
 */
val copiarWeb = tasks.register<Copy>("copiarWeb") {
    from(rootProject.file("web")) {
        include("*.html", "*.js")
        exclude("*.test.js")
    }
    into(layout.projectDirectory.dir("src/main/assets"))
}

tasks.named("preBuild") { dependsOn(copiarWeb) }
