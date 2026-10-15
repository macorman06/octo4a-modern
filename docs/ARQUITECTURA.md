# Datos y arquitectura

Estado: diseño objetivo, no estructura ya implementada. Se aplica por incrementos de la [hoja de ruta](HOJA_DE_RUTA.md).

## Punto de partida comprobado

- `tablet/:protocol` interpreta Marlin y define operaciones, mallas e historial.
- `tablet/:app` contiene interfaz, permisos, controlador, transporte USB y servicio Android.
- Perfil e historial de mallas se conservan actualmente en preferencias; el diagnóstico, en un archivo local acotado.
- La APK 0.2 no tiene base de datos de clientes, pedidos, inventario ni impresiones.
- El manifiesto declara `allowBackup=false`. No existe hoy una copia completa exportable/restaurable de los datos de la aplicación.
- La firma de las compilaciones de desarrollo es temporal; una actualización puede requerir desinstalación y borrar datos locales. Una estrategia de firma estable y una copia restaurable son necesarias antes del uso continuado con datos de negocio. La autorización para crear y subir material privado de firma sigue pendiente; este plan no la da por concedida.

## Separación de responsabilidades

Un módulo funcional tiene sus reglas, datos y pantallas. No necesita convertirse inmediatamente en un módulo Gradle distinto si todavía es pequeño, pero sus dependencias deben estar separadas desde su primera función.

- **App:** navegación, composición de dependencias y coordinación entre áreas. Mantiene una sola sesión de impresora.
- **Dominio compartido:** identificadores, unidades, importes, fechas y contratos. Kotlin sin Android, USB ni Compose.
- **Datos locales:** repositorios por dominio, persistencia y transacciones; las pantallas no acceden directamente a tablas.
- **Impresora:** servicio, transporte, controlador y pantallas de calibración. Es la única parte autorizada a enviar G-code.
- **Producción:** trabajos, intentos, consumos y resultado confirmado; funciona sin USB.
- **Inventario:** bobinas, artículos, lotes y movimientos. Las recepciones y consumos pasan por su contrato, no por edición externa de existencias.
- **Compras:** enlaces, proveedores, compras y recepciones; solicita movimientos de inventario identificables e idempotentes.
- **Presupuestos y costes:** cálculo determinista y versiones de presupuesto. No consulta precios en internet ni envía mensajes.
- **Ventas:** contactos, pedidos, cobros e interacciones.
- **Informes:** lectura agregada y trazable; no duplica registros editables.
- **Mejoras:** captura y seguimiento local de ideas y fallos; no depende del controlador USB.

En F00 se extrae el área de impresora y se crea la base compartida mínima. En F01 se añade Mejoras. Los demás módulos se crean al implementar su primera función; no se generan módulos vacíos para todo el plan. Los cálculos de costes se separan en Kotlin puro cuando se incorpore F03. Informes puede empezar como parte de Taller y separarse físicamente cuando lo justifique su tamaño.

El mediador de la app y los contratos de repositorio coordinan los casos que cruzan dominios. No hay referencias entre pantallas de distintos módulos ni dependencias circulares. Navegación por identificadores, no pasando copias mutables de pedidos o de clientes.

## Persistencia y conservación

Propuesta: base SQLite mediante Room para los registros estructurados. Mantener preferencias para opciones pequeñas y archivos internos para adjuntos y diagnóstico. Elegir versiones compatibles al implementar, sin actualizar el conjunto de dependencias por esta documentación.

