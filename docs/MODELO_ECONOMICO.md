# Modelo económico de presupuestos

Referencia funcional: documento privado `MODELO_PRESUPUESTOS_Y_BENEFICIOS.md`, versión 1.0, revisado por el usuario el 15 de octubre de 2026. Sus tarifas e históricos se entregan en un perfil JSON privado fuera del repositorio público. Esta página documenta reglas; no publica esos valores.

## Regla que sustituye la propuesta anterior

El modelo básico descuenta materiales consumidos. Diseño, impresión y montaje son partidas del precio cobrado por el trabajo propio: **no se vuelven a descontar como gastos**. No se crean gastos automáticos de sueldo propio, alquiler de máquina, amortización, desgaste o mantenimiento a partir de tarifas de venta.

Otros gastos son opcionales. Registrar un gasto y repercutirlo al cliente son decisiones separadas: un desembolso no repercutido reduce el resultado, pero no desaparece del cálculo. La electricidad importada es una estimación configurable, no una medición. No se suma a la vez una estimación y una lectura de energía.

## Fórmulas y redondeo

Con aritmética decimal y redondeo HALF_UP:

```text
precio_material_base = media_simple(observaciones_de_precio) / cantidad_base_por_compra
materiales = redondear_centimos(suma(cantidad_usada × precio_material_base))
diseño = redondear_centimos(minutos_diseño / 60 × tarifa_diseño)
impresión = redondear_centimos(minutos_impresión / 60 × tarifa_impresión)
montaje = redondear_centimos(minutos_montaje / 60 × tarifa_montaje)
electricidad = redondear_centimos(W × minutos_impresión / 60000 × precio_kWh)
base = materiales + diseño + impresión + montaje + gastos_repercutidos
precio_calculado_sin_impuestos = redondear_centimos(base / (1 − margen_adicional))
venta_sin_impuestos = precio_acordado_manual, si existe; si no, precio_calculado
impuestos = redondear_centimos(venta_sin_impuestos × porcentaje_configurado)
total_cliente = venta_sin_impuestos + impuestos
gastos = materiales + electricidad_registrada + otros_gastos_registrados
beneficio_estimado_del_trabajo = venta_sin_impuestos − gastos
```

El margen comercial adicional no representa todo el beneficio. Los impuestos añadidos al cliente no se incluyen en el ingreso usado para este resultado. El porcentaje es una configuración introducida por el usuario, no una determinación del tratamiento fiscal aplicable.

Los precios por gramo/metro conservan precisión interna. El subtotal de materiales se redondea después de sumar líneas, no cada línea antes de sumarla. Cada partida de trabajo se cierra a céntimos; la energía se acumula y redondea una sola vez por trabajo. Margen válido: 0 ≤ m < 1. Valores negativos y divisores no positivos se rechazan.

La remuneración por hora activa divide el beneficio estimado entre horas de diseño y montaje; no añade las horas de impresión automática. El motor admite tiempo activo real separado cuando esté disponible. Un denominador cero deja el indicador no disponible. Un dato pendiente no se convierte en cero.

## Catálogo y unidades

Preservar cada observación de precio, incluidas repeticiones. No deducir fechas, orden cronológico de compras o cantidades de existencias a partir de la lista. La media inicial es aritmética simple; no se presenta como ponderada.

Cada material define una unidad base y una cantidad por formato de compra: gramos por bobina, metros por rollo o unidades por pack. Confirmar el formato al editar un precio. Un sobrante aprovechable no se imputa entero al trabajo.

El motor permite un precio real por unidad de lote como sustitución explícita. La interfaz inicial usa la media histórica; la selección de lote/consumo real se añadirá con inventario. Las compras y los consumos siguen siendo registros diferentes; este catálogo no crea ninguno automáticamente.

## Alcance implementado en 0.3

- Módulo Kotlin `:pricing` independiente de Android, Compose y USB, con pruebas de las reglas económicas.
- Área Presupuestos con importación/exportación privada del modelo, edición de tarifas y catálogo, cálculo provisional del lote y vista/texto de cliente con concepto y total final.
- Perfil importado persistente en la tablet. Las cantidades y los tiempos del trabajo empiezan pendientes; la dificultad propone sus minutos solo al seleccionarla.
- Datos del cálculo conservados ante rotación y cambio de pestaña; aún no existe un historial persistente de presupuestos enviados/aceptados. No confiar en el borrador como registro de un pedido. Cambiar el modelo recalcula expresamente el borrador abierto.
- No hay PDF, pedidos, inventario real, cobros ni informes de caja en esta entrega. La separación de presupuestos por revisiones permanece en F04b antes de guardar ofertas enviadas.

La importación muestra un resumen y requiere confirmar la sustitución. Un perfil incompatible no reemplaza el actual. Exportar el perfil permite conservar sus tarifas y catálogo; no es una copia de toda la app.

## Privacidad y procedencia

El documento original no se modifica. El JSON privado se extrae de su bloque de configuración; no contiene consumos ni tiempos de un encargo. Sus importes no se incluyen en el código, pruebas, documentación ni artefactos públicos del fork. Las pruebas y capturas utilizan datos sintéticos identificados como tales.

Una importación no demuestra precios actuales de mercado ni obliga a usar una tarifa. Las preferencias y materiales siguen siendo editables. La salida de cliente recibe exclusivamente concepto y total; no tiene acceso a tarifas, proveedores, materiales o beneficio.
