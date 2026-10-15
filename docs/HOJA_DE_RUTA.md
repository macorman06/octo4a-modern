# Hoja de ruta por funciones

Fecha: 15 de octubre de 2026. F00–F10 sigue siendo la secuencia de módulos completos. La petición posterior de incorporar el modelo económico adelanta en 0.3 un motor aislado, perfil privado y cálculo provisional de F03/F04a; no da por terminadas estas funciones ni las dependencias de inventario, copias completas y revisiones. No asignar fechas ficticias ni prometer todas las funciones para una única versión.

## Forma de trabajar

Una entrega tiene un recorrido de usuario completo, datos persistentes, criterios verificables y una nota de versión que distingue lo comprobado en código de la prueba física pendiente. Al iniciar cada función se concreta su ficha y al finalizar se registra qué se verificó. Se permite dividir una función grande en entregas menores conservando sus dependencias.

Antes de tocar comunicación física, validar la 0.2 en la CR-10 S4 con una etapa segura cada vez. La planificación y los módulos sin USB pueden avanzar mientras esa prueba está pendiente; no se marca la desconexión como resuelta físicamente.

## F00 — Base modular y conservación de datos

**Primera entrega propuesta.** Preparar navegación de áreas y separar el módulo actual de impresora de la gestión. Introducir repositorios y persistencia mínima para las próximas funciones, junto a exportación/restauración completa versionada.

Puede dividirse en F00a, extracción y navegación; F00b, persistencia y copia/restauración. F01 requiere ambas.

Se acepta cuando cambiar de pantalla no recrea la sesión USB, los controles actuales siguen funcionando, la migración conserva perfil/mallas y una copia restaurada reproduce los datos. Una copia corrupta no altera el estado actual. Resolver por un procedimiento autorizado la continuidad de firma antes de adoptar actualizaciones con datos de negocio; no crear ni subir secretos como parte implícita de este plan.

No incluye tablas vacías para todos los módulos, nube ni cambios del firmware. Mostrar en navegación solo áreas funcionales o indicar claramente su estado de preparación, sin pantallas con métricas inventadas.

## F01 — Mejoras de la app

Depende de F00. Crear, consultar y editar ideas, funciones y fallos con módulo, prioridad y estado. Búsqueda/filtros, notas y contexto opcional. [Ficha completa](features/F01-MEJORAS.md).

Se acepta cuando funciona sin USB ni internet, persiste tras cerrar, conserva filtros, se incluye en la copia y permite distinguir «por verificar» de «hecho». No publica nada en GitHub.

Sirve como primer recorrido pequeño de datos locales y como bandeja de necesidades reales del resto del desarrollo.

## F02 — Inventario inicial

Depende de F00. Bobinas, consumibles por unidad, altas, ajustes, bajas y consulta de movimientos. Precio de adquisición conocido o pendiente, ubicación y umbral opcional. Evitar existencias editadas sin motivo trazable.

Se acepta cuando una bobina admite consumo/ajuste sin perder el histórico, un peso medido se distingue de un resto estimado, el coste desconocido no se trata como gratis y la copia conserva cantidades y procedencia. Las compras podrán originar movimientos después, a través del mismo contrato.

No incluye reservas complejas, reparto de portes automático ni múltiples almacenes.

## F03 — Trabajos SD manuales y cálculo de costes

Depende de F02 para consumos reales. Registrar un intento sin tener un pedido: duración prevista/conocida, unidades, material y resultado. Tarifas de venta del trabajo propio separadas de materiales y gastos reales; ver [modelo económico](MODELO_ECONOMICO.md). En 0.3 se adelanta el motor sin registro de intentos. Los precios y potencia proceden del perfil explícito del usuario, con las estimaciones identificadas.

Se acepta cuando un intento fallido conserva gasto, el material se descuenta una sola vez, soportes no duplican gramos ya incluidos, cambiar tarifas no reescribe un coste cerrado y un trabajo sigue registrándose sin USB. Resumen básico de horas, gramos y coste disponible desde esta entrega.

No detecta automáticamente trabajos SD ni transmite archivos. La estimación de energía se identifica como tal.

## F04 — Presupuestos: detalle propio y salida para cliente

Depende de F02 y del motor de costes de F03; no requiere que exista ya un pedido. Borradores, cantidades, partidas internas, precio final libre o sugerido, revisiones y salida de cliente. [Ficha completa](features/F04-PRESUPUESTOS.md).

Dividir en F04a, cálculo/borrador y vista previa; F04b, revisiones enviadas, exportación PDF y texto para cliente. No marcar F04 completa antes de comprobar ambas salidas.

Se acepta cuando el total coincide entre la vista interna y la de cliente, el cliente solo recibe los campos públicos previstos, una revisión enviada conserva sus tarifas/precio y no existe envío automático. Coste incompleto no se presenta como rentabilidad confirmada.

## F05 — Clientes, pedidos y cobros

