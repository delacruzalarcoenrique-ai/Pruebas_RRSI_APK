# Captura de RSSI vía mini-app Android — Plan de Implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir una mini-app Android (WebView) que carga la página de speedtest de W-NET en Vercel y le inyecta el RSSI real (dBm) + banda vía un puente JS, más un snippet web que lo clasifica según la escala oficial de W-NET; el APK se compila en GitHub Actions.

**Architecture:** Dos unidades con interfaz clara. (1) App Android nativa (Kotlin, sin AndroidX) que lee `WifiManager` y expone `window.WinetWifi`. (2) Snippet `winet-rssi.js` que consume ese puente, clasifica el RSSI y lo entrega a la UI vía evento `winet:rssi` + API `window.getWifiSignal()`. La compilación es en la nube (GitHub Actions), sin herramientas locales.

**Tech Stack:** Kotlin, Android SDK (compileSdk 34, minSdk 26), Gradle Kotlin DSL, JavaScript puro (sin dependencias), GitHub Actions.

## Global Constraints

- `applicationId` / `namespace`: `pe.win.rssi`
- Nombre visible de la app: `W-NET Señal`
- Nombre del puente JS: `WinetWifi` (objeto global inyectado en la web)
- minSdk = 26, targetSdk = 34, compileSdk = 34
- Sin AndroidX (usa `android.app.Activity` + tema de plataforma) → build mínimo
- Todo el repositorio debe ser texto (sin PNG/JAR binarios): íconos vectoriales; CI provee Gradle
- Clasificación de calidad OFICIAL W-NET (copiar textual):
  - `rssi >= -50` → `optima` / "Óptima" / `#0F7B0F` (verde oscuro)
  - `-60 <= rssi < -50` → `buena` / "Buena" / `#7CB518` (verde limón)
  - `-77 <= rssi < -60` → `baja` / "Baja" / `#E6B800` (amarillo)
  - `-84 <= rssi < -77` → `debil` / "Débil" / `#E8720C` (naranja)
  - `rssi < -84` → `fuera` / "Fuera de cobertura" / `#D32F2F` (rojo)
  - sin datos → `na` / "Sin datos" / `#9E9E9E`
- Puente JS `WinetWifi.getInfoJson()` devuelve exactamente:
  `{"rssi":<int>,"frequencyMhz":<int>,"band":"<2.4 GHz|5 GHz|6 GHz|>","available":<bool>}`

---

## File Structure

```
winet-wifi-rssi/
├─ .github/workflows/build-apk.yml     # Tarea 7: compila APK en la nube
├─ settings.gradle.kts                 # Tarea 1
├─ build.gradle.kts                    # Tarea 1 (raíz)
├─ gradle.properties                   # Tarea 1
├─ app/
│  ├─ build.gradle.kts                 # Tarea 1
│  └─ src/main/
│     ├─ AndroidManifest.xml           # Tarea 2
│     ├─ java/pe/win/rssi/MainActivity.kt  # Tarea 4
│     └─ res/
│        ├─ values/strings.xml         # Tarea 2
│        ├─ drawable/ic_launcher_background.xml   # Tarea 3
│        ├─ drawable/ic_launcher_foreground.xml   # Tarea 3
│        └─ mipmap-anydpi-v26/ic_launcher.xml, ic_launcher_round.xml  # Tarea 3
├─ web/
│  ├─ winet-rssi.js                    # Tarea 5 (snippet + API)
│  ├─ winet-rssi.test.js               # Tarea 5 (prueba Node de clasificación)
│  └─ ejemplo.html                     # Tarea 6 (demo de integración)
└─ README.md                           # Tarea 8
```

---

