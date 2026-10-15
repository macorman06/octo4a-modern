# Verificación de Jesper 3D 0.2.0-alpha

Fecha: 15 de octubre de 2026.

Código comprobado: `aee05e4e2a5b198513c6a6f8ae6e6b6c91c6c4e8`.

[Ejecución de GitHub Actions](https://github.com/macorman06/octo4a-modern/actions/runs/38773230075).

## Corrección de la desconexión

La versión anterior dejaba escapar un tiempo de espera de respuesta como `CancellationException`. El cierre final de la sesión USB se ejecutaba sin registrar ese fallo como una excepción de protocolo visible. También confundía mensajes que empezaran por `start` con el anuncio de reinicio exacto `start` y podía descartar la lectura del sondeo si fallaba una consulta posterior.

La nueva versión utiliza plazos de actividad y plazos máximos independientes, conserva la malla confirmada por G29 antes de las consultas posteriores, deja un motivo explícito de cierre y mantiene un registro exportable. La copia local se guarda por separado de EEPROM. `Settings Stored` sin error y con `ok` es necesario para mostrar confirmación de M500; un `ok` aislado no prueba almacenamiento. No se repite una medición ni se reabre automáticamente un USB que pueda reiniciar la placa.

Sin un registro de la impresora física no se atribuye el caso observado a una causa única. Los cambios corrigen y cubren estos fallos concretos; la reproducción final en CR-10 S4/Pad 6 sigue pendiente.

## Comprobado

- APK Android compilada.
- 35 pruebas de protocolo: 35 correctas, 0 fallos y 0 omitidas. Incluyen una medición con más de diez minutos de `busy`, continuación de la misma comunicación, inactividad, plazo máximo, cancelación externa, guardado rechazado/no confirmado, malla subdividida, historial, comparación y CSV con configuración regional española.
- 5 pruebas Android: 3 ejercitan el controlador real con respuestas serie simuladas; 2 cubren navegación, controles bloqueados sin USB, malla simulada, selección de puntos, comparación y visibilidad del botón de guardado.
- Regresión del controlador: tras G29 se confirma y archiva una malla; se agota el plazo de M420 V; una nueva instancia del controlador recupera la misma malla de las preferencias de la tablet.
- Análisis Android sin errores; 9 avisos de actualización de SDK/dependencias y reglas de extracción de datos, conservados para trabajo posterior.
- Capturas de Control, Cama y una malla simulada revisadas visualmente. La captura con valores incluye la indicación visible de datos simulados.
- Huellas de los artefactos contrastadas con los valores de GitHub.

SHA-256 de la APK entregada:

```text
fa7baf73f5e328a9d583786de4812090aacad92c0a7b85f8b92315e201a2db3c  Jesper3D-0.2.0-alpha.apk
```

## Pendiente en la impresora real

Repetir la medición en la CR-10 S4/Pad 6 y contrastar los mensajes exactos de TH3D/Marlin, la continuidad USB durante/después del sondeo, el reposo de HyperOS, la cuadrícula obtenida y la confirmación de guardado. El emulador y las respuestas simuladas no verifican el chip USB, el cable, la placa ni la persistencia tras un reinicio real.

La lectura de los ajustes corresponde a RAM. La edición/restauración de mallas, UBL e impresión de archivos no forman parte de esta versión. La comparación de cuadrículas usa índices; no inventa coordenadas físicas de los sensores.

## Instalación y firma

Esta entrega conserva la firma temporal de desarrollo de GitHub Actions. Android puede exigir desinstalar la APK anterior por incompatibilidad de firmas, perdiendo entonces los datos locales de esa app. Deben exportarse previamente las copias y registros que se deseen conservar. La propuesta de crear una firma permanente y enviarla a los secretos del fork fue bloqueada por revisión automática: no se ha creado ni enviado ese material privado y requiere autorización expresa.
