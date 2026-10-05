# SAMEC — Análisis de requerimientos

Consolidado de tres fuentes, para guiar la implementación sobre la base actual (same_quarkus + same_angular):

- **Propuesta GCTIC** (`Propuesta_Sist_Acreditacion_GCTIC.pptx`, 27 diapositivas): procesos BPMN, roles y requerimientos, estados, generador de plantillas, AT/capacitaciones, mediciones y reportería.
- **Prototipo** (`PROTOTIPO SIST_ACREDITACION.pptx`, 45 diapositivas, Gerencia de Acreditación y Mejora Continua): objetivos, funcionalidades por rol y pantallas.
- **samec-old** (Flask): modelo del LEA y algoritmo de cálculo ya probados en QA.

Objetivos del sistema (prototipo): centralización documental, estandarización, minimizar errores documentarios (resoluciones mal redactadas), seguimiento y control, evaluación y retroalimentación. Reemplaza el uso del aplicativo "Acredita" y el llenado manual del Anexo 2.

---

## 1. Roles

| Rol (propuesto en código) | Propuesta | Prototipo | samec-old | Alcance |
|---|---|---|---|---|
| `ADMIN_SISTEMA` | Administrador del Sistema GAMCC | Admin. Sist. | ADMINISTRADOR | Nacional |
| `GERENTE_GAMCC` | Director/Gerente GAMCC | Gerencia/Dir. GAMCC ("Jefatura GAMCC") | GERENTE_GAMCC | Nacional |
| `SECTORISTA_GAMCC` | Sectorista GAMCC (= "Evaluador GAMCC") | Gestor GAMCC | GESTOR_GAMCC | IPRESS asignadas (reasignables) |
| `JEFE_CALIDAD_RED` | Jefe de Calidad de la Red | Usuario Red | RED | Su Red |
| `COORDINADOR_IPRESS` | (Jefe de Calidad de la IPRESS) | Coord. IPRESS (Jefe de Calidad) | EVALUADO | Su IPRESS |
| `EVALUADOR_IPRESS` | Usuario de IPRESS | Usuario Autoev. | EVALUADOR | Su IPRESS |

Reglas: un usuario puede tener más de un rol; el Admin asigna IPRESS a cada sectorista; el Jefe de Calidad de la IPRESS puede dar acceso a usuarios de su IPRESS; activación/desactivación con trazabilidad; correo al activar la cuenta; validar usuario oficial (correo institucional).

Funcionalidades por rol (prototipo):
- **Gerencia/Dir. GAMCC**: seguimiento del avance nacional, trazabilidad del proceso, cumplimiento de metas GAMCC, ajustar recursos según demanda, configuración del proceso anual (directiva, fechas, activar/cerrar año), metas de tiempo de respuesta por sectorista, reasignar sectorista por IPRESS, estadísticas, AT/capacitaciones (ver, reprogramar).
- **Gestor/Sectorista GAMCC**: bandeja de revisión documentaria de sus IPRESS (filtros año, IPRESS, red, macrorregión, fecha de asignación), aprobar/observar hitos ya aprobados por el Jefe de Calidad de Red, documentación centralizada, análisis de resultados, medición histórica, AT/capacitaciones (programar, reprogramar con causa, cancelar; calendario; invitación Outlook).
- **Jefe de Calidad de Red**: bandeja de autoevaluaciones de su red; aprobar/observar documentos de la IPRESS; generación y carga múltiple de resoluciones para varias IPRESS; seguimiento y control de avance; alertas; análisis por IPRESS; solicitud de AT.
- **Coordinador IPRESS**: carga documentaria por proceso institucional.
- **Evaluador IPRESS (usuario de autoevaluación)**: generación de plantillas de resoluciones por hito, validación del sustento por criterio (Anexo 2), guía en línea, análisis de baja puntuación, cálculo de puntaje, solicitud de AT, dashboard propio.

---

## 2. Proceso general (BPMN de la propuesta)

1. La IPRESS (o la Red) registra el proceso de autoevaluación y puede **solicitar asistencia técnica**.
2. **Revisión documentaria por hitos**: la IPRESS registra el documento de cada hito → el Jefe de Calidad de la Red lo verifica (aprueba u observa) → el sectorista GAMCC lo valida.
3. **Desarrollo de la autoevaluación** (hito "Desarrollo de Autoev.", subproceso "Ingreso de sustento"):
   - Evaluado: sube documentos por proceso institucional ("¿Data completa?").
   - Evaluador: valida sustentos → registra observaciones → califica criterios → con puntuación completa genera el cálculo, el Anexo de sustentos (Anexo 2) y el reporte final → cierre.
