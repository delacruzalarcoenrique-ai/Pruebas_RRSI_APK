# Diseño: Captura de RSSI vía mini-app Android para W-NET TELECOM

**Fecha:** 2026-07-22
**Autor:** Equipo W-NET (delacruza@win.pe) + Claude
**Estado:** En revisión

## 1. Problema y objetivo

Los técnicos de campo de W-NET usan una página web (alojada en Vercel) para
ejecutar pruebas de **speedtest** contra el enlace del cliente. Esa parte ya
funciona. Ahora se necesita **capturar el RSSI** (intensidad de señal, en dBm)
del enlace entre el dispositivo del técnico (STA) y el AP Mesh / ONT Mesh al que
está conectado, para evaluar la calidad del RX en sitio.

**Limitación técnica central:** un navegador web NO expone ninguna API para leer
el RSSI en dBm (por seguridad/privacidad). `navigator.connection` solo da tipo de
red y estimaciones de ancho de banda, nunca el dBm real. Por tanto, HTML/JS puro
en Chrome/Safari **no puede** obtener el RSSI.

**Solución:** una mini-app Android (WebView) que carga la MISMA URL de Vercel y
le inyecta el RSSI real a través de un puente JavaScript, usando `WifiManager`
nativo. La página web no cambia su lógica; solo gana acceso al dato del dBm
cuando se abre dentro de la app.

## 2. Alcance

**Incluye:**
- Mini-app Android (Kotlin, WebView) que carga la URL de Vercel.
- Puente JS nativo `WinetWifi` que expone RSSI (dBm) y banda (2.4/5/6 GHz).
- Snippet `winet-rssi.js` para integrar en la página de Vercel: lee el RSSI,
  lo clasifica por calidad y lo entrega a la UI existente.
- Clasificación de calidad oficial de W-NET (5 niveles de color).
- Compilación del APK vía GitHub Actions (sin instalar nada en el PC).

**No incluye (fuera de alcance):**
- Escaneo de redes WiFi vecinas (SSIDs/canales de otros APs).
- Escaneo de dispositivos de la LAN.
- Rediseño de la UI existente del speedtest (el snippet solo entrega datos;
  el widget visual es opcional).
- Firma de release para Google Play (se usa APK debug para distribución interna).

## 3. Arquitectura

```
┌───────────────────────────────────────────────┐
│           Mini-app Android (WebView)           │
│  ┌─────────────────────────────────────────┐  │
│  │  Página de Vercel (speedtest)           │  │
│  │  + winet-rssi.js                        │  │
│  └─────────────────────────────────────────┘  │
│                     ↕  puente JS               │
│  ┌─────────────────────────────────────────┐  │
│  │  WinetWifi (Kotlin @JavascriptInterface)│  │
│  │  WifiManager → rssi + frequency         │  │
│  └─────────────────────────────────────────┘  │
└───────────────────────────────────────────────┘
```

Dos unidades independientes con interfaz bien definida (el objeto `WinetWifi`):
- La capa nativa solo se ocupa de leer el WiFi y exponerlo.
- La capa web solo consume el puente y lo integra a la UI.

## 4. Componente A — Mini-app Android

- **Lenguaje:** Kotlin. Una sola `MainActivity`.
- **minSdk 24 (Android 7), targetSdk 34.** Cubre prácticamente todo Android de campo.
- **Permisos (Manifest):** `INTERNET`, `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`,
  `ACCESS_FINE_LOCATION`.
- **Permiso en runtime:** se solicita `ACCESS_FINE_LOCATION` al abrir (Android 8+
  lo exige para leer detalles del WiFi). Si el usuario lo niega, la app sigue
  cargando la web pero el RSSI reportará `available:false`.
- **WebView:** pantalla completa, `javaScriptEnabled=true`, `domStorageEnabled=true`,
  carga la URL de Vercel (configurable en una constante `WEB_URL`).
- **Puente JS** — objeto `window.WinetWifi` con métodos `@JavascriptInterface`:
  - `getInfoJson(): String` → `{"rssi":-58,"frequencyMhz":5180,"band":"5 GHz","available":true}`
    (método principal, una sola llamada).
  - `getRssi(): Int` → dBm (ej. `-58`). Si no hay WiFi/permiso → `0`.
  - `getBand(): String` → `"2.4 GHz"` | `"5 GHz"` | `"6 GHz"` | `""`.
- **Lectura del RSSI:** `wifiManager.connectionInfo.rssi` y `.frequency`.
  (`connectionInfo` está deprecado en API 31+ pero sigue operativo; se documenta
  en el código. Alternativa futura: `ConnectivityManager` + `NetworkCapabilities`.)
