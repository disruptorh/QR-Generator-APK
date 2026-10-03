# QR Generator para Android

Portátil del generador de QR de escritorio a Android. El código QR no está
reimplementado: la app usa el mismo núcleo C++ que la aplicación de escritorio,
incluido como submódulo en `core/qr-generator`. Codificar, verificar, describir y
exportar pasan por ese núcleo, así que las dos interfaces no pueden divergir.

## Requisitos

- JDK 17
- Android SDK con la plataforma 35 y build-tools 35
- NDK 27.0.12077973 y CMake 3.22.1 (los instala el SDK Manager)
- `ANDROID_HOME` apuntando al SDK, o `sdk.dir` en `local.properties`

## Compilar

```bash
./gradlew :app:assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease   # app/build/outputs/apk/release/app-release.apk
./gradlew :app:lintDebug
```

ABIs generadas: `arm64-v8a`, `armeabi-v7a`, `x86_64`.

Para una versión firmada con tu propia clave, crea `keystore.properties` en la raíz
(está en `.gitignore`) con `storeFile`, `storePassword`, `keyAlias` y
`keyPassword`. Sin ese archivo la variante release se firma con la clave de
depuración, suficiente para instalarla pero no para publicarla.

## Pruebas del puente

Los puntos delicados del puente —el formato de los registros y el dibujo de la
vista previa— se prueban en el host, sin dispositivo:

```bash
cmake -S app/src/main/cpp/tests -B /tmp/qrhost \
      -DQR_CORE_DIR="$PWD/core/qr-generator"
cmake --build /tmp/qrhost -j
ctest --test-dir /tmp/qrhost --output-on-failure
```

La suite no reimplementa nada: llama al núcleo real, comprueba que los tamaños
que anuncia el registro coinciden con `render::raster_size`, y que cada módulo de
la matriz corresponde a su píxel en la vista previa.

## Cómo se reparten las responsabilidades

- `core/qr-generator` — submódulo. Motor, verificación por decodificación,
  estadísticas y exportación.
- `app/src/main/cpp/` — el puente. Traduce entradas y resultados entre Kotlin y
  el núcleo; no decide nada. Los registros son líneas `clave=valor` en UTF-8 con
  escapes percentuales. Los píxeles de la vista previa viajan aparte, como
  `IntArray`, porque el núcleo ya informa de su tamaño.
- `app/src/main/java/…/core/` — modelos de Kotlin y llamadas JNI.
- `app/src/main/java/…/QrViewModel.kt` — estado de la pantalla. Recalcula en cada
  cambio de campo, fuera del hilo principal.
- `app/src/main/java/…/ui/` — interfaz Compose, en español. Guardar usa el
  selector de documentos del sistema, compartir usa `FileProvider` y el portapapeles
  del sistema.

## Vista previa

La vista previa usa el mismo rasterizador que la exportación con un píxel por
módulo; el teléfono la escala. Así el coste por pulsación es mínimo y lo que se ve
en pantalla es exactamente lo que se exporta.