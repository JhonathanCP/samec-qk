# Pendientes

Decisiones por confirmar con el área usuaria y trabajo que quedó para después.

## Por confirmar

### 1. Peso de un macroproceso sin criterios para la categoría de la IPRESS

Cuando un macroproceso no tiene criterios que apliquen a la categoría de la IPRESS (por ejemplo,
**AEX – Atención extramural** no tiene criterios para **III-1**), hoy su peso se reparte
proporcionalmente entre los demás macroprocesos (`AccreditationScoring`). Para III-1, PRE pasa de
40 % a 35,48 %, GER de 35 % a 37,63 % y APO de 25 % a 26,88 %.

La alternativa es mantener los pesos fijos (GER 35, PRE 40, APO 25) y contar ese macroproceso como
0 %, como hace la planilla del modelo de calificación (`modelo excel acredita.xlsx`). Con esa
opción, una IPRESS III-1 nunca podría pasar de 93 %.

**¿Cuál corresponde?**

### 2. Casos ambiguos del Excel LEA

`scripts/lea_a_json.py` los lista al convertir el manual. Hoy quedaron así:

| Criterio | Qué trae el Excel | Cómo se cargó |
|---|---|---|
| DIR1-2 | 3 fuentes sin marcar Y/O | Se exigen todas (Y) |
| MRA1-10 | Solo niveles 0 y 1 en el enunciado | Se cargan los dos |
| MRA4-1 | 2 normas sin enlace | Normas sin enlace |
| MRA5-1 | "¿Aplica a todo ESSALUD?" = Sí, pero sin enlace en "Ubicación" | La sube la IPRESS |
| ATH4-1, EMG1-4 | 1 fuente con 3 enlaces | Se usa el primer enlace |
| EIF1-6 | 5 fuentes con 2 enlaces | Se asignan en orden |
| MRA7-3, MRA7-4 | Nivel 1 = "NO VALIDO" | Se carga tal cual |

Corregirlos en el Excel y volver a generar `seed/lea.json`, o confirmar que así está bien.

### 3. Tipos de observación

Se sembraron los dos del prototipo ("El documento no corresponde", "El documento no corresponde
al año") y "Otro". Falta la lista oficial; se editan por la API del catálogo.

## Del prototipo, aún sin implementar

Las pantallas de autoevaluación siguen el prototipo (PPT). Estas partes se ven en él pero todavía no
tienen backend:

- **Reportes finales**: Reporte Acredita (Excel), Generar Anexo 2 y Anexo 4 (Word). Los botones ya
  están en "Macroprocesos", deshabilitados ("Disponibles próximamente").
- **Cabecera del Anexo 2** por macroproceso: evaluadores, evaluados, fecha y servicios evaluados.
- **Anexo 4 – Hoja de Recomendaciones / Oportunidades de mejora** por criterio: observaciones,
  recomendaciones, ¿acción inmediata?, acción de mejora, responsable y fecha de compromiso.
- **Versiones del registro del Anexo 2** ("Guardar nueva versión").
- **Asistente virtual** (chatbot con la normativa).

## Para después

- **Notificaciones por hitos** del proceso de acreditación (la sección de evidencias no notifica).
  Usar el canal Kafka `correos` existente.
- **Funciones de los gestores** (Sede, Red, IPRESS) en el core: seguimiento y tableros. El
  GESTOR_GAMCC de samec-old tenía varias redes; el alcance actual es una sola red.
- **Base en español**: las tablas heredadas (`usuarios`, `ipress`, `redes`…) siguen en español y el
  core en inglés.
- **Categoría de la IPRESS**: `ipress` solo guarda el nivel I/II/III; la autoevaluación guarda su
  propia copia de la categoría (I-1 … III-1) al iniciarse.
- **Migraciones versionadas** (Flyway) para producción en vez de `schema-management=update`.