- IDs estables y fecha de creación/modificación. Dinero en unidades menores enteras o representación decimal exacta; no `Float`/`Double` para cálculos monetarios.
- Gramos, horas, energía y porcentajes con unidades explícitas. Almacenar masa/tiempo en unidades pequeñas enteras cuando corresponda y usar aritmética decimal en las tarifas.
- Instantes almacenados de forma inequívoca; presentación y periodos según zona horaria del usuario. No asumir que todos los días duran 24 horas al agrupar por fecha.
- Toda cantidad derivada conserva la procedencia: introducida manualmente, estimada por laminador, observada por firmware o medida. Un valor desconocido es nulo, no cero.
- Cambios de esquema con migraciones explícitas y comprobadas; sin recreación destructiva de la base como solución de actualización.
- Importar el perfil y el historial actuales sin borrarlos ni marcar como confirmado lo que antes era desconocido. No hace falta migrarlos todos a Room si una envoltura de exportación conserva correctamente su formato existente.
- Archivo completo versionado para exportar y restaurar: manifiesto, registros y adjuntos que ya estén soportados, además de perfil, mallas y diagnóstico. El CSV es una salida de consulta, no una copia completa.
- Obtener una instantánea consistente para exportar. No copiar solamente el archivo SQLite mientras pueda haber transacciones pendientes.
- Importación con validación, resumen previo, copia del estado actual y sustitución atómica. En la primera versión se restaura una copia completa; no se promete mezclar dos bases. Deshabilitar la restauración durante una sesión USB o una operación activa.
- Rechazar versiones de copia no compatibles, referencias rotas y archivos corruptos sin modificar los datos actuales. Comprobar ida y vuelta después de cerrar y volver a abrir la app.
- Exportar mediante el selector de documentos Android a un destino elegido por el usuario. Sin nube, cuentas o sincronización propias en la primera versión.
- Una copia con datos de clientes se considera privada. La app no la incluye automáticamente en diagnósticos públicos ni en incidencias de desarrollo.

## Modelo de datos mínimo por incorporación

No crear todas estas entidades en F00; esta lista fija nombres y relaciones para evitar duplicaciones al crecer.

- `PrinterProfile`: identidad y perfil; una máquina inicialmente, con ID para no acoplar todos los trabajos al nombre CR-10 S4.
- `Improvement`: tipo, área, prioridad, estado, texto, contexto permitido y versión de resolución.
- `InventoryItem`: material/consumible/repuesto/herramienta y unidad.
- `StockLot` / `Spool`: procedencia, coste de adquisición y cantidad inicial; una bobina puede ser una unidad individual de un lote.
- `StockMovement`: alta, recepción, consumo, ajuste o baja; cantidad, unidad, motivo y referencia de origen única.
- `SavedLink`: URL, categoría, notas y precio visto opcional con moneda y fecha.
- `Purchase` / `PurchaseLine` / `Receipt`: compromiso de compra, importes y recepción real. El recibido puede ser menor que lo comprado.
- `PrintAttempt`: un intento físico, origen SD/manual, impresora, fechas, duración conocida, estado, cantidad prevista y unidades válidas.
- `MaterialConsumption`: intento, bobina/lote, gramos y procedencia; también permite consumos de pruebas sin pedido.
- `CostSnapshot`: tarifas y reglas utilizadas, partidas, completitud y fecha; mantiene separadas previsión y realización.
- `Quote` / `QuoteRevision` / `QuoteLine`: identidad comercial, versión inmutable al enviar y cantidades ofertadas. Conserva internamente el coste y el precio final acordado por versión.
- `Customer`: contacto mínimo y canal, no necesariamente una agenda completa.
- `Order` / `OrderLine`: compromiso, cantidades, precio y versión de presupuesto opcional. Permite un pedido sin presupuesto previo.
- `ProductionAllocation`: asignación de un intento a una línea de pedido. Al principio un intento pertenece como máximo a una línea; producción compartida se habilita después con cantidades y reparto de coste explícitos.
- `Payment`: cobro o devolución con fecha y referencia al pedido. No se infiere del estado comercial.
- `Interaction` / `FollowUp`: resumen manual, canal, próxima acción y vencimiento.
- `ContentTask`: idea/fotos/preparado/publicado, referencia de producto/pedido opcional y enlace de publicación.
- `Product`: catálogo reutilizable cuando sea necesario; inicialmente los presupuestos y pedidos pueden usar descripciones libres.