Depende de F04 para convertir presupuestos; permite también crear pedidos con precio acordado manualmente. Contactos mínimos, origen Instagram/WhatsApp, cantidades, fecha objetivo, asignación de intentos, entrega y pagos parciales.

Se acepta cuando aceptar dos veces no duplica pedidos, un reintento se acumula al coste del pedido, cancelar conserva consumos y un pedido entregado puede seguir teniendo saldo pendiente. Los estados de pago, producción y entrega se ven separados.

Inicialmente un intento se asigna a una línea de pedido. No simular un reparto entre varios pedidos hasta incorporar esa regla explícitamente.

## F06 — Enlaces, compras y recepción

Depende de F02. F06a puede implementarse de forma independiente antes si guardar enlaces resulta más urgente: categorías, notas y precio observado fechado. F06b añade proveedores, compras por líneas, importes reales y recepción en inventario.

Se acepta cuando guardar un enlace no aumenta stock, el precio histórico no parece una consulta actual, una recepción parcial solo añade lo recibido y repetirla no duplica existencias. Compra pagada y material consumido aparecen como conceptos distintos.

No compra ni paga en tiendas. Adjuntos y automatización de precios se concretan aparte.

## F07 — Informes del taller y del negocio

Depende de F03 y F05 para el alcance completo; los resúmenes de producción de F03 ya son utilizables antes. Uso registrado, material, fallos, costes, ventas, cobros, pendientes, comparación de presupuesto/real y margen sobre costes registrados. Gastos de compras cuando exista F06b.

Se acepta cuando los totales se pueden reconstruir desde trabajos/pedidos, se indica la cobertura, las compras no se cuentan dos veces y un periodo sin datos no se presenta como ausencia demostrada de actividad. Exportación CSV de consulta; informe compartible posterior si hace falta.

No se denomina beneficio neto a un cálculo con costes incompletos. No se ofrece contabilidad fiscal.

## F08 — Publicaciones, mensajes y seguimiento

Depende de F05 para vincular pedidos. Tareas de fotos/publicación, enlace publicado, resumen manual de conversaciones y próxima acción. Se podrá vincular contenido a un producto sin cliente cuando exista catálogo.

Se acepta cuando distinguir «preparado» y «publicado» es explícito, el usuario puede abrir su canal y revisar el texto, y publicar no cambia el estado del pedido. Los datos privados de clientes no se copian a publicaciones por defecto.

No importa conversaciones, envía mensajes ni publica automáticamente.

## F09 — Observación SD y mantenimiento asistido

Depende de F03 y de validación física del enlace. Comprobar capacidades reales, apertura USB sin reinicio y observaciones de estado/tiempo. Proponer asociación a un trabajo en lugar de asignar un archivo a un cliente por nombre.

Se acepta cuando desconectar y reconectar no marca éxito ni crea duplicados, un contador reiniciado no genera uso ficticio y las funciones no soportadas dejan disponible el registro manual. Una prueba específica debe demostrar que no se reinicia la placa al abrir el puerto en el modo ofrecido.

Es una ampliación; el negocio y los informes no dependen de tenerla.

## F10 — Ampliaciones a elegir por uso real

Catálogo reutilizable y variantes, fotografías/adjuntos, reservas de stock, reparto de una placa entre pedidos, importación de estimaciones del laminador, mantenimiento por horas registradas, plantillas, recordatorios locales y sincronización entre dispositivos. Cada una necesita su propia ficha antes de implementar.

La sincronización y las integraciones de redes no se introducen como requisito oculto del uso individual en la tablet.

## Necesidades iniciales para registrar en Mejoras

Estas entradas son contenido de planificación; aún no existen registros en una base de la app:

- **BUG-001:** desconexión o espera indefinida al finalizar malla. Área Impresora. Prioridad alta. Corrección en código 0.2, pendiente de probar en hardware; no marcar «Hecho» todavía.
- **IDEA-001:** inventario de bobinas y consumibles. F02.
- **IDEA-002:** registrar impresiones SD y coste de los intentos fallidos. F03.
- **IDEA-003:** presupuesto interno detallado y documento de cliente con precio final. F04.
- **IDEA-004:** pedidos, clientes y cobros desde Instagram/WhatsApp. F05.
- **IDEA-005:** enlaces de interés, compras y recepción de materiales. F06.
- **IDEA-006:** informes de uso, coste y margen con datos pendientes visibles. F07.
- **IDEA-007:** publicaciones y seguimientos pendientes. F08.
- **IDEA-008:** mantener los puntos de malla existentes; comprobar si la lectura confirma 11 × 11. No cambiar firmware ni parametrizar una cantidad inventada.

## Cierre de cada entrega

Registrar alcance, cambios, comprobaciones y límites. Entregar una APK solo cuando haya un incremento funcional compilado y verificado. Documentación o maquetas no se presentan como funciones instaladas. La prueba física pendiente se conserva como pendiente aunque pase el emulador.