### Task 1: Andamiaje Gradle (configuración de build)

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`

**Interfaces:**
- Consumes: nada.
- Produces: proyecto Gradle con módulo `:app`, `namespace = "pe.win.rssi"`, Kotlin/AGP configurados. Las tareas 2–4 dependen de esta estructura; la tarea 7 ejecuta `gradle assembleDebug` sobre ella.

- [ ] **Step 1: Crear `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WinetSenal"
include(":app")
```

- [ ] **Step 2: Crear `build.gradle.kts` (raíz)**

```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
```

- [ ] **Step 3: Crear `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=false
kotlin.code.style=official
```

- [ ] **Step 4: Crear `app/build.gradle.kts`**

```kotlin
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
        versionCode = 1
        versionName = "1.0"
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
```

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties app/build.gradle.kts
git commit -m "build: andamiaje Gradle del módulo :app (Kotlin, sin AndroidX)"
```

> **Verificación:** este andamiaje se valida en la Tarea 7 (compilación en CI). No se compila localmente porque el PC no tiene Android SDK.

---

### Task 2: AndroidManifest y strings

**Files:**
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: estructura Gradle de la Tarea 1.
- Produces: declaración de la actividad `.MainActivity` (creada en Tarea 4), permisos WiFi/ubicación/internet, `@string/app_name` = "W-NET Señal", referencia al ícono `@mipmap/ic_launcher` (creado en Tarea 3).

- [ ] **Step 1: Crear `app/src/main/res/values/strings.xml`**

```xml
<resources>
    <string name="app_name">W-NET Señal</string>
</resources>
```

- [ ] **Step 2: Crear `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:usesCleartextTraffic="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|keyboardHidden">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml
git commit -m "feat: manifest con permisos WiFi/ubicación y nombre de app"
```

---

### Task 3: Íconos vectoriales adaptativos

**Files:**
- Create: `app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

**Interfaces:**
- Consumes: nada externo.
- Produces: recursos `@mipmap/ic_launcher` y `@mipmap/ic_launcher_round` referenciados por el Manifest (Tarea 2). Solo válidos en API 26+ (por eso minSdk 26).

- [ ] **Step 1: Crear `app/src/main/res/drawable/ic_launcher_background.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#0F7B0F" />
</shape>
```

- [ ] **Step 2: Crear `app/src/main/res/drawable/ic_launcher_foreground.xml`** (tres barras de señal blancas)

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#FFFFFF" android:pathData="M40,64 h9 v8 h-9 z" />
    <path android:fillColor="#FFFFFF" android:pathData="M53,54 h9 v18 h-9 z" />
    <path android:fillColor="#FFFFFF" android:pathData="M66,42 h9 v30 h-9 z" />
</vector>
```

- [ ] **Step 3: Crear `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

- [ ] **Step 4: Crear `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`** (mismo contenido)

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/drawable app/src/main/res/mipmap-anydpi-v26
git commit -m "feat: ícono vectorial adaptativo (barras de señal, verde W-NET)"
```

---

### Task 4: MainActivity + puente WinetWifi

**Files:**
- Create: `app/src/main/java/pe/win/rssi/MainActivity.kt`

**Interfaces:**
- Consumes: Manifest (Tarea 2) que declara `.MainActivity` y los permisos.
- Produces: objeto JS `window.WinetWifi` con `getInfoJson(): String`, `getRssi(): Int`, `getBand(): String`. Contrato de `getInfoJson()`: ver Global Constraints. La Tarea 5 (winet-rssi.js) consume este objeto.

- [ ] **Step 1: Crear `app/src/main/java/pe/win/rssi/MainActivity.kt`**