4. Sectorista GAMCC: valida resultados del Anexo 2, refuerza AT según puntajes, análisis y retroalimentación → cierre.

---

## 3. Hitos de la autoevaluación

Prototipo (8 hitos, coincide con `acreditacion_reporte_hitos.h1..h8` de samec-old):

| # | Hito | Documento | Fase |
|---|---|---|---|
| 1 | Conformación del Equipo de Acreditación | Resolución (plantilla) | Planificación |
| 2 | Conformación de Evaluadores Internos | Resolución (plantilla) | Planificación |
| 3 | Elaboración y aprobación del Plan de Autoevaluación | Resolución | Planificación |
| 4 | Comunicación de inicio de la Autoevaluación | Resolución | Planificación |
| 5 | Acta de Reunión de Apertura | Acta | Ejecución |
| 6 | Desarrollo de la Autoevaluación (Anexo 2) | Registro en sistema | Ejecución |
| 7 | Acta de Reunión de Cierre | Acta | Ejecución |
| 8 | Informe Técnico de Autoevaluación | Informe | Ejecución |

Por cada hito: generar documento desde plantilla (opcional), cargar la resolución aprobada (PDF, con confirmación "¿Está seguro que la resolución ya fue aprobada y sellada oficialmente?"), ver estado, detalle y descarga.

Reglas:
- Carga secuencial, salvo "Comunicación de inicio de la autoevaluación", que es libre.
- Se acepta presentación fuera de plazo; se muestran los días para el vencimiento y los días de retraso.
- Se envía correo al cargar, aprobar u observar.
- Hay trazabilidad por documento (usuario, estado, fecha, días de vencimiento).
- Indicador de avance: porcentaje del proceso, hito actual (3/8) y fase.

### Estados de un hito (propuesta, diapositiva 13)
`NO_PRESENTADO → PENDIENTE → APROBADO_JEFE_CALIDAD | OBSERVADO_JEFE_CALIDAD`; si se aprueba, `→ APROBADO_GAMCC` (final, cierra el hito) o `→ OBSERVADO_GAMCC`. Ambos observados vuelven a `PENDIENTE` al subsanar.

### Estado del proceso de acreditación de una IPRESS
`NO_PRESENTADO` (ningún hito) → `PLANIFICACION` (tiene documento en el hito 1) → `EJECUCION` (tiene documento en el hito 5) → `FINALIZADO` (aprobado por GAMCC).
Vista de la Red: `NO INICIADO` (sin documentos), `EN PROCESO` (al menos uno), `FINALIZADO` (documentación completa), más % de avance y última actualización.

### Operación múltiple (Red)
- La configuración de la Red define las IPRESS con resolución masiva por categoría (p. ej. Nivel I, hito 1). La propuesta habla de los hitos 1, 4 y 5.
- **Generación múltiple**: elegir resolución, categoría e IPRESS, y luego agregar miembros, o marcar IPRESS para la resolución de evaluadores internos. Una sola resolución lista a los integrantes agrupados por IPRESS. Vista previa y "Guardar y descarga".
- **Carga masiva** del documento firmado para varias IPRESS.

---

## 4. Generador de plantillas (Word)

- Cada hito tiene su plantilla, que administra el Admin.
- El usuario registra los campos de su IPRESS y el sistema genera la resolución. Ejemplo (Resolución de Conformación del Equipo de Acreditación):
  - N° (5 dígitos), red, año y fecha;
  - documento previo (nota, memorando o informe), su número y fecha;
  - nombre y cargo del responsable, nombre de la IPRESS;
  - integrantes del equipo (nombre, cargo, rol Coordinador o Miembro), mínimo 5.
- Vista previa en el navegador y exportación a .docx.
- A futuro: validar con IA que la resolución subida sea correcta.

---

## 5. Desarrollo de la autoevaluación (hito 6 — Anexo 2)

### 5.1 Carga documental por proceso institucional (Coordinador IPRESS)
- **Listado de procesos institucionales**: nombre, botón "ver flujo" (BPMN del proceso con los documentos y los criterios que sustenta, p. ej. DIR1-1), n.º de actividades, n.º de documentos cargados, estado (semáforo), cargar y observaciones.
  Ejemplos de procesos: Planificación y control de servicios de salud, Elaboración y uso de GPC, Recursos humanos, Seguridad y salud en el trabajo, Capacitación del personal, Gestión de la calidad, Gestión de reclamaciones, Información a los usuarios, Publicación de información, Medición de la satisfacción del usuario.