Relaciones importantes: un cliente tiene varios pedidos; un presupuesto tiene varias revisiones; un pedido puede referir a una revisión; una línea de pedido puede tener varios intentos; un intento puede consumir varios materiales; un lote tiene muchos movimientos. Las tarifas son datos del momento, no referencias vivas que reescriben costes antiguos.

## Costes sin resultados engañosos

**Actualización del modelo:** prevalece [MODELO_ECONOMICO.md](MODELO_ECONOMICO.md). Diseño, impresión y montaje propios son importes de venta, no gastos a descontar. El cálculo de una tarifa cobrada no crea un gasto de máquina o trabajo. La versión 0.3 incorpora este motor independiente y el perfil privado; inventario y pedidos siguen pendientes.

Separar **previsto**, **registrado como real** y **pendiente**. El dato real también puede haber sido introducido manualmente; el origen y la calidad son campos diferentes.

Partidas previstas para incorporar de forma gradual:

1. Material: gramos totales consumidos × coste por gramo del lote. Registrar soportes y residuos; evitar sumarlos otra vez si ya estaban incluidos en el peso total.
2. Energía estimada: potencia media configurada en kW × horas × tarifa por kWh. Una medición de energía real sustituye esa estimación; no se suma encima. La potencia nominal de la fuente no es una lectura de consumo.
3. Impresión: horas × tarifa de venta. Es un importe cobrado, no un gasto automático de máquina, desgaste o mantenimiento.
4. Trabajo propio: diseño y montaje por tiempo y tarifa de venta, separados de gastos externos; distinguir partidas por unidad y por lote.
5. Consumibles, embalaje, servicios externos, transporte y comisiones, cuando se conozcan.
6. Contingencia prevista opcional, visible como hipótesis del presupuesto. En realización se contabilizan los fallos de verdad; no se les suma automáticamente una segunda reserva por fallos.

Coste de pedido = suma de los costes asignados de todos los intentos, incluidos fallidos, más sus partidas de preparación/entrega. Los prototipos no asignados no se cargan a un cliente de forma implícita.

Precio de venta y coste son campos distintos. Un precio final puede fijarse manualmente con coste aún incompleto; la app permite preparar el presupuesto pero indica internamente que la rentabilidad no está completamente calculada.

El margen comercial adicional se aplica a materiales más importes por trabajo y gastos repercutidos: base / (1 − margen_adicional). No se confunde con el margen efectivo del beneficio. Beneficio estimado del trabajo = venta sin impuestos − materiales − gastos adicionales registrados, sin restar otra vez el trabajo propio. La remuneración por hora activa excluye la impresión automática. Ver fórmulas y redondeo en el modelo actualizado.

Trabajar en EUR al principio. No agregar otras monedas sin conversión explícita. Definir y probar redondeo decimal al introducir el motor: cálculo con precisión suficiente, redondeo de las partidas mostradas a céntimos y total igual a la suma de esas partidas. Preservar tarifas y reglas con versión en cada presupuesto enviado y cierre de coste; una corrección posterior es explícita y queda registrada.

Los informes distinguen gasto de compra, valor del material consumido, venta acordada y dinero cobrado. No restar a la vez la compra de una bobina y su consumo como si fueran dos costes de producción. Mostrar «beneficio estimado del trabajo», que incluye la remuneración propia y no es resultado después de impuestos personales o gastos no registrados. El porcentaje de impuestos configurable solo aplica el modelo introducido por el usuario; no determina obligaciones tributarias ni genera facturas fiscales.

## Presupuesto interno y salida para cliente

Dos modelos distintos, no dos estilos de la misma pantalla:

- `InternalQuote`: todas las partidas, hipótesis, tarifas, notas, margen y precio final.
- `CustomerQuote`: lista explícita de campos públicos; por defecto referencia/concepto y total final con moneda. Los opcionales públicos se seleccionan de forma consciente.

La conversión al modelo para cliente no copia costes, margen, proveedores ni notas privadas. Texto, PDF, vista previa, metadatos y exportación de cliente reciben únicamente `CustomerQuote`. Ocultar datos con estilos o con pestañas no basta. El detalle interno se exporta mediante una acción aparte y claramente identificada.

