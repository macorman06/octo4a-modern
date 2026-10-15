# Verificación de Jesper 3D 0.1.0-alpha

Fecha: 15 de octubre de 2026.

Código comprobado: `eed61f17e2449452f1cfe8caad6bbdd9f4662323`.

[Ejecución completa de GitHub Actions](https://github.com/macorman06/octo4a-modern/actions/runs/38761416471): **success**.

## Comprobado

- Compilación de la APK Android de desarrollo completada.
- 21 pruebas de protocolo: 21 correctas, 0 fallos, 0 omitidas. Cubren respuestas USB fragmentadas, temperaturas, posiciones, capacidades, ajustes, malla bilineal, puntos desconocidos, respuestas corruptas y límites de movimiento/temperatura.
- Análisis Android: 0 errores, 9 avisos. Ocho son recomendaciones de actualización de SDK/dependencias; uno corresponde a las reglas de extracción de datos. `allowBackup` está desactivado. El falso positivo del analizador sobre el permiso de servicio USB está justificado localmente: la app comprueba el permiso USB efectivo antes de iniciar el servicio.
- Una prueba de interfaz en Android 15 virtual: arranque, navegación por Resumen/Cama/Ajustes/Consola y controles de máquina desactivados al no haber conexión. 1 correcta, 0 fallos, 0 omitidas.
- Capturas del resumen y de la pantalla de cama obtenidas del emulador y revisadas visualmente. No contienen datos de una impresora ni una malla simulada.
- Descarga de los artefactos verificada frente a las huellas SHA-256 que devuelve GitHub.

SHA-256 de la APK entregada:

```text
ae35c6359a5f19be44b9da57c1dac30d0ee56db8b06cc1d9cb2e0eea83e533e3  Jesper3D-0.1.0-alpha.apk
```

## Pendiente de una impresora real

No se ha conectado ni movido ni calentado una impresora durante estas pruebas. La integración física de la Xiaomi Pad 6/HyperOS, cable OTG y placa de la CR-10 S4, la apertura del puerto, la respuesta exacta de TH3D/Marlin y la permanencia de USB en segundo plano requieren prueba real. El emulador no valida nada de ello.

También requieren verificación física: posición/orientación real de los puntos, funcionamiento y recorrido del sensor, origen y movimientos, temperaturas objetivo, medición y activación de malla, persistencia después de `M500` y respuesta a la solicitud `M112`.

Primera prueba propuesta: instalar la APK, conectar con la impresora en reposo a 115200 y comprobar únicamente identificación del firmware y temperaturas. Después se compara la malla leída con la pantalla/consola de Marlin, antes de habilitar movimientos o realizar nuevas mediciones.

## Límites de esta entrega

Es una versión alfa centrada en consulta y calibración de Marlin por USB. La visualización de malla admite el formato bilineal indexado. No incluye impresión de archivos, UBL, edición de puntos, modificación del número de puntos compilado en Marlin ni editor general de ajustes. El informe de ajustes representa RAM y no garantiza coincidencia con EEPROM. La medición nueva está condicionada a las capacidades anunciadas y a la confirmación del perfil.
