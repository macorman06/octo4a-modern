# Validación de Jesper 3D 0.3.0-alpha

Fecha: 15 de octubre de 2026.

Código verificado: `edad6ac9e17266cd2f7b36c07af0e0d524bd56e7`.

Resultado: **success** en [Android tablet, ejecución 38811054685](https://github.com/macorman06/octo4a-modern/actions/runs/38811054685).

## Alcance

Motor económico Kotlin independiente, importación/exportación de un modelo privado, tarifas y catálogo editables, cálculo provisional del lote, separación de gasto e ingreso por trabajo propio, electricidad opcional y salida de texto para cliente con concepto y precio final. Se conserva el módulo de impresora de 0.2.

## Comprobaciones

- **35 pruebas de protocolo**, 0 fallos y 0 omitidas.
- **22 pruebas económicas**, 0 fallos y 0 omitidas: trabajo propio no descontado dos veces, margen adicional, impuestos fuera del ingreso del resultado, gastos registrados/repercutidos independientes, energía opcional, medias sin deduplicación, unidades, datos pendientes y redondeos.
- **10 pruebas Android**, 0 fallos y 0 omitidas: regresiones del controlador, pantallas existentes, persistencia y validación del perfil, salida pública, sustitución de materiales/tarifas, pegado inválido, guardado de porcentajes y adaptación visual.
- Lint Android y construcción de APK correctos.
- Capturas reales de emulador Android 15 en tablet y formato compacto 432 × 960 con fuente 1,3 y modo oscuro; datos sintéticos identificados como tales.
- Revisión independiente de seis correcciones concretas: los materiales que desaparecen no se omiten del total, tarifa de diseño coherente, cantidades serializadas sin ambigüedad, contraste del sistema en oscuro, ancho legible del formulario y avisos de exportación visibles. Veredicto **ship** limitado a esas seis correcciones.
- La captura inicial oscura final acredita hora e iconos claros desde el inicio. El retorno desde un diálogo tiene evidencia de un pase anterior y comprobación del indicador actual; la última captura etiquetada como retorno aún muestra el diálogo y no se utiliza para afirmar validación visual del retorno en ese pase.
- Perfil JSON privado comparado con el bloque de configuración del documento original: contenido equivalente, repeticiones preservadas. No se incorporan datos de un encargo ni se publica el perfil en el fork.

## Artefactos finales

ZIP de APK de Actions: SHA256 `6cae884964d7547d09bce5cb170d4853622ebbeae90011711c7805364533716e`.

APK extraída: SHA256 `dd8bad4db762cda1bcd024c5e33968adedcf7b8dc188a3a99ce5e975405068a2`.

ZIP de informes de protocolo/economía/lint: SHA256 `93a9ca7ca00db0fb772091893684d522d8dd03153bf54eafb46cc193ed0da767`.

ZIP de informes y capturas Android: SHA256 `2c04039ca4bf456ba47b6764c05d010a1cd507f0e6ce9a22bc4431bb0954785b`.

Los ZIP descargados se contrastaron con sus digest de GitHub antes de extraerlos.

## Límites prácticos

No se ha instalado en la Pad 6 ni realizado operaciones físicas sobre la impresora durante esta validación. La corrección anterior del cierre de sesión al medir malla sigue pendiente de prueba en la máquina del usuario. No se cambia la cantidad de puntos del firmware.

El perfil se conserva localmente y puede exportarse; esto no equivale a una copia completa de toda la aplicación. El cálculo de un trabajo sigue siendo provisional: sin historial de ofertas enviadas/aceptadas, PDF, inventario real, pedidos o cobros.

La firma de desarrollo sigue siendo temporal. Android puede requerir desinstalar la versión anterior, con pérdida de datos locales; exportar antes lo que deba conservarse. No se ha creado ni subido material privado de firma.
