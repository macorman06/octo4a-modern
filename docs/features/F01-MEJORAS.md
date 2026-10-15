# F01 — Mejoras de la app

Estado: especificación propuesta, pendiente de implementar. Dependencia: F00, navegación, repositorios y copia completa.

## Resultado esperado

El usuario puede apuntar una necesidad mientras usa cualquier módulo, recuperarla después y seguir su resolución desde la tablet. No necesita USB, conexión a internet, una cuenta ni abrir GitHub.

## Primera entrega

Lista con búsqueda por texto, filtro de tipo/área/estado y orden por prioridad o última modificación. Formulario de alta/edición y ficha con descripción y seguimiento. La lista conserva su selección al cambiar de módulo.

Acceso «Añadir mejora» desde el área actual con el módulo preseleccionado. La acción solo abre el formulario: no cancela lo que está haciendo la impresora ni consulta el USB.

## Campos

- ID estable, fecha de creación y modificación: automáticos.
- Tipo: Idea, Función o Fallo.
- Título obligatorio de 1–160 caracteres útiles; descripción opcional de hasta 10.000.
- Área: General, Impresora, Producción, Informes, Inventario, Compras y enlaces, Presupuestos, Pedidos y clientes, o Contenido y seguimiento.
- Prioridad: Baja, Normal, Alta o Crítica; Normal por defecto. Es una prioridad elegida por el usuario, no una promesa automática de orden de desarrollo.
- Estado: Bandeja, Planificada, En curso, Por verificar, Hecha o Descartada; Bandeja por defecto.
- Notas de seguimiento, motivo de descarte opcional y versión de resolución opcional.
- En fallos: pasos para reproducir, qué se esperaba y qué ocurrió; todos opcionales para permitir una captura rápida.
- Versión instalada de la app, tomada localmente; no se infiere una versión de firmware que no se haya recibido.
- Relación opcional con otra mejora como duplicado. Impedir autorreferencias y no borrar la original.

Los cambios de estado quedan fechados. «Por verificar» permite registrar una corrección pendiente de probar. Un fallo puede reabrirse conservando su seguimiento.

## Diagnóstico y datos privados

Primer alcance: incluir versión de app y, si se selecciona expresamente, un resumen de contexto proporcionado por la app. Para la impresora puede contener estado de conexión, fase y error ya observados, sin lanzar nuevas consultas.

El diagnóstico completo es opcional, con vista previa y confirmación de inclusión. Si requiere adjuntos aún no disponibles en F00, posponerlo como F01b sin impedir crear el fallo; el diagnóstico actual puede exportarse por su acción existente. No convertir adjuntos opcionales en requisito para informar de un problema.

La pantalla de Mejoras no importa ni depende directamente de `PrinterController`: la app suministra una instantánea permitida. No adjuntar automáticamente clientes, conversaciones, pedidos o la copia completa de la base.

No crear incidencias públicas ni enviar registros fuera del dispositivo. Exportar una selección para revisión es una acción posterior explícita y muestra qué campos incluye.

## Persistencia y correcciones

Guardar antes de mostrar el mensaje de éxito y conservar datos tras cierre/reapertura. Si falla la escritura, mantener el texto en el formulario para reintentar. Avisar al abandonar un borrador modificado.

Descartar una idea no la borra. La eliminación definitiva, si se incorpora, tiene una acción y confirmación propias; no se mezcla con «Hecha». Restaurar una copia devuelve también estados, notas y relaciones entre mejoras.

## Criterios de aceptación

1. Sin impresora ni internet, crear una idea con solo título y área; cerrar y abrir la app; sigue en la lista.
2. Crear un fallo desde Cama durante una sesión: se preselecciona Impresora y no se envía ningún comando ni se recrea la conexión.
3. Buscar por texto, filtrar y abrir una ficha; al volver se conserva la lista filtrada.
4. Cambiar a Por verificar, registrar una versión de corrección y volver a Bandeja conserva el seguimiento.
5. Exportar una copia, crear otra entrada y restaurar la copia con su confirmación: los datos restaurados coinciden con la instantánea elegida.
6. Título vacío o compuesto solo por espacios no se guarda; un fallo de almacenamiento no pierde el borrador.
7. El diagnóstico no se incorpora sin seleccionarlo; la vista previa no contiene datos de otros módulos por defecto.
8. Una relación de duplicado no puede apuntar a la propia entrada y una referencia ausente en una copia se rechaza antes de restaurar.

## Fuera de esta entrega

Integración con GitHub, votaciones, usuarios/asignaciones, tickets públicos, diagnóstico enviado automáticamente, capturas de pantalla, grabaciones y priorización automática. Se añaden solo si se solicitan y se concreta su alcance.

## Comprobación necesaria al implementarla

Pruebas de repositorio para persistencia/estados y relaciones; prueba de interfaz para crear, filtrar y reabrir; prueba de exportación/restauración con mejoras. Reutilizar la regresión del controlador para comprobar que navegar o guardar una idea no afecta al USB. No añadir pruebas que solo repitan getters o textos estáticos.
