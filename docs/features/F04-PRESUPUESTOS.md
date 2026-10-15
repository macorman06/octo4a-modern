# F04 — Presupuestos internos y para cliente

Estado: especificación propuesta, pendiente de implementar. Dependencias: F02 y motor de costes F03. Integración con pedidos al incorporar F05.

## Resultado esperado

Preparar una oferta sabiendo cómo se ha calculado y qué falta por conocer. El propietario ve el detalle; el cliente ve por defecto el concepto o referencia y el precio final total. El precio que se muestra al cliente coincide exactamente con la revisión guardada.

## Recorrido

1. Crear presupuesto con concepto, cantidad y cliente/alias opcional.
2. Añadir material, impresión y trabajo previsto. Elegir tarifas conocidas o dejar explícitamente una partida pendiente.
3. Revisar el cálculo interno. Introducir precio final o usar una sugerencia basada en un margen objetivo cuando el coste esté completo.
4. Ver la salida para cliente y sus campos públicos seleccionados.
5. Guardar una revisión y exportar PDF o copiar texto para enviar manualmente.
6. Marcar Enviado cuando se haya enviado de verdad; generar o copiar el documento no demuestra envío.
7. Registrar aceptación, rechazo o sustitución. En F05, convertir una revisión aceptada en pedido una sola vez.

## Vista interna

Agrupar sin esconder ninguna partida:

- Alcance: cantidad, variantes y descripción; coste fijo del lote separado de coste por unidad.
- Materiales: bobina/lote o tarifa de referencia, gramos estimados, soportes, purga y otros residuos. Indicar si el total del laminador ya los incluye.
- Máquina: duración estimada y tarifa opcional por hora; definir si es únicamente desgaste/mantenimiento o incluye otras partidas.
- Energía: potencia media y tarifa con procedencia/fecha o energía medida de referencia. Siempre estimada para el trabajo futuro, aunque se base en una medición anterior.
- Trabajo: diseño, preparación, montaje/acabado y sus tiempos/tarifas.
- Otros: consumibles, embalaje, servicios, transporte, comisiones y contingencia opcional.
- Resultado: coste total previsto, coste unitario previsto, precio final total, margen sobre venta y recargo sobre coste diferenciados.
- Calidad del cálculo: campos pendientes, hipótesis y fecha/versión de tarifas. No mostrar un margen completo si el coste solo es parcial.
- Notas privadas y referencia al archivo/modelo; no se copian automáticamente a la salida de cliente.

No rellenar material, potencia, precio de electricidad o trabajo con valores supuestos silenciosamente. Cero puede ser una decisión explícita —por ejemplo, no cobrar diseño—, pero no sustituye a un dato desconocido.

El usuario puede decidir un precio manual aunque falten costes. La vista interna conserva la advertencia de rentabilidad incompleta; la salida al cliente sigue pudiendo mostrar el precio final acordado sin exponer la advertencia interna.

## Salida para cliente

Modelo público separado mediante una lista permitida de campos. Predeterminado:

- Referencia/concepto público del presupuesto.
- Precio final total y moneda EUR.

Descripción pública, cantidad y plazo/validez pueden habilitarse expresamente. No se muestra un desglose por materiales, impresión, mano de obra, margen o proveedor. Tampoco se muestra un precio unitario si el usuario solo quiere comunicar el total.

La salida no contiene notas privadas, IDs de compras, coste de bobinas, rutas internas de archivos ni datos financieros ocultos en metadatos, capas del PDF o propiedades del documento. El renderizador de cliente recibe únicamente el modelo público y no tiene acceso al presupuesto interno completo.

Previsualización y exportación usan la misma revisión y selección de campos. Las acciones se denominan «Vista para cliente», «Copiar texto para cliente» y «Exportar PDF para cliente». Exportar el detalle propio es otra acción claramente identificada como interna.

Esta salida es un presupuesto, no una factura fiscal. No se añaden ni calculan impuestos de forma automática en esta primera función; la configuración comercial necesaria para ello se definirá antes de ampliar ese alcance.

## Versiones y estados