- **Detalle de un proceso**: actividad de la IPRESS, responsable, tipo de documento, archivo (subir), observación del evaluador (p. ej. "El documento no corresponde", "El documento no corresponde al año").

### 5.2 Validación por macroproceso y criterio (Evaluador IPRESS)
- **Macroprocesos**: total de estándares, criterios, puntaje alcanzado, % de avance del sustento e "Ir".
  - Gráfico de criterios con puntaje 0 por macroproceso ("oportunidad de mejora").
  - Puntaje total y clasificación.
  - Botones: Reporte Acredita (Excel), Generar Anexo 2 y Anexo 4 (Word).
- **Macroproceso → criterios**:
  - Cabecera: evaluadores, evaluados, fecha y servicios evaluados.
  - Por criterio: estándar, puntaje alcanzado, sustentos requeridos (n documentos), estado de carga (x/y con semáforo) y "Validar".
- **Modal del criterio**:
  - Descripción y textos de los puntajes 0/1/2; puntaje.
  - **Anexo 2 – Hoja de Registro de Datos**, una fila por condición: función en la IPRESS, responsable, tipo de documento, técnica, archivo (enlazado a lo cargado por proceso), cumple Sí/No, observación al documento y sustento del puntaje.
  - **Anexo 4 – Hoja de Recomendaciones / Oportunidades de mejora**: observaciones, recomendaciones, ¿acción inmediata? Sí/No, acción de mejora, responsable, fecha de compromiso.
- **Versiones** del registro del Anexo 2 ("Guardar nueva versión").
- **Ayuda contextual**: explicación del criterio y fuentes auditables. Chatbot (Botpress) con la normativa: integración externa, en una fase posterior.

### 5.3 Cálculo y clasificación
- Algoritmo de la planilla oficial "modelo excel acredita" (ya implementado en samec-old):
  - tipos de criterio con peso E×1, P×2, R×3;
  - % por macroproceso;
  - categorías GER 35 %, PRE 40 %, APO 25 %;
  - se calcula también por estándar.
- **Clasificación**: < 50 % no aprobado (reiniciar la autoevaluación); 50–69 % no aprobado (subsanar en 9 meses); 70–84 % no aprobado (subsanar en 6 meses); ≥ 85 % aprobado.
- **Semáforo de tableros**: verde > 84 %, amarillo 50–84 %, rojo < 50 %.
- **Reporte Acredita (Excel)**, con las mismas hojas que la planilla: Hoja1, Resultados, ResultxMacroproceso, CriteriosxMacroprocesos, ResultxEstandar, CriteriosxEstandares, Simulación, PuntajexEstandares.
- **Anexo 2 (Word)**, con cabecera: establecimiento, macroproceso, evaluador(es), fecha, servicios evaluados, participantes, código del estándar; y una tabla de criterio, puntaje, fuente auditable, técnica y sustento.

---

## 6. Asistencia técnica y capacitaciones

- **Solicitud de AT (IPRESS o Red)**: fecha, hora, tema, descripción, N° de expediente SGD, varias fechas por solicitud, registrado por y fecha de registro. Con calendario.
- **Registro por el sectorista**:
  - Datos: sectorista que atiende, red, IPRESS, tipo (AT o capacitación) y N° de requerimiento/expediente.
  - Puede registrar aunque la IPRESS no sea de su macrorregión (por reasignaciones, rotación o vacaciones).
- **Programación**: fecha de inicio y fin; búsqueda por expediente o IPRESS; filtros por red, IPRESS y estado; programar, reprogramar (con causa: falta de asistentes, cambio de personal, disponibilidad de la IPRESS u otra) y cancelar. Invitación al calendario de Outlook.
- **Estados**: `REGISTRADO (SOLICITADO) → PROGRAMADO → [REPROGRAMADO ↺] → ATENDIDO → CERRADO`, o `CANCELADO`. Además `VENCIDO` (registrado y no programado). "Atendido" significa que se registraron participantes y se atendió en fecha.
- **Participantes**: registro mediante un formulario externo (enlace público).
- **IPRESS**: calendario de capacitaciones programadas, próximas fechas y lista de participantes. Menú "Registrar disponibilidad para AT".

---

