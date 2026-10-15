# Jesper 3D para Android

Primera versión nativa para una tablet Android conectada **directamente por USB OTG** a una impresora Marlin. Desarrollada en el fork de [Octo4a](https://github.com/feelfreelinux/octo4a), conserva el proyecto original en `../app/` y su licencia AGPL-3.0.

La nueva app funciona sin instalar OctoPrint, Alpine, Python ni un servidor. Reutiliza la familia de controladores `usb-serial-for-android` que usa Octo4a, actualizada y desacoplada de su puente de terminal virtual. No abras ambos programas sobre el mismo puerto USB.

## Versión 0.3: modelo económico y cálculo provisional

Nueva área **Presupuestos**, independiente de la conexión USB: importar/exportar un perfil privado JSON, editar tarifas y catálogo de materiales, calcular un lote y generar una vista/texto para el cliente con concepto y precio final. Diseño, impresión y montaje son ingresos por trabajo propio, no gastos duplicados. Electricidad opcional, gastos registrados/repercutidos separados, margen adicional e impuestos configurables. Dinero con aritmética decimal y medias históricas que conservan precios repetidos.

El perfil privado se entrega separado de la APK y no se publica en el fork. Importarlo no crea compras, existencias ni pedidos. El modelo se conserva en la tablet y puede exportarse; el cálculo es provisional, sin historial de presupuestos enviados/aceptados ni PDF. Las horas y consumos empiezan pendientes. Las actualizaciones con firma temporal pueden requerir reinstalar: exporta tu modelo antes de desinstalar.

Detalles: [modelo económico](../docs/MODELO_ECONOMICO.md).

### Control de impresora incorporado en 0.2

- Interfaz minimalista con modo claro/oscuro del sistema y la cama como pantalla inicial.
- Progreso por fases, tiempo transcurrido y puntos recibidos durante la medición.
- El plazo de una operación ya no se confunde con una cancelación silenciosa de toda la conexión. Los avisos `busy` renuevan la espera de actividad durante el sondeo, con un máximo total de 20 minutos para G29. Un fallo real del enlace o una respuesta que nunca termina sigue cerrando la sesión de forma explícita para evitar comandos desincronizados.
- La malla recibida al finalizar G29 se incorpora antes de consultar el estado posterior. Si esa consulta falla, se conserva el resultado ya confirmado.
- Estados separados para copia local, cambios en RAM, guardado confirmado por Marlin y orden aceptada sin confirmación de almacenamiento. La persistencia tras reiniciar no se da por verificada.
- Guardado opcional al terminar: desactivado por defecto, disponible al confirmar una nueva medición y condicionado a soporte EEPROM. M500 puede guardar más ajustes que la propia malla.
- Historial local de hasta 12 mallas, comparación por índices y exportación CSV sin conexión. No se restauran mallas antiguas automáticamente a la impresora.
- Registro persistente y exportación del diagnóstico, con operación y comando que agotó el plazo.
- Protección de la sesión USB frente al reposo de CPU mientras el servicio está activo. No garantiza que HyperOS no cierre el proceso: requiere prueba en la tablet real.
- Búsqueda de ajustes y órdenes de ventilador de capa a 0/50/100 %. No se presenta la orden solicitada como una medida de velocidad real.

### Qué ocurre al medir una cama

1. Se busca el origen y se ejecuta G29, sin repetirlo automáticamente.
2. Cuando Marlin termina y confirma la orden, se procesa la cuadrícula comunicada y se guarda una copia en la tablet.
3. Se solicita M420 V para actualizar la malla/compensación y M114 para consultar la posición.
4. Si se marcó **Guardar también en la impresora al terminar**, se envía M500. En caso contrario aparece **Pendiente de guardar en la impresora**.
5. Solo se muestra **Marlin confirmó M500** cuando la respuesta incluye `Settings Stored` y no hay error. Un `ok` aislado se muestra como orden aceptada con almacenamiento sin confirmar. En ambos casos debe comprobarse la persistencia tras reiniciar.

La copia de la tablet y la memoria de la impresora son independientes. Una lectura de una malla ya existente no demuestra que estuviera guardada en EEPROM.

## Funciones disponibles

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
- Registro persistente/exportable y solicitud de parada al firmware mediante `M112`.

No incluye todavía edición individual de puntos, ajuste guiado de tornillos, edición de todos los parámetros, soporte UBL/mesh manual, impresión de archivos, conexión remota, gráficos históricos ni exportación de perfiles. El número/área de los puntos de sondeo depende del firmware: la interfaz no inventa coordenadas físicas ni cambia opciones compiladas en Marlin.

## Compilar y obtener la APK

Abre **esta carpeta `tablet/`** en Android Studio. El proyecto original `app/` tiene otro sistema de construcción y no interviene en esta app.

Requisitos: JDK 17, Android SDK 35 y acceso a Google Maven, Maven Central, JitPack y Gradle. Las versiones están fijadas en los archivos Gradle. No se afirma que sean las versiones más recientes.

```powershell
.\gradlew.bat :protocol:test :pricing:test :app:lintDebug :app:assembleDebug
```

La APK de prueba se genera en `app/build/outputs/apk/debug/app-debug.apk`. El flujo **Android tablet** de GitHub Actions ejecuta esas mismas comprobaciones, pruebas del controlador con respuestas simuladas y pruebas de interfaz en Android 15. Publica el artefacto `jesper3d-tablet-debug`. No requiere las claves de firma privadas de Octo4a. Es una compilación de desarrollo, no una publicación en Google Play.

Las APK de Actions conservan la firma de desarrollo temporal del entorno de compilación. Android puede exigir desinstalar la APK anterior antes de instalar una compilación nueva; esa desinstalación borra los perfiles, copias y registros locales de la app, por lo que deben exportarse primero cuando existan.

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

Se envía un comando y se espera su `ok` antes del siguiente. `busy:` no se interpreta como finalización y renueva el plazo de actividad. Los movimientos manuales esperan `M400` antes de volver a consultar la posición. El origen tiene un máximo total de 5 minutos; la medición, 20 minutos por comando largo, con un máximo de 3 minutos consecutivos sin recibir datos. Las consultas normales esperan hasta 30 segundos sin actividad y 60 segundos totales. Un plazo agotado, reinicio, petición de reenvío o fallo USB deja un motivo explícito y detiene la sesión; no se repite automáticamente un movimiento. La malla recibida previamente y el registro permanecen disponibles. El estado de origen no sobrevive a una reconexión. Solo se aceptan movimientos dentro de los límites del perfil confirmado.

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