- **Derivación de banda** por frecuencia (MHz): `>=5925 → 6 GHz`; `>=4900 → 5 GHz`;
  `>0 → 2.4 GHz`; `0 → ""`.
- **Ícono:** adaptativo por vector (XML), sin PNG binarios (mantiene el repo 100% texto).

## 5. Componente B — Snippet `winet-rssi.js`

Archivo único que se importa/pega en la página de Vercel.

- Al cargar, detecta `typeof window.WinetWifi !== 'undefined'`.
- **Dentro de la app:** consulta `getInfoJson()` cada 1000 ms; clasifica el RSSI y:
  - Dispara `document` event `winet:rssi` con detalle
    `{ rssi, band, quality, label, color, available }`.
  - Expone `window.getWifiSignal()` para lectura bajo demanda.
  - Opcional: `WinetRSSI.mount('#selector')` monta un mini-widget listo
    (número grande en dBm, chip de banda, etiqueta de calidad con color).
- **En navegador normal:** entrega `{ available:false }` y, si se montó el widget,
  muestra "Abre desde la app W-NET TELECOM para medir la señal". El speedtest
  existente no se ve afectado.
- **Sin dependencias externas** (JS puro, funciona offline dentro del WebView).

### Clasificación de calidad (oficial W-NET)

| Nivel | `quality` | Rango | Condición | Color sugerido |
|---|---|---|---|---|
| Óptima | `optima` | −24 a −50 dBm | `rssi >= -50` | Verde oscuro `#0F7B0F` |
| Buena | `buena` | −51 a −60 dBm | `-60 <= rssi < -50` | Verde limón `#7CB518` |
| Baja | `baja` | −61 a −77 dBm | `-77 <= rssi < -60` | Amarillo `#E6B800` |
| Débil | `debil` | −78 a −84 dBm | `-84 <= rssi < -77` | Naranja `#E8720C` |
| Fuera de cobertura | `fuera` | −85 a −92 dBm | `rssi < -84` | Rojo `#D32F2F` |

## 6. Componente C — Compilación (GitHub Actions)

- Workflow `.github/workflows/build-apk.yml`:
  - Dispara en `push` y manualmente (`workflow_dispatch`).
  - Instala **JDK 17**, configura **Gradle** (vía `gradle/actions/setup-gradle`)
    y el **Android SDK**.
  - Ejecuta `gradle assembleDebug`.
  - Sube `app-debug.apk` como **artifact** descargable.
- No se requiere Android SDK, Gradle ni Android Studio en el PC del usuario.
- Se evita incluir el binario `gradle-wrapper.jar`: el CI provee Gradle directamente.
- El APK debug se firma automáticamente con la debug keystore; se instala en los
  Android habilitando "orígenes desconocidos". Suficiente para distribución interna.

## 7. Estructura del proyecto

```
winet-wifi-rssi/
├─ .github/workflows/build-apk.yml
├─ settings.gradle.kts
├─ build.gradle.kts
├─ gradle.properties
├─ app/
│  ├─ build.gradle.kts
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/pe/win/rssi/MainActivity.kt
│     └─ res/
│        ├─ values/strings.xml
│        ├─ mipmap-anydpi-v26/ic_launcher.xml (+ ic_launcher_round)
│        └─ drawable/ (vector del ícono)
├─ web/
│  ├─ winet-rssi.js       (snippet para Vercel)
│  └─ ejemplo.html        (demo de integración local)
├─ docs/superpowers/specs/2026-07-22-captura-rssi-android-design.md
└─ README.md              (guía de compilación + integración)
```

## 8. Configuración clave

- `WEB_URL` en `MainActivity.kt`: la URL de Vercel a cargar (a confirmar por W-NET).
- `applicationId`: `pe.win.rssi` (ajustable).
- Nombre visible de la app: "W-NET Señal" (ajustable).
- Nombre del puente: `WinetWifi` (ajustable).

## 9. Manejo de errores

- Sin permiso de ubicación → RSSI `available:false`, mensaje claro; la web sigue.
- Sin WiFi conectado (datos móviles) → `available:false`.
- Fallo de red al cargar Vercel → WebView muestra su error; se puede añadir botón
  "Reintentar" en una iteración futura (fuera de alcance inicial).

## 10. Criterios de éxito

1. Al abrir la app en un Android conectado a WiFi, la página de Vercel carga igual
   que en Chrome.
2. La página recibe el RSSI real en dBm y lo actualiza en vivo (~1 s) al moverse.
3. El valor se clasifica según los 5 niveles de W-NET con su color.
4. En un navegador normal, el speedtest sigue funcionando y el RSSI indica que se
   debe abrir desde la app.
5. El APK se genera en GitHub Actions sin instalar nada localmente.