## 7. Alertas y notificaciones
- Vencimiento de un hito X días antes.
- Días de retraso: cuando la atención supera el tiempo promedio por hito.
- AT/capacitación vencida sin N° de expediente.
- AT/capacitación programada y no atendida.
- Campana de notificaciones dentro de la aplicación y correo (ya existe Kafka + email-service).

## 8. Tableros, mediciones y reportería
- **Dashboard de Gerencia** (pestañas Resumen, AT y Capacitaciones, Análisis de puntaje, Evaluación externa, Turnos), con filtros de año, red e IPRESS:
  - mapa de calor del Perú por red (n.º de IPRESS, % de avance documental, puntaje); totales de redes (30) e IPRESS;
  - estatus de acreditación (no acreditada, en autoevaluación, acreditada);
  - seguimiento de acreditación (IPRESS, %, vencimiento, observado);
  - puntaje alcanzado por red o IPRESS;
  - cobertura de capacitaciones y de asistencias técnicas;
  - incidencia de puntaje 0 por macroproceso;
  - avance documental por fase (planificación/ejecución) por red;
  - comparativo con la evaluación externa por IPRESS.
- **KPIs**:
  - eficacia de las AT;
  - índice de evolución del desempeño: (puntaje actual – puntaje anterior) / máximo;
  - % de hitos cumplidos y tiempo promedio por hito;
  - % de documentos entregados a tiempo y atendidos a tiempo;
  - calidad documental (aprobados sin observaciones / aprobados);
  - cobertura de capacitación y promedio de AT por periodo;
  - satisfacción post-capacitación;
  - tendencia de retrasos antes y después de una capacitación.
  - Todos por IPRESS, red, macrorregión y global.
- **Reportes**:
  - documentos por estado (tipo, IPRESS, red, macrorregión, nivel, fecha, sectorista o jefe de calidad);
  - desarrollo de la autoevaluación (macroproceso, puntaje, fuentes, versión, responsable de la AT);
  - programación de AT (fecha, vencimiento, estado, responsable, expediente, n.º de participantes).

## 9. Otros
- **Evaluación externa**: menú "Solicitud evaluación externa" e histórico con el puntaje de la evaluación externa (`autoevaluacion_historico` de samec-old). Hay poco detalle en las fuentes.
- **Ayuda en línea** en cada funcionalidad.

---

## 10. Puntos a confirmar con el área usuaria
1. **¿8 o 9 hitos?** La propuesta dice 9 (hito 7 = desarrollo; fases 1–4 y 5–9). El prototipo y samec-old usan 8 (hito 6 = desarrollo).
2. **Hitos con operación múltiple** de la Red: la propuesta dice 1, 4 y 5; el prototipo muestra 1 y 2.
3. **Nombres definitivos de los roles** y si la asignación del sectorista es por IPRESS (propuesta) o por red (samec-old).
4. **Pesos del cálculo**: el LEA (hoja "Criterios") trae los 22 macroprocesos, 338 criterios y 612 filas criterio × condición, pero no la fórmula. Los pesos por tipo, por macroproceso y por categoría salen de `samec-old/recursos/modelo excel acredita.xlsx`. Hay que confirmar que sean los vigentes.
5. **Plazos por hito**: si son por año (directiva del Gerente) o los ajusta cada red ("Configuración plazos autoevaluación").
6. **Formulario externo de participantes**: si es público o con código de evento.
7. Las diapositivas dicen "SEMAC"; se asume SAMEC.

## 11. Fases de implementación propuestas
- **F0 · Roles y organización**: los 6 roles; asignación sectorista ↔ IPRESS (con fecha y reasignación); el coordinador de IPRESS da acceso a usuarios de su IPRESS.
- **F1 · Catálogo LEA**:
  - macroproceso (categoría, peso), estándar, criterio, condición, fuente, técnica, procesos institucionales y sus actividades;
  - carga inicial desde el Excel LEA (hoja "Criterios") y mantenimiento.
- **F2 · Proceso anual y revisión documentaria por hitos**: plazos, estados, trazabilidad, correos y almacenamiento de archivos con control de acceso.
- **F3 · Generador de plantillas Word**: por hito y múltiple por red.
- **F4 · Desarrollo de la autoevaluación**: carga por proceso, validación por criterio (Anexo 2 y Anexo 4), versiones, cálculo y exportes Excel/Word.
- **F5 · AT y capacitaciones**: calendario y formulario de participantes.
- **F6 · Alertas**: tareas programadas y notificaciones dentro de la aplicación.
- **F7 · Tableros, KPIs y reportería**: incluye el mapa.
- **F8 · Evaluación externa**, chatbot e IA (futuro).