```kotlin
package pe.win.rssi

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView

class MainActivity : Activity() {

    // ┌───────────────────────────────────────────────────────────────┐
    // │  CONFIGURA AQUÍ la URL de tu página de speedtest en Vercel:    │
    // └───────────────────────────────────────────────────────────────┘
    private val webUrl = "https://TU-PROYECTO.vercel.app"

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android exige permiso de ubicación para leer datos del WiFi conectado.
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(WinetWifi(this), "WinetWifi")
        setContentView(webView)
        webView.loadUrl(webUrl)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}

/** Puente JS expuesto a la web como window.WinetWifi. */
class WinetWifi(private val ctx: Context) {

    @Suppress("DEPRECATION")
    private fun connectionInfo() =
        (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo

    private fun freq(): Int = try { connectionInfo().frequency } catch (e: Exception) { 0 }

    private fun bandFromFreq(mhz: Int): String = when {
        mhz >= 5925 -> "6 GHz"
        mhz >= 4900 -> "5 GHz"
        mhz > 0 -> "2.4 GHz"
        else -> ""
    }

    @JavascriptInterface
    fun getRssi(): Int = try { connectionInfo().rssi } catch (e: Exception) { 0 }

    @JavascriptInterface
    fun getBand(): String = bandFromFreq(freq())

    @JavascriptInterface
    fun getInfoJson(): String {
        val rssi = getRssi()
        val f = freq()
        val band = bandFromFreq(f)
        val available = f > 0            // hay WiFi conectado si la frecuencia es > 0
        return "{\"rssi\":$rssi,\"frequencyMhz\":$f,\"band\":\"$band\",\"available\":$available}"
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/pe/win/rssi/MainActivity.kt
git commit -m "feat: WebView + puente WinetWifi que expone RSSI y banda"
```

> **Verificación:** compilación en CI (Tarea 7) + prueba manual en un Android real
> conectado a WiFi (ver README): el valor de `getInfoJson()` debe reflejar el dBm real.

---

### Task 5: Snippet `winet-rssi.js` (con prueba TDD de clasificación)

**Files:**
- Create: `web/winet-rssi.test.js`
- Create: `web/winet-rssi.js`

**Interfaces:**
- Consumes: `window.WinetWifi.getInfoJson()` (Tarea 4) cuando corre dentro de la app.
- Produces:
  - `window.WinetRSSI` = `{ classify, getWifiSignal, start, stop, mount, hasBridge }`
  - `window.getWifiSignal()` → `{ available, rssi, band, quality, label, color }`
  - Evento `document` `'winet:rssi'` con `detail` igual a `getWifiSignal()`
  - En Node: `module.exports` = misma API (para pruebas)
- `classify(rssi)` → `{ quality, label, color }` según Global Constraints.

- [ ] **Step 1: Escribir la prueba que falla — `web/winet-rssi.test.js`**

```javascript
const assert = require('assert');
const { classify } = require('./winet-rssi.js');

const casos = [
  [-24, 'optima'], [-50, 'optima'],
  [-51, 'buena'],  [-60, 'buena'],
  [-61, 'baja'],   [-77, 'baja'],
  [-78, 'debil'],  [-84, 'debil'],
  [-85, 'fuera'],  [-92, 'fuera'],
  [null, 'na'],    [0, 'na'],
];

for (const [rssi, esperado] of casos) {
  const q = classify(rssi).quality;
  assert.strictEqual(q, esperado, `classify(${rssi}) => ${q}, esperado ${esperado}`);
}
console.log('OK: ' + casos.length + ' umbrales de clasificación pasaron');
```

- [ ] **Step 2: Ejecutar la prueba y verificar que falla**

Run: `node web/winet-rssi.test.js`
Expected: FAIL — `Cannot find module './winet-rssi.js'` (aún no existe).

> Si `node` no está instalado, esta prueba puede ejecutarse igualmente en el navegador
> abriendo `ejemplo.html` (Tarea 6), que corre las mismas aserciones en consola.

- [ ] **Step 3: Implementar `web/winet-rssi.js`**

