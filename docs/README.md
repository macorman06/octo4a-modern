# Plan de evolución de Jesper 3D

Fecha: 15 de octubre de 2026. Estado: propuesta organizada para implementar por funciones.

## Decisiones confirmadas

- Uso individual desde una Xiaomi Pad 6; funcionamiento local y sin conexión para los datos del taller.
- CR-10 S4 con Marlin por USB directo. Las impresiones se inician normalmente desde la tarjeta SD.
- Conservar la cuadrícula del firmware. El usuario recuerda 11 × 11, es decir, 121 puntos; falta confirmarlo con una lectura. La app no impondrá ese tamaño ni modificará el firmware.
- Pedidos procedentes de Instagram y WhatsApp.
- Separar impresora, producción e informes, ventas, inventario, compras/enlaces, presupuestos y mejoras.
- Registrar ideas, nuevas funciones y fallos desde la propia app.
- Presupuestos con detalle completo para el propietario y una salida para cliente centrada en el precio final.
- Primero definir el producto y sus límites; después implementar incrementos pequeños y verificables.

## Documentos

- [Producto y módulos](PRODUCTO.md): qué hace cada parte, navegación y recorridos de uso.
- [Datos y arquitectura](ARQUITECTURA.md): responsabilidades, costes, persistencia y límites de la automatización.
- [Hoja de ruta](HOJA_DE_RUTA.md): orden, dependencias y criterios de entrega.
- [Primera función: Mejoras](features/F01-MEJORAS.md): especificación lista para desarrollar después de la base común.
- [Presupuestos internos y para cliente](features/F04-PRESUPUESTOS.md): separación de datos, cálculo y casos de aceptación.

## Qué existe hoy

La APK 0.2 contiene el control USB, ajustes, temperaturas, movimientos, lectura y medición bilineal, historial de mallas y diagnóstico. Aún falta probar en la impresora del usuario la corrección de la desconexión observada en la versión anterior.

Los módulos comerciales, los informes de producción, el inventario y la pantalla de Mejoras **no están implementados**. Este cambio solo incorpora documentación; no publica una nueva APK ni modifica la conexión con la impresora.

El primer incremento propuesto es **F00: base de la app y conservación de datos**, seguido de **F01: Mejoras**. No se iniciarán todos los módulos a la vez.