Las revisiones enviadas quedan congeladas. Editar crea otra revisión; aceptar dos veces la misma revisión no crea dos pedidos. La salida para cliente corresponde a una revisión concreta, con el mismo total que se ve internamente.

## Qué puede saberse al imprimir desde SD

La base será el registro manual y la confirmación del resultado. La detección automática se incorpora después, como observación complementaria y con hardware validado.

- `M27` puede comunicar posición en bytes y nombre de archivo SD según soporte. Porcentaje de bytes leídos no es porcentaje de tiempo transcurrido.
- `M31` puede comunicar el tiempo del trabajo que conoce Marlin; el temporizador SD empieza al iniciar la impresión desde SD.
- `M78` depende de `PRINTCOUNTER` y aporta estadísticas del firmware cuando esté disponible. No ofrece por sí solo una relación fiable entre histórico, clientes y pedidos.
- Firmware, reinicios, contadores reiniciados, conexión tardía y periodos sin tablet crean huecos. Un contador acumulado se conserva como observación separada; no se suma como si fueran nuevas impresiones detalladas.
- «Not SD printing», ausencia de respuesta, desconexión USB o `ok` a una consulta no prueban que una pieza se haya fabricado bien. Confirmar resultado y cantidad válida.
- Un archivo puede imprimirse varias veces; su nombre no sirve como ID único del intento ni como asociación automática al pedido.
- Ver una temperatura alta o un movimiento no inicia por sí solo un trabajo de producción.
- No conectar a mitad de impresión hasta verificar en esa placa que abrir el puerto no reinicia la impresora. La app actual abre con DTR y está documentada para conexión en reposo; F09 debe validar y resolver ese comportamiento antes de ofrecer seguimiento SD.
- La observación no envía órdenes de inicio/parada de temporizador, reinicio de contadores ni reanudación de impresión.

El tiempo de calendario entre dos pulsaciones se etiqueta como duración manual/estimada si no se conoce la impresión efectiva y sus pausas. La tablet puede estar apagada mientras la SD sigue imprimiendo; el historial de la app no se presenta como toda la vida útil de la máquina.

## Comprobaciones por riesgo

- Mantener las pruebas del protocolo y controlador al extraer el módulo de impresora. Probar navegación mientras la sesión conserva su propietario único.
- Persistencia y copias: cierre/reapertura, migración desde 0.2 y exportación/restauración con datos relacionados.
- Inventario/compras: recepción repetida, recepción parcial, devolución, consumo de fallo y ajustes no duplican existencias.
- Costes: unidades, decimales, partidas por lote/unidad, fallos, nulos, redondeos y tarifas históricas.
- Presupuestos: el archivo para cliente no contiene datos privados en texto, contenido embebido ni metadatos; el detalle interno mantiene todas las partidas.
- Pedidos: aceptación repetida no duplica pedido; cancelación no borra consumos; cobros parciales y devoluciones no alteran el total acordado.
- Informes: totales reconciliables con registros de origen y cobertura de datos visible; cambios de periodo y zona horaria coherentes.
- SD: pérdida de USB, apertura tardía, reinicio y respuesta no soportada nunca inventan un trabajo finalizado.

## Fuentes y comprobación

Código local revisado el 15 de octubre de 2026: `tablet/settings.gradle.kts`, `PrinterController.kt`, `PrinterUi.kt`, `AndroidManifest.xml` y `tablet/README.md`. Las decisiones de separación y persistencia son propuestas para este proyecto, apoyadas en las guías oficiales de [módulos Android](https://developer.android.com/topic/modularization/patterns) y [Room](https://developer.android.com/training/data-storage/room).

Límites de observación consultados en documentación oficial de Marlin: [M27](https://marlinfw.org/docs/gcode/M027.html), [M31](https://marlinfw.org/docs/gcode/M031.html) y [M78](https://marlinfw.org/docs/gcode/M078.html). Su presencia en la documentación no demuestra que el firmware instalado los soporte; falta comprobarlo en la máquina.