```javascript
/*!
 * winet-rssi.js — Captura y clasificación de RSSI para W-NET TELECOM.
 * Funciona SOLO dentro de la app "W-NET Señal" (puente window.WinetWifi).
 * En un navegador normal reporta { available:false } y no rompe el speedtest.
 */
(function (global) {
  'use strict';

  var BRIDGE = 'WinetWifi';

  // Clasificación OFICIAL W-NET (dBm)
  function classify(rssi) {
    if (rssi == null || rssi === 0 || rssi <= -127) {
      return { quality: 'na', label: 'Sin datos', color: '#9E9E9E' };
    }
    if (rssi >= -50) return { quality: 'optima', label: 'Óptima', color: '#0F7B0F' };
    if (rssi >= -60) return { quality: 'buena',  label: 'Buena',  color: '#7CB518' };
    if (rssi >= -77) return { quality: 'baja',   label: 'Baja',   color: '#E6B800' };
    if (rssi >= -84) return { quality: 'debil',  label: 'Débil',  color: '#E8720C' };
    return { quality: 'fuera', label: 'Fuera de cobertura', color: '#D32F2F' };
  }

  function hasBridge() {
    return typeof global[BRIDGE] !== 'undefined' && !!global[BRIDGE];
  }

  function readNative() {
    if (!hasBridge()) return { available: false, rssi: null, band: '', frequencyMhz: 0 };
    try {
      var d = JSON.parse(global[BRIDGE].getInfoJson());
      return {
        available: !!d.available,
        rssi: d.rssi,
        band: d.band || '',
        frequencyMhz: d.frequencyMhz || 0
      };
    } catch (e) {
      return { available: false, rssi: null, band: '', frequencyMhz: 0 };
    }
  }

  function getWifiSignal() {
    var info = readNative();
    var q = classify(info.available ? info.rssi : null);
    return {
      available: info.available,
      rssi: info.available ? info.rssi : null,
      band: info.band,
      frequencyMhz: info.frequencyMhz,
      quality: q.quality,
      label: q.label,
      color: q.color
    };
  }

  var timer = null;
  var widgetEl = null;

  function dispatch(signal) {
    var ev;
    try {
      ev = new CustomEvent('winet:rssi', { detail: signal });
    } catch (e) {
      ev = document.createEvent('CustomEvent');
      ev.initCustomEvent('winet:rssi', false, false, signal);
    }
    document.dispatchEvent(ev);
  }

  function tick() {
    var s = getWifiSignal();
    dispatch(s);
    if (widgetEl) renderWidget(widgetEl, s);
  }

  function start(intervalMs) {
    stop();
    tick();
    timer = setInterval(tick, intervalMs || 1000);
  }

  function stop() {
    if (timer) { clearInterval(timer); timer = null; }
  }

  function mount(selector) {
    widgetEl = typeof selector === 'string' ? document.querySelector(selector) : selector;
    if (widgetEl) renderWidget(widgetEl, getWifiSignal());
    return widgetEl;
  }

  function renderWidget(el, s) {
    if (!s.available) {
      el.innerHTML =
        '<div style="font:14px sans-serif;padding:12px;border-radius:10px;' +
        'background:#f2f2f2;color:#555;text-align:center">' +
        '📶 Abre desde la app <b>W-NET Señal</b> para medir la señal</div>';
      return;
    }
    el.innerHTML =
      '<div style="font-family:sans-serif;padding:14px;border-radius:12px;color:#fff;' +
      'text-align:center;background:' + s.color + '">' +
        '<div style="font-size:40px;font-weight:700;line-height:1">' +
          s.rssi + ' <span style="font-size:16px">dBm</span></div>' +
        '<div style="font-size:16px;margin-top:4px">' + s.label + '</div>' +
        (s.band ? '<div style="font-size:12px;opacity:.9;margin-top:2px">' + s.band + '</div>' : '') +
      '</div>';
  }

  var api = {
    classify: classify,
    getWifiSignal: getWifiSignal,
    hasBridge: hasBridge,
    start: start,
    stop: stop,
    mount: mount
  };

  global.WinetRSSI = api;
  global.getWifiSignal = getWifiSignal;

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = api; // pruebas en Node
  } else if (typeof document !== 'undefined') {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', function () { start(1000); });
    } else {
      start(1000);
    }
  }
})(typeof window !== 'undefined' ? window : this);
```

- [ ] **Step 4: Ejecutar la prueba y verificar que pasa**

Run: `node web/winet-rssi.test.js`
Expected: PASS — `OK: 12 umbrales de clasificación pasaron`

