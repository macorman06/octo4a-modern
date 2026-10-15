# UI de Presupuestos · extensión nativa 0.3

Fecha: 15 de octubre de 2026. Modo de uso: **Operate**, para calcular y revisar un trabajo desde la tablet.

Este documento registra la extensión implementada en la app Android existente. No establece una nueva identidad visual ni un sistema de diseño aprobado. No había `PRODUCT.md` ni `DESIGN.md` en la raíz y esta entrega no los crea. La referencia visual sigue siendo la implementación nativa de Jesper 3D.

## Alcance implementado

La entrada **Presupuestos** convive con **Impresora** y abre tres pestañas:

- **Calcular:** borrador del lote completo, materiales, tiempos y tarifas, gastos opcionales, precio acordado, detalle interno y vista para cliente.
- **Modelo:** edición de tarifas de diseño, impresión y montaje, margen comercial, impuestos configurados y estimación de electricidad.
- **Materiales:** catálogo de precios de referencia, formato de compra e histórico de observaciones, incluidas las repetidas.

El cálculo funciona sin conexión USB ni internet. Al existir una sesión USB, se mantienen el aviso de sesión activa y la acción **Parada**; **Impresora** permite volver al control existente.

## Identidad preservada

La superficie usa Jetpack Compose y los componentes Material 3 del tema compartido `JesperTheme`, sin introducir una apariencia web ni sustituir la navegación de la impresora. Se conservan:

- El verde principal en claro (`#236A56`) y su variante en oscuro (`#97D5C4`), con fondos claros cálidos o verde oscuro según el sistema.
- La tipografía de Material 3 mediante sus roles de título, cuerpo y etiqueta; no se incorpora otra familia tipográfica.
- Las formas compartidas de 8, 12 y 18 dp, campos delineados, botones nativos, pestañas, chips, divisores y diálogos.
- La jerarquía funcional: títulos de sección, ayuda junto al dato y detalle monetario interno. El color de error se reserva a errores y a la parada existente.

No se añaden conceptos de marca, ilustraciones, nuevos tokens globales ni aprobaciones visuales implícitas. Los valores anteriores describen el código existente y no constituyen una nueva fuente de tokens.

## Distribución y adaptación

La cabecera, la importación/exportación y las pestañas ocupan el ancho disponible. El formulario se centra y limita su ancho a **880 dp**, con 20 dp de relleno y 18 dp entre bloques. En compacto utiliza el ancho disponible y desplazamiento vertical; las acciones de importación/exportación pueden saltar de línea.

`safeDrawingPadding` e `imePadding` protegen el contenido frente a las barras del sistema y al teclado. Los diálogos de edición permiten desplazarse por su contenido. El formulario conserva el borrador al cambiar de pestaña o recrearse la actividad mediante estado guardable; esto no equivale a guardar un presupuesto enviado.

Los controles conservan los componentes interactivos Material 3 y su referencia nativa de **48 dp** para el área de interacción. No se introducen alturas que reduzcan esa referencia. No se ha medido individualmente cada área táctil en esta revisión documental.

La prueba nativa contempla pantalla de tablet y una configuración compacta de 432 × 960, modo oscuro y **escala de fuente 1,3**. Esa resolución es la configuración de prueba, no una medida del dispositivo físico del usuario. Las capturas existentes muestran el formulario centrado en tablet y los campos y textos adaptados a compacto.

`JesperTheme` sigue el modo claro/oscuro del sistema. El código actual publica la actualización de los iconos después de la composición, y el recurso nativo `values-night/styles.xml` fija el contraste oscuro desde la creación de la ventana. La captura inicial oscura final confirma hora e iconos claros sin tener que abrir un diálogo.

## Contrato de los formularios

Un campo vacío representa un dato pendiente; introducir `0` declara que la tarea o consumo no existe. Los números aceptan coma o punto decimal y el teclado solicita entrada decimal. El cálculo permanece pendiente si falta un tiempo, un precio o la revisión explícita de materiales, incluso cuando no se han añadido materiales.

Al guardar o importar un modelo, el borrador se recalcula y exige revisar los materiales de nuevo. Si un material desaparece del modelo, su línea permanece visible como incidencia y bloquea el resultado completo hasta resolverla. La tarifa del perfil de diseño seleccionado se actualiza con el modelo.

