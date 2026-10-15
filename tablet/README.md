# Jesper 3D para Android

Primera versión nativa para una tablet Android conectada **directamente por USB OTG** a una impresora Marlin. Desarrollada en el fork de [Octo4a](https://github.com/feelfreelinux/octo4a), conserva el proyecto original en `../app/` y su licencia AGPL-3.0.

La nueva app funciona sin instalar OctoPrint, Alpine, Python ni un servidor. Reutiliza la familia de controladores `usb-serial-for-android` que usa Octo4a, actualizada y desacoplada de su puente de terminal virtual. No abras ambos programas sobre el mismo puerto USB.

## Alcance de la versión 0.1

- Interfaz nativa en español con Kotlin, Jetpack Compose y Material 3, preparada para tablet y pantalla estrecha.
- Selección de dispositivo USB y velocidad: 115200, 250000 y 57600 baudios; permiso USB de Android.
- Servicio de conexión en primer plano que conserva la sesión al girar o cambiar de pantalla.
- Identificación del firmware y capacidades con `M115`.
- Temperatura actual y objetivo de boquilla/cama, consulta de posición y refresco en reposo.
- Lectura y agrupación de ajustes **activos** con `M503`: pasos/mm, velocidades, aceleraciones, PID, desplazamiento del sensor y otros que reporte el firmware. No equivale a leer toda la configuración de compilación ni a verificar EEPROM.
- Lectura de la malla con `M420 V`, representación de cuadrículas bilineales indexadas y selección de cada punto. Los puntos sin medir permanecen desconocidos.
- Consulta y cambio del estado de compensación; consulta del estado después de hacer origen.
- Origen y movimientos pequeños X/Y/Z, después de confirmar el perfil y hacer origen en la sesión.
- Medición automática bilineal con `G28` y `G29`, condicionada a perfil confirmado y capacidades `Z_PROBE`/`AUTOLEVEL` anunciadas por Marlin. Coordenadas reales de sondeo cuando Marlin las emite.
- Temperaturas objetivo limitadas en esta versión a 240 °C de boquilla y 80 °C de cama; apagado de calentadores.
- Guardado explícito mediante `M500`, si el firmware anuncia EEPROM.
- Registro de comunicación y solicitud de parada al firmware mediante `M112`.

No incluye todavía edición individual de puntos, ajuste guiado de tornillos, edición de todos los parámetros, soporte UBL/mesh manual, impresión de archivos, conexión remota, gráficos históricos ni exportación de perfiles. El número/área de los puntos de sondeo depende del firmware: la interfaz no inventa coordenadas físicas ni cambia opciones compiladas en Marlin.

## Compilar y obtener la APK

Abre **esta carpeta `tablet/`** en Android Studio. El proyecto original `app/` tiene otro sistema de construcción y no interviene en esta app.

Requisitos: JDK 17, Android SDK 35 y acceso a Google Maven, Maven Central, JitPack y Gradle. Las versiones están fijadas en los archivos Gradle. No se afirma que sean las versiones más recientes.

```powershell
.\gradlew.bat :protocol:test :app:lintDebug :app:assembleDebug
```

La APK de prueba se genera en `app/build/outputs/apk/debug/app-debug.apk`. El flujo **Android tablet** de GitHub Actions ejecuta esas mismas comprobaciones y publica el artefacto `jesper3d-tablet-debug`. No requiere las claves de firma privadas de Octo4a. Es una compilación de desarrollo, no una publicación en Google Play.

## Primera conexión con CR-10 S4 y Xiaomi Pad 6

1. Impresora encendida, en reposo y sin impresión desde SD. Cierra otras apps que usen USB. Abrir el puerto puede reiniciar algunas placas.
2. Conecta mediante un cable **de datos** compatible con USB OTG y con el puerto físico de la placa. El tipo de conector y la compatibilidad física de esta máquina deben verificarse; no se deducen del nombre del firmware.
3. Abre la APK, pulsa **Conectar USB**, busca dispositivos, selecciona la impresora y prueba primero **115200**. Acepta el permiso Android.
4. Verifica el texto de firmware, las temperaturas y los ajustes. La conexión inicial solamente consulta; no calienta, mueve, mide ni guarda automáticamente.
5. Abre **Cama → Leer malla**. Comprueba número de filas/columnas, signo y orientación frente a la respuesta de la consola o la pantalla de la impresora.
6. Para probar movimientos en otro paso, confirma las dimensiones en **Perfil**, libera la cama y busca origen desde la app. Empieza con 0,1–1 mm. El perfil inicial de 400 × 400 × 400 mm es una propuesta para la CR-10 S4, no una medición de tu máquina modificada.
7. Solo después de verificar el sensor y el modo bilineal, confirma ese modo en Perfil para habilitar nueva medición. Si el firmware no anuncia capacidades, la medición se mantiene bloqueada en esta versión.
8. Guarda únicamente después de revisar los valores. `M500` guarda los ajustes activos admitidos por el firmware, incluidos cambios hechos fuera de la app. Verifica persistencia real tras reiniciar antes de confiar en ella.

La compatibilidad con la Pad 6/HyperOS, el chip USB de la placa y la carga simultánea OTG **requiere prueba física**. No se han realizado acciones sobre una impresora real durante el desarrollo.

## Comportamiento de la comunicación

Se envía un comando y se espera su `ok` antes del siguiente. `busy:` no se interpreta como finalización. Los movimientos esperan `M400` antes de volver a consultar la posición. El origen dura como máximo 180 s por comando; la medición, 600 s. Un timeout, reinicio, petición de reenvío o fallo USB detiene la sesión; no se repite automáticamente un movimiento. El estado de origen no sobrevive a una reconexión. Solo se aceptan movimientos dentro de los límites del perfil confirmado.

La app está pensada para preparación y calibración con la impresora en reposo. No detecta de forma completa trabajos iniciados desde la SD o desde otra interfaz. Desconectar USB o cerrar la app no equivale a apagar los calentadores. `M112` depende de que el enlace y el firmware respondan y del soporte de `EMERGENCY_PARSER`; no sustituye al interruptor físico.

## Estructura

- `protocol/`: intérprete Marlin sin dependencias Android, lector de líneas/malla, validación de comandos y pruebas de regresión.
- `app/`: interfaz Compose, permisos y servicio Android, transporte USB y ejecución de operaciones.
- `../app/`: Octo4a original conservado como referencia y posibilidad de evolución futura.

## Fuentes técnicas

- [Código original de Octo4a](https://github.com/feelfreelinux/octo4a) y `app/app/src/main/java/com/octo4a/serial/VirtualSerialDriver.kt`.
- [Controladores USB serie](https://github.com/mik3y/usb-serial-for-android).
- [USB host en Android](https://developer.android.com/develop/connectivity/usb/host).
- Marlin: [M115](https://marlinfw.org/docs/gcode/M115.html), [M503](https://marlinfw.org/docs/gcode/M503.html), [M420](https://marlinfw.org/docs/gcode/M420.html), [G29 bilineal](https://marlinfw.org/docs/gcode/G029-abl-bilinear.html).

## Licencia

Este fork conserva `../LICENSE.md` y la atribución al proyecto Octo4a. El código añadido se distribuye bajo la misma licencia AGPL-3.0. Las dependencias conservan sus propias licencias. Se mantiene disponible el código fuente correspondiente junto a las compilaciones del fork.