- [ ] **Step 5: Commit**

```bash
git add web/winet-rssi.js web/winet-rssi.test.js
git commit -m "feat: snippet winet-rssi.js con clasificación W-NET (test verde)"
```

---

### Task 6: Demo de integración `ejemplo.html`

**Files:**
- Create: `web/ejemplo.html`

**Interfaces:**
- Consumes: `web/winet-rssi.js` (Tarea 5) — `WinetRSSI.mount`, evento `winet:rssi`,
  `WinetRSSI.classify` (para las aserciones en navegador).
- Produces: página de demostración; muestra cómo enganchar el evento a una UI propia
  y corre las mismas aserciones de clasificación en consola (respaldo si no hay Node).

- [ ] **Step 1: Crear `web/ejemplo.html`**

```html
<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>W-NET Señal — Demo de integración</title>
</head>
<body style="font-family:sans-serif;max-width:420px;margin:24px auto;padding:0 16px">
  <h2>Demo — captura de RSSI</h2>

  <!-- Opción A: widget automático -->
  <div id="senal"></div>

  <!-- Opción B: engancha el evento a tu propia UI -->
  <p style="margin-top:20px">Lectura manual (tu UI):
     <b id="mio">—</b></p>

  <hr>
  <p style="color:#888;font-size:13px">Abre esta página dentro de la app
     <b>W-NET Señal</b> para ver el dBm real. En un navegador normal indicará
     que debe abrirse desde la app.</p>

  <script src="winet-rssi.js"></script>
  <script>
    // Opción A: widget listo
    WinetRSSI.mount('#senal');

    // Opción B: tu propia UI escuchando el evento
    document.addEventListener('winet:rssi', function (e) {
      var s = e.detail;
      document.getElementById('mio').textContent =
        s.available ? (s.rssi + ' dBm · ' + s.label + (s.band ? ' · ' + s.band : ''))
                    : 'No disponible (abrir desde la app)';
    });

    // Respaldo de pruebas en navegador (si no hay Node)
    (function verificarClasificacion() {
      var casos = [[-24,'optima'],[-50,'optima'],[-51,'buena'],[-60,'buena'],
        [-61,'baja'],[-77,'baja'],[-78,'debil'],[-84,'debil'],
        [-85,'fuera'],[-92,'fuera'],[null,'na'],[0,'na']];
      var ok = casos.every(function (c) { return WinetRSSI.classify(c[0]).quality === c[1]; });
      console.log(ok ? 'OK: clasificación W-NET correcta (' + casos.length + ' casos)'
                     : 'ERROR: la clasificación no coincide');
    })();
  </script>
</body>
</html>
```

- [ ] **Step 2: Verificar en navegador (opcional)**

Abrir `web/ejemplo.html` en un navegador. Consola debe mostrar
`OK: clasificación W-NET correcta (12 casos)` y el widget debe indicar
"Abre desde la app" (correcto: no hay puente en un navegador normal).

- [ ] **Step 3: Commit**

```bash
git add web/ejemplo.html
git commit -m "docs: página demo de integración del snippet RSSI"
```

---

### Task 7: Workflow de compilación en GitHub Actions

**Files:**
- Create: `.github/workflows/build-apk.yml`

**Interfaces:**
- Consumes: el proyecto Gradle completo (Tareas 1–4).
- Produces: artifact `winet-senal-debug` con `app-debug.apk` descargable desde
  la pestaña Actions de GitHub.

- [ ] **Step 1: Crear `.github/workflows/build-apk.yml`**

```yaml
name: Build APK

on:
  push:
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Configurar JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Configurar Android SDK
        uses: android-actions/setup-android@v3

      - name: Instalar paquetes del SDK
        run: sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

      - name: Configurar Gradle
        uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: '8.9'

      - name: Compilar APK debug
        run: gradle assembleDebug --no-daemon

      - name: Subir APK
        uses: actions/upload-artifact@v4
        with:
          name: winet-senal-debug
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/build-apk.yml
git commit -m "ci: compilación del APK en GitHub Actions"
```