Los precios de materiales son referencias: la media simple conserva cada observación, convierte el formato de compra a su unidad base y no deduce fechas, compras ni existencias. La electricidad es una estimación configurable, no una medición.

## Cálculo y separación de datos

El motor usa `BigDecimal`, precisión decimal y redondeo monetario a céntimos. Diseño, impresión y montaje son importes cobrados por el trabajo propio. Los materiales y los gastos opcionales registrados se descuentan una sola vez del ingreso sin impuestos; el trabajo propio no vuelve a descontarse como gasto. Repercutir un gasto al cliente y registrarlo como gasto son decisiones separadas.

La pantalla interna puede mostrar materiales, tarifas, importes de trabajo, gastos, margen, ajuste comercial y beneficio estimado. El texto explica que este beneficio incluye la remuneración propia y no contempla gastos sin registrar ni impuestos personales. El tiempo activo comprende diseño y montaje; no incluye la impresión automática.

Hay dos salidas deliberadamente distintas:

- **Modelo privado JSON:** contiene tarifas y observaciones de precios. Se importa con el selector de documentos de Android, se valida antes de sustituir el modelo y se guarda localmente en las preferencias privadas de la app. La exportación se identifica como privada y utiliza el selector de destino de Android.
- **Vista y texto para cliente:** se construyen desde una proyección con solo concepto y precio final total. No reciben materiales, tarifas, gastos, márgenes ni beneficio. La exportación pública actual es texto, no PDF.

Este documento no reproduce el perfil privado, sus tarifas ni el documento económico privado. Las pruebas y capturas citadas utilizan datos sintéticos.

## Límites de esta versión

El cálculo es provisional. No hay presupuestos enviados inmutables, historial de presupuestos, pedidos, PDF, inventario, movimientos de stock ni compras automáticas. Exportar una vista de texto no crea ninguno de esos registros. Los cambios en el modelo pueden cambiar el borrador abierto; no existe una instantánea comercial cerrada.

## Evidencia y verificación pendiente

Se ha revisado el código de [BusinessUi.kt](../tablet/app/src/main/java/com/jesper3d/tablet/business/BusinessUi.kt), el tema y la entrada al módulo en [PrinterUi.kt](../tablet/app/src/main/java/com/jesper3d/tablet/PrinterUi.kt), los selectores y la confirmación de importación en [MainActivity.kt](../tablet/app/src/main/java/com/jesper3d/tablet/MainActivity.kt), la validación y persistencia en [PricingStore.kt](../tablet/app/src/main/java/com/jesper3d/tablet/business/PricingStore.kt) y el contrato del motor en [Pricing.kt](../tablet/pricing/src/main/kotlin/com/jesper3d/pricing/Pricing.kt).

Las pruebas existentes [PricingTest.kt](../tablet/pricing/src/test/kotlin/com/jesper3d/pricing/PricingTest.kt) y [BusinessTest.kt](../tablet/app/src/androidTest/java/com/jesper3d/tablet/BusinessTest.kt) cubren reglas decimales, descuento único, valores pendientes, proyección pública, persistencia del perfil, sustitución del modelo y adaptación nativa. La lectura de estas pruebas acredita su alcance, no el resultado del último pase.

Las capturas finales proceden de `entrega-jesper3d-0.3/ui-verified/outputs/connected_android_test_additional_output/debugAndroidTest/connected/emulator-5554 - 15`, fuera del repositorio, con datos de prueba. La captura inicial oscura acredita el contraste corregido. La última captura etiquetada como retorno desde diálogo no acredita visualmente ese retorno porque aún muestra el diálogo; no se usa para sostener esa afirmación. Hay una captura válida del retorno de un pase anterior y comprobación del indicador actual.

El pase final de [validación 0.3](../VALIDACION_0.3.md) ha superado 35 pruebas de protocolo, 22 económicas y 10 Android, sin fallos ni omisiones. La revisión independiente dio por resueltas sus seis correcciones, con la limitación de captura anterior explicitada. No se declara validación del dispositivo físico del usuario ni del comportamiento USB con su impresora a partir de estas capturas. Al ser una superficie Android nativa, no se ha utilizado un detector web.
