# QR Generator para Android

Portátil del generador de QR de escritorio a Android. El código QR **no está
reimplementado**: la app usa el mismo núcleo C++ que la aplicación de escritorio,
incluido como submódulo en `core/qr-generator`. Codificar, verificar, describir y
exportar pasan por ese núcleo, así que las dos interfaces no pueden divergir.

<p align="center">
  <a href="https://github.com/disruptorh/QR-Generator-APK/releases/latest/download/QR-Generator.apk">
    <img alt="Descargar" src="https://img.shields.io/badge/%E2%AC%87%20Download-latest%20release-2f6feb?style=for-the-badge&logo=github&logoColor=white">
  </a>
  <a href="https://github.com/disruptorh/QR-Generator-APK/releases/latest">
    <img alt="Versiones" src="https://img.shields.io/github/v/release/disruptorh/QR-Generator-APK?label=release&style=flat&logo=github&logoColor=white">
  </a>
  <a href="./LICENSE">
    <img alt="Licencia" src="https://img.shields.io/badge/licencia-Apache--2.0-blue?style=flat">
  </a>
</p>

## 📥 Descarga rápida

**[`QR-Generator.apk`](https://github.com/disruptorh/QR-Generator-APK/releases/latest/download/QR-Generator.apk)**
— APK firmado, listo para instalar en Android 7.0 o superior.

```bash
# 1. Descargar la última release
curl -L -o QR-Generator.apk https://github.com/disruptorh/QR-Generator-APK/releases/latest/download/QR-Generator.apk

# 2. Instalar en el móvil conectado por USB (con depuración USB activada)
adb install -r QR-Generator.apk
```

Un solo APK con las tres ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`), así que no
hay que elegir arquitectura. La app no pide ningún permiso en tiempo de
ejecución.

## 🚀 Uso rápido

Una sola pantalla, con tres zonas:

1. **Vista previa** arriba: el símbolo aparece en cuanto escribes el contenido,
   sin pulsar nada. Debajo sale la descripción del núcleo: versión, corrección de
   errores, máscara y tamaño en módulos. Tocar la imagen la abre a pantalla
   completa, con los datos usados y la capacidad, el tamaño de la exportación y
   cuántos bloques se han verificado.
2. **Editor** abajo: un selector de tipo de contenido y los campos que le
   corresponden. Es la única zona que hace scroll.
3. **Barra inferior**: **Guardar**, **Compartir** y **Copiar** (el contenido, no la
   imagen).

En la barra superior está el selector de formato (**PNG** / **SVG**) y el botón de
**Ajustes**, que abre una hoja inferior con lo que no hace falta tocar cada
ratón: corrección de errores, refuerzo de ECC, máscara, versión mínima y máxima,
píxeles por módulo, zona de silencio y los dos colores.

Tipos de contenido disponibles: **Texto, URL, Wi-Fi, Contacto, Correo, SMS,
Teléfono, Ubicación y Evento**.

En pantallas anchas (≥ 600 dp) la vista previa y el editor se ponen uno al lado
del otro; en móvil van apilados.

## 📦 Compilar desde código

La raíz del repositorio **es** el proyecto Gradle. `settings.gradle.kts` solo
incluye `:app`: `core/` no es un módulo de Gradle, es el **submódulo de C++**
del que se compila el núcleo.

### Requisitos

| Qué | Versión |
|---|---|
| JDK | 17 (`sourceCompatibility`/`jvmTarget` = `17`) |
| Android Gradle Plugin | 8.9.1 |
| Kotlin | 2.2.0 (+ plugin de Compose) |
| Gradle | 8.11.1, vía wrapper (no hace falta instalarlo) |
| Android SDK | plataforma **35**, `minSdk 24`, `targetSdk 35` |
| NDK | **27.0.12077973** (fijado con `ndkVersion` en `app/build.gradle.kts`) |
| CMake | **3.22.1** (fijado con `version` en `externalNativeBuild`) |
| zlib | headers de desarrollo, para el escritor de PNG del núcleo |

El proyecto es multi-ABI: compila C++ para `arm64-v8a`, `armeabi-v7a` y `x86_64`.
**No incluye `x86_64`**, así que en un emulador con CPU x86_64 el APK no instala;
usa un emulador arm64 o un dispositivo real.

**`local.properties`**: el SDK se localiza por ese fichero, que está en
`.gitignore` y por eso no viene en el clon:

```bash
# 1. Crear local.properties apuntando a tu SDK (cambia la ruta si no es esta)
printf 'sdk.dir=%s\n' "$HOME/Android/Sdk" > local.properties

# 2. Comprobar que ha quedado bien
cat local.properties
```

La ruta que trae el repositorio publicado no te sirve: es la del equipo que
compiló el APK y en tu equipo ese directorio no existe. Si tienes el SDK en otro
sitio, sustituye `$HOME/Android/Sdk` por esa ruta. También vale tener
`ANDROID_HOME` apuntando al SDK en vez de escribir el fichero.

### Clonar

El núcleo es un submódulo, así que el clon tiene que traerlo:

```bash
# 1. Clonar el repositorio con sus submódulos
git clone --recurse-submodules https://github.com/disruptorh/QR-Generator-APK.git
cd QR-Generator-APK

# 2. Comprobar que el núcleo está
ls core/qr-generator/CMakeLists.txt
```

Si ya lo clonaste sin `--recurse-submodules`, o si el submódulo se queda vacío:

```bash
# 1. Descargar el submódulo del núcleo C++
git submodule update --init --recursive
```

Sin ese paso el build falla con
`QR_CORE_DIR is required: pass -DQR_CORE_DIR=<path to the qr core>`, porque es
la variable que CMake necesita para encontrar los `.cpp` del núcleo
(`app/build.gradle.kts` la pasa como `-DQR_CORE_DIR=${rootProject.projectDir}/core/qr-generator`,
así que solo hay un sitio dueño del path).

### Dependencias

Paquetes del SDK que hacen falta (Debian/Ubuntu; en Android Studio, SDK Manager):

```bash
# 1. Aceptar las licencias del SDK (una sola vez)
yes | sdkmanager --licenses

# 2. Instalar la plataforma 35, las platform-tools (adb), el NDK y CMake
sdkmanager "platforms;android-35" "platform-tools" "ndk;27.0.12077973" "cmake;3.22.1"
```

Para compilar los **tests del puente en el host** (no en Android) hacen falta
también las herramientas de compilador de C++ y los headers de zlib:

```bash
# 1. Dependencias de los tests del host (Debian/Ubuntu)
sudo apt update && sudo apt install -y build-essential cmake zlib1g-dev
```

### Compilar

```bash
# 1. APK debug (para probar en el dispositivo)
./gradlew :app:assembleDebug

# 2. APK release
./gradlew :app:assembleRelease

# 3. Lint
./gradlew :app:lintDebug
```

Rutas exactas:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

El release sale **siempre instalable**: si no hay `keystore.properties`, se firma
con la clave de depuración de Android en vez de quedarse sin firmar. La sección
[🔐 Seguridad y firma](#-seguridad-y-firma) tiene el bloque para firmar con una
clave propia.

No hay `build_apk.sh` en este repositorio: los dos comandos de arriba son el
build completo.

### Ejecutar los tests

No hay tests de JVM ni de instrumentación (`app/src/test/` y `app/src/androidTest/`
no existen). Los puntos delicados —el formato de los registros y el dibujo de la
vista previa— se prueban en el **host**, sin dispositivo, porque son C++ puro:

```bash
# 1. Configurar, compilar y ejecutar los tests del puente
cmake -S app/src/main/cpp/tests -B /tmp/qrhost -DQR_CORE_DIR="$PWD/core/qr-generator" && cmake --build /tmp/qrhost -j && ctest --test-dir /tmp/qrhost --output-on-failure
```

La suite (`app/src/main/cpp/tests/codec_tests.cpp`) no reimplementa nada: compila
las mismas fuentes del núcleo y del códec que el APK, y llama al núcleo real.

| Test | Qué cubre |
|---|---|
| `test_escape_round_trip` | El escape percentual de Kotlin y el de C++ coinciden: ida y vuelta, saltos de línea, `=`, bytes UTF-8, y escapes incompletos (`100%`, `50%2`, `%%41`) |
| `test_parse_full_message` | Un mensaje con **todos** los campos (los 8 tipos de contenido, ECC, máscara, versiones, píxeles, colores y formato) se interpreta campo a campo |
| `test_defaults_and_unknown_keys` | Mensaje vacío: los valores por defecto del núcleo. Las claves desconocidas se ignoran sin romper lo demás |
| `test_rejects_bad_values` | Los valores que no se pueden interpretar se rechazan informando del campo concreto |
| `test_generate_end_to_end` | Wi-Fi válido de punta a punta: payload exacto, bloques verificados > 0, y el registro (`ok`, `payload`, `describe`, `filename`, `version`, `px_w`, `preview_w/h`) coincide con lo que el núcleo dice |
| `test_preview_pixels_match_the_matrix` | **Cada módulo de la matriz corresponde a su píxel** en la vista previa, y la zona de silencio es blanco opaco |
| `test_record_reports_failure` | Un Wi-Fi sin SSID falla: el registro trae `ok=0` con `err.field` y `err.msg`, y **sin** payload |
| `test_core_reports_the_bad_coordinate` | Una latitud ilegible llega como `NaN` al núcleo y vuelve el error con el campo `latitude`: el editor nunca inventa un valor |
| `test_exports_from_a_verified_symbol` | La exportación produce un PNG con firma válida y un SVG que empieza por `<svg` |

### Ejecutar la aplicación

```bash
# 1. Instalar el debug en el dispositivo conectado
./gradlew :app:installDebug
```

O instalar a mano un APK ya compilado:

```bash
# 1. Instalar el release
adb install -r app/build/outputs/apk/release/app-release.apk

# 2. Instalar el debug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 🧰 Comandos útiles

```bash
# 1. APK debug
./gradlew :app:assembleDebug

# 2. APK release
./gradlew :app:assembleRelease

# 3. Instalar el debug en el móvil conectado
./gradlew :app:installDebug

# 4. Lint
./gradlew :app:lintDebug

# 5. Tests del puente en el host
cmake -S app/src/main/cpp/tests -B /tmp/qrhost -DQR_CORE_DIR="$PWD/core/qr-generator" && cmake --build /tmp/qrhost -j && ctest --test-dir /tmp/qrhost --output-on-failure

# 6. Limpiar
./gradlew clean
```

## 🗂️ Estructura del proyecto

```text
.
├── app/
│   ├── build.gradle.kts          ← compileSdk 35, NDK, CMake, ABIs y firma
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml   ← FileProvider, sin permisos
│       ├── cpp/                  ← el puente (C++)
│       │   ├── CMakeLists.txt    ← compila el núcleo del submódulo en qr_core
│       │   ├── qr_bridge.cpp     ← capa JNI: genera, vista previa, PNG y SVG
│       │   ├── inputs_codec.cpp  ← lee y escribe los registros clave=valor
│       │   └── tests/            ← suite de host (codec_tests.cpp)
│       ├── java/com/disruptorh/qrgenerator/
│       │   ├── core/             ← modelos Kotlin y llamadas JNI
│       │   ├── ui/               ← pantalla Compose, campos y tema
│       │   ├── QrViewModel.kt    ← estado; recalcula en cada cambio
│       │   └── MainActivity.kt   ← selector de documentos, compartir, portapapeles
│       └── res/                  ← icono, tema y rutas del FileProvider
├── core/
│   └── qr-generator/             ← submódulo: el motor C++ (QR-Generator)
├── build.gradle.kts              ← AGP 8.9.1, Kotlin 2.2.0
├── settings.gradle.kts           ← solo el módulo :app
├── .gitmodules                   ← core/qr-generator -> disruptorh/QR-Generator
├── gradle/wrapper/               ← Gradle 8.11.1
└── gradle.properties
```

## 🧬 Decisiones técnicas

### Cómo se reparten las responsabilidades

- **`core/qr-generator`** — submódulo (`disruptorh/QR-Generator`, fijado en el
  tag 1.0). Motor, verificación por decodificación, estadísticas y exportación.
- **`app/src/main/cpp/`** — el puente. Traduce entradas y resultados entre Kotlin y
  el núcleo; **no decide nada**. Los registros son líneas `clave=valor` en UTF-8
  con escapes percentuales (todo byte fuera de `[A-Za-z0-9._~-]` pasa a `%XX`, que
  es lo que impide que un `=` o un salto de línea en el texto del usuario rompan el
  framed de líneas). Los píxeles de la vista previa viajan aparte, como
  `IntArray`, porque el núcleo ya informa de su tamaño.
- **`app/src/main/java/…/core/`** — modelos de Kotlin y llamadas JNI
  (`QrCore.kt`, `Inputs.kt`).
- **`QrViewModel.kt`** — estado de la pantalla. `inputs` es un `MutableStateFlow` y
  `state` se deriva con `.map { … }.flowOn(Dispatchers.Default)`: **recalcula en
  cada cambio de campo, fuera del hilo principal**, sin debounce, porque generar,
  verificar y rasterizar son fracciones de milisegundo y así lo que hay en pantalla
  siempre describe lo que acabas de escribir.
- **`ui/`** — interfaz Compose, en español.

El CMakeLists del puente compila una lista explícita de 8 `.cpp` del submódulo
—los mismos que el build de escritorio mete en su target `qr_core`, menos lo que
es específico de plataforma— y enlaza `z` (zlib) para el escritor de PNG. El
núcleo ya está libre de ventanas e I/O, así que no necesita parches para correr en
Android.

`CMakeLists.txt` no tiene el path del núcleo escrito dentro: lo recibe en
`QR_CORE_DIR`, que Gradle pasa desde `rootProject.projectDir`, para que el build
nativo y el de Gradle no puedan discrepar sobre dónde está.

### La vista previa

La vista previa usa el mismo rasterizador que la exportación con **un píxel por
módulo**; el teléfono la escala con `FilterQuality.None` (vecino más próximo), así
que cada módulo sigue siendo un cuadrado nítido y lo que se ve en pantalla es
exactamente lo que se exporta. El coste por pulsación es mínimo porque no se
rasteriza a resolución final, y el tamaño llega del núcleo
(`previewWidth`/`previewHeight`).

Si el símbolo no cabe a los píxeles por módulo elegidos, el deslizador baja solo
hasta `4096 px` de lado (el tope del núcleo) en vez de dejar que la exportación
falle después.

### Errores

El núcleo devuelve `ok=0` con `err.field` (el identificador estable del campo) y
`err.msg`. La pantalla marca **el campo exacto** que está mal, así que un error no
sale como «no se pudo generar» sino junto al campo culpable. Las coordenadas se
mandan como texto a propósito: la validación y el rango son del núcleo, no del
editor.

## 🔐 Seguridad y firma

- **No hay ningún permiso en el manifiesto**, ni `INTERNET` ni nada: generar un QR
  es una operación local.
- El único `FileProvider` (`${applicationId}.fileprovider`, `exported="false"`)
  existe **solo para compartir**: `res/xml/file_paths.xml` expone únicamente
  `cache-path name="shared"`, que es donde `MainActivity` deja el fichero antes de
  lanzar el `ACTION_SEND`. El permiso de lectura se concede con
  `FLAG_GRANT_READ_URI_PERMISSION` y dura lo que dura ese intent.
- **Guardar** usa el selector de documentos del sistema (`CreateDocument`), así que
  la app solo escribe donde el usuario nombra. El nombre que propone el núcleo es
  `qr-v<versión>-<corrección>-<slug del contenido>.png|svg`.
- La app guarda **nada en disco**: no hay `SharedPreferences` ni base de datos. El
  estado del editor vive en el `ViewModel`, así que el manifiesto puede dejar
  `allowBackup="true"` sin Copias de seguridad que respaldar: no hay nada que
  copiar.
- La copia del contenido al portapapeles es un `ClipData.newPlainText` normal, sin
  marca de sensible: el contenido del QR es público por definición (se acabó de
  imprimir en una pantalla o un cartel).

### Qué pasa sin keystore propio

Aquí la diferencia con otras apps del cuaderno: **sin `keystore.properties` el
release no sale sin firmar**. `app/build.gradle.kts` hace
`signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")`, así
que la variante release se firma con la clave de depuración de Android. El APK es
instalable en cualquier dispositivo, pero no se puede publicar en una tienda ni
sustituir una app ya instalada firmada con otra clave.

`keystore.properties` se busca en la **raíz del repo** (`rootProject.file`), y su
`storeFile` también se resuelve respecto a la raíz.

### Crear un keystore de pruebas

```bash
# 1. Keystore de PRUEBAS: la contraseña está escrita a propósito para que el bloque
#    se pegue tal cual. Es una clave de usar y tirar, no la uses para publicar nada.
keytool -genkeypair -v -keystore release.keystore -storetype PKCS12 -alias qrgen -keyalg RSA -keysize 4096 -validity 10000 -storepass test1234 -keypass test1234 -dname "CN=QR Generator Test, OU=Dev, O=Local, L=Local, ST=Local, C=ES"

# 2. Apuntar la firma en la raíz del repo
cat > keystore.properties <<'EOF'
storeFile=release.keystore
storePassword=test1234
keyAlias=qrgen
keyPassword=test1234
EOF

# 3. Recompilar el release, ahora con tu firma
./gradlew :app:assembleRelease
```

Para una clave de verdad: cambia las cuatro líneas de `keystore.properties` por
tus valores antes de publicar. `keyPassword` puede ser la misma que
`storePassword`. Los dos ficheros (`*.keystore` y `keystore.properties`) están en
`.gitignore`, así que no se pueden subir por accidente.

El release aplica **R8** (`isMinifyEnabled` y `isShrinkResources` a true). Las
funciones JNI están declaradas `external` en Kotlin, así que R8 las conserva; el
núcleo se alcanza solo a través de `QrCore`, y no hay nada más que preservar.

## 📄 Licencia

Apache-2.0 — ver [LICENSE](LICENSE). El núcleo de C++ que se usa como submódulo
tiene su propia licencia en `core/qr-generator/LICENSE`.