> **Verificación:** al subir el repo a GitHub, la pestaña **Actions** debe mostrar el
> workflow en verde y el artifact `winet-senal-debug` descargable con el APK.

---

### Task 8: README (guía de compilación e integración)

**Files:**
- Create: `README.md`

**Interfaces:**
- Consumes: todos los componentes anteriores.
- Produces: instrucciones para (a) configurar la URL, (b) compilar en la nube,
  (c) instalar el APK, (d) integrar el snippet en la web de Vercel.

- [ ] **Step 1: Crear `README.md`**

````markdown
# W-NET Señal — Captura de RSSI

Mini-app Android que abre la página de speedtest de W-NET (Vercel) y le da acceso
al **RSSI real (dBm)** y a la **banda (2.4/5/6 GHz)** del enlace WiFi, para que los
técnicos evalúen la calidad de la señal en campo.

## 1. Configurar la URL

Edita `app/src/main/java/pe/win/rssi/MainActivity.kt` y reemplaza:

```kotlin
private val webUrl = "https://TU-PROYECTO.vercel.app"
```

por la URL real de tu página.

## 2. Compilar el APK (en la nube, sin instalar nada)

1. Crea un repositorio en GitHub y sube esta carpeta.
2. Ve a la pestaña **Actions** → workflow **Build APK** → se ejecuta solo al subir
   (o dale "Run workflow").
3. Al terminar (verde), descarga el artifact **winet-senal-debug** → contiene
   `app-debug.apk`.

## 3. Instalar en el Android del técnico

1. Copia `app-debug.apk` al teléfono.
2. Ábrelo; habilita "Instalar apps de orígenes desconocidos" si lo pide.
3. Al abrir la app, **acepta el permiso de ubicación** (Android lo exige para leer
   el WiFi) y mantén la **ubicación del teléfono activada**.

## 4. Integrar el medidor en tu web de Vercel

Sube `web/winet-rssi.js` a tu proyecto e inclúyelo:

```html
<script src="/winet-rssi.js"></script>
```

Luego, dos formas de usarlo:

**A) Widget automático:**
```html
<div id="senal"></div>
<script>WinetRSSI.mount('#senal');</script>
```

**B) Tu propia UI escuchando el dato:**
```html
<script>
  document.addEventListener('winet:rssi', function (e) {
    var s = e.detail; // { available, rssi, band, quality, label, color }
    // ...actualiza tu interfaz con s.rssi, s.label, s.color...
  });
</script>
```

Consulta `web/ejemplo.html` para una demo completa.

## Clasificación de calidad (W-NET)

| RSSI (dBm) | Calidad | Color |
|---|---|---|
| −24 a −50 | Óptima | Verde oscuro |
| −51 a −60 | Buena | Verde limón |
| −61 a −77 | Baja | Amarillo |
| −78 a −84 | Débil | Naranja |
| −85 a −92 | Fuera de cobertura | Rojo |

## Notas técnicas

- El RSSI **solo** está disponible dentro de la app (un navegador normal no puede
  leerlo). El speedtest sigue funcionando en cualquier navegador.
- `WifiManager.connectionInfo` está deprecado en Android 12+ pero sigue operativo;
  migrar a `ConnectivityManager` + `NetworkCapabilities` es una mejora futura.
- El APK es de tipo *debug* (firma automática), suficiente para distribución interna.
````

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: guía de compilación e integración"
```

---

## Notas de verificación global

- **Lo único ejecutable/verificable en el PC actual** es la prueba Node de la Tarea 5
  (`node web/winet-rssi.test.js`) y la demo en navegador (Tarea 6). El resto (Android)
  se verifica al compilar en GitHub Actions (Tarea 7) y con una prueba manual en un
  teléfono real conectado a WiFi.
- Orden recomendado: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8. Las tareas 5 y 6 (web) son
  independientes de las 1–4 (Android) y pueden hacerse en paralelo.