Un presupuesto tiene un identificador estable y revisiones numeradas. El borrador es editable. Al marcarlo como enviado, la revisión queda congelada con concepto, cantidades, tarifas, partidas, completitud y precio final. Una modificación posterior crea otra revisión; la anterior queda accesible como enviada o sustituida.

Estados comerciales: Borrador, Enviado, Aceptado, Rechazado, Caducado y Sustituido. La caducidad debe ser visible; no borrar una oferta anterior al cambiar la fecha.

F04 conserva aceptación sin necesidad de inventar un pedido antes de F05. Al incorporar F05, una revisión aceptada puede convertirse en pedido con relación única a esa revisión. Una segunda pulsación abre el pedido ya creado. Modificar la oferta después no cambia un pedido confirmado sin una acción explícita de revisión comercial.

Si la aceptación difiere del importe enviado, crear la revisión que refleja el acuerdo antes de convertirla. No cambiar silenciosamente una revisión ya enviada.

## Reglas de cálculo

- Tarifa por gramo derivada del coste del lote y su masa útil conocida; conservar el origen. Si se asignan portes al lote, no volver a añadir esos mismos portes al coste de material.
- Distinguir cantidades por pieza y por lote. Multiplicar por unidades solo las partidas por unidad.
- Un total de gramos que ya contiene soportes/merma no recibe otro suplemento implícito por esos mismos gramos.
- Una contingencia es una reserva prevista explícita, no material realmente consumido.
- Separar coste y precio. Margen sobre venta y recargo sobre coste llevan nombres y fórmulas distintas.
- Valores monetarios decimales exactos y regla de redondeo documentada. El total mostrado coincide con la suma de partidas mostradas y con el total público.
- No dividir por cero al calcular margen/coste unitario. Exigir cantidad positiva para un presupuesto de piezas y precio final no negativo. Permitir un precio cero explícito para una muestra, con margen sobre venta sin definir.
- Coste pendiente produce estado incompleto, no una partida de cero euros.

## Casos de aceptación

Los siguientes importes son datos de prueba, no costes de la impresora del usuario.

1. Coste previsto 12 EUR y precio final 20 EUR: diferencia 8 EUR, margen sobre venta 40 % y recargo sobre coste aproximadamente 66,67 %. Los dos porcentajes no se confunden.
2. Dos piezas con coste variable de 3 EUR cada una y preparación única de 4 EUR: coste total 10 EUR. La preparación no se multiplica otra vez por dos.
3. Material pendiente de precio: el coste aparece incompleto; introducir venta de 20 EUR permite generar una salida pública de 20 EUR sin afirmar un margen completo.
4. Presupuesto enviado con una tarifa concreta: cambiar la tarifa de inventario no cambia ese presupuesto. Editar crea una revisión nueva.
5. La vista propia contiene una nota privada y un margen; la salida PDF/texto para cliente no contiene ninguno, ni en contenido oculto ni en metadatos. La prueba inspecciona el archivo generado además de verlo.
6. El total de cliente coincide al céntimo con la revisión interna seleccionada, incluidas cantidades y redondeos.
7. Copiar o exportar no marca automáticamente Enviado. No se contacta con Instagram, WhatsApp ni el cliente sin una acción de envío fuera de esta función.
8. En F05, aceptar/convertir dos veces la misma revisión crea un único pedido.
9. Cerrar y abrir la app y restaurar una copia conserva revisiones, datos privados y selección pública, manteniendo su separación.

## Entregas pequeñas

**F04a:** editor interno, motor de coste, precio final, borrador persistente y vista pública. Recorrido utilizable para calcular sin exportar todavía un documento.

**F04b:** revisiones enviadas, estados, texto y PDF para cliente, exportación interna diferenciada y comprobación del contenido generado. F04 se considera completa cuando ambas están verificadas.

## Ampliaciones posteriores

Plantillas visuales, identidad de marca, productos/variantes de catálogo, adjuntos, varias monedas y configuración fiscal si se solicita. La marca o el formato no cambian la regla de que los datos privados nunca se pasan al generador de documentos para cliente.
