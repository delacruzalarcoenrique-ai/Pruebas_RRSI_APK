# W-NET Señal — Captura de RSSI

Mini-app Android que abre la página de speedtest de W-NET (Vercel) y le da acceso
al **RSSI real (dBm)** y a la **banda (2.4/5/6 GHz)** del enlace WiFi, para que los
técnicos evalúen la calidad de la señal en campo.

> 📘 **¿Vas a mantener o compilar este proyecto?** Lee
> **[MANUAL-TECNICO.md](MANUAL-TECNICO.md)** — explica la arquitectura con diagramas,
> cómo construir el APK paso a paso, las dos fuentes de RSSI y los problemas
> frecuentes.

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
