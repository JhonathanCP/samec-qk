# SAMEC

Sistema de la Oficina de Gestión de la Calidad y Humanización de EsSalud para la autoevaluación
con el **Listado de Estándares de Acreditación (LEA)**: macroprocesos → estándares → criterios
(ver `00. Manual completo LEA ESSALUD (Y-O).xlsx`).

Este repositorio trae lista toda la **infraestructura** —autenticación, usuarios, mensajería,
correo, orquestación y despliegue— migrada del Sistema de Auditorías y adaptada. Lo que falta es
el **dominio del core** (LEA y autoevaluaciones), que se construye sobre esta base (ver
[Cómo agregar un módulo](#cómo-agregar-un-módulo-del-core)).

---

## Arquitectura

```
Navegador ──► nginx-samec :8754 ──┬──► /            frontend Angular (same_angular)
                                  └──► /samec-api/  samec-core :8753 (same_quarkus) ──► Oracle 19c / PostgreSQL
                                                         │
                                                         └─ Kafka (samec-email-topic) ──► email-service-samec ──► Exchange / Gmail API
```

| Carpeta | Qué es | Tecnología |
|---|---|---|
| `same_quarkus/` | Core: API REST, seguridad, reglas de negocio | Quarkus 3.33 LTS, Java 21, Hibernate ORM/Panache, SmallRye JWT, SmallRye Kafka |
| `same_angular/` | Frontend | Angular 21 (LTS), standalone + signals |
| `email/` | Microservicio de correo (el mismo de Auditorías, rebrandeado) | Spring Boot 3.5, Kafka, Thymeleaf |
| `nginx/default.conf` | Orquestador: sirve el front y reenvía `/samec-api/` al core | Nginx |
| `docker-compose.yml` | Stack completo | Kafka KRaft, core, email, nginx |
| `scripts/` | Generación de las claves RSA de los JWT | openssl |

Puertos y red elegidos para convivir en el mismo servidor con Auditorías:

| Servicio | Puerto | Auditorías usa |
|---|---|---|
| `nginx-samec` | 8754 | 8744 |
| `samec-core` | 8753 | 8743 |
| `kafka-samec` | 9097 (solo dev) | 9095 / Zookeeper 2184 |
| Red Docker | 192.168.220.0/24 | 192.168.210.0/24 |

---

## Qué se migró de Auditorías (y qué cambió)

| Pieza | En SAMEC |
|---|---|
| Login con JWT + refresh en cookie httpOnly | Igual. Access token **RS256** de 30 min; refresh token **opaco** (no JWT) guardado como SHA-256, **rotado en cada uso** y con detección de reuso (si reaparece uno ya rotado, se cierran todas las sesiones del usuario; hay 30 s de gracia para dos pestañas renovando a la vez). |
| Cuenta desactivada = fuera de inmediato | Igual: en cada petición se cargan de la BD el estado, roles, permisos y alcance (`SesionAugmentor`). Un cambio de roles aplica sin volver a entrar. |
| Mensajes que no revelan si un usuario existe | Igual, y además el login tarda lo mismo exista o no la cuenta (verificación señuelo de BCrypt). |
| Bloqueo por cuenta tras 5 claves incorrectas (15 min) | Igual (`IntentosFallidos`). |
| Límite de solicitudes por IP/usuario (Nginx + backend) | Igual (`RateLimiter`, `RateLimitFilter`, `IpCliente`), con las rutas nuevas. |
| Olvidé mi contraseña con OTP de 8 dígitos | Igual; el OTP se guarda como hash y vence en `samec.auth.otp-minutos` (5). |
| Cuentas nuevas sin contraseña por correo | Igual: se les envía un enlace a `/auth/olvide-password?usuario=…`. |
| Autorregistro con aprobación de Sede Central | Igual (solo correos `@essalud.gob.pe`), con avisos por correo. |
| Gestión de usuarios por alcance (Admin/Sede/Red/IPRESS) | Igual; quién administra a quién está en `RoleSets.ADMINISTRA`. |
| Trazabilidad `created/updated at/by` | Igual, con una superclase `EntidadAuditable` en vez de reflexión por nombre de campo. |
| Bitácora de cambios | Igual, genérica (`BitacoraService.registrar(entidad, id, accion, detalle)`). |
| Kafka → email-service | Igual, pero **el evento sale solo si la transacción se confirma** (en Auditorías podía avisar de algo que luego no se guardaba). Kafka en modo **KRaft**: sin contenedor de Zookeeper. |
| Errores JSON `{ message }` en español | Igual (`ManejadorErrores`); los 500 nunca exponen detalles internos. |
| Catálogos MacroRegión → Red → IPRESS | Igual, con el mismo maestro (30 redes, 506 IPRESS). |
| Permisos duplicados backend/frontend | **Una sola fuente**: `Permisos.java`. El front recibe los permisos ya resueltos en la sesión. |
| Endpoints públicos por lista blanca | **Seguro por defecto**: todo endpoint exige sesión salvo los marcados `@PermitAll`. |
| Credenciales en `application*.properties` | Todo sensible entra por variables de entorno (`.env`, no versionado). |
| Usuarios de prueba sembrados siempre | Solo en dev/test; en producción se crea **un** administrador desde `SAMEC_ADMIN_*`. |

No se migró lo propio de Auditorías (comités, planes, auditorías, informes, seguimiento, IA/Ollama, RNA).

---

## Roles y permisos

Seis roles fijos. La migración es modular: por ahora el core (autoevaluación LEA) lo usan
**Administrador, Evaluador y Evaluado**; los gestores ya existen pero aún no tienen funciones del core.

| Rol | Alcance | Administra usuarios | Core (autoevaluación) |
|---|---|---|---|
| `ADMIN_SISTEMA` | Nacional | Todos | Catálogo LEA, iniciar y cerrar autoevaluaciones de cualquier IPRESS |
| `GESTOR_CALIDAD_SEDE` | Nacional | Todos salvo Administradores | Catálogo LEA, ver |
| `GESTOR_CALIDAD_RED` | Su Red | Gestores de IPRESS, evaluadores y evaluados de su Red | Ver |
| `GESTOR_CALIDAD_IPRES` | Su IPRESS | Evaluadores y evaluados de su IPRESS | Ver |
| `EVALUADOR` | Su IPRESS | — | Iniciar, revisar evidencias, verificar en campo, calificar y cerrar |
| `EVALUADO` | Su IPRESS | — | Registrar evidencias (archivos y enlaces) y corregir observaciones |

Los códigos anteriores (`DIRECTOR_IPRES`, `EVALUADOR_INTERNO`) se renombran solos al arrancar,
conservando a sus usuarios (`RoleSets.CODIGOS_ANTERIORES`).

Permisos (`same_quarkus/.../security/Permisos.java`): `usuarios.crud`, `usuarios.crudRed`,
`usuarios.crudIpress`, `usuarios.solicitudes`, `catalogos.crud`, `config.admin`, `accesos.ver`,
`autoevaluacion.ver`, `autoevaluacion.gestionar`, `evidencias.registrar`, `evidencias.evaluar`,
`dashboard.global`, `dashboard.red`, `dashboard.ipress`. El permiso abre el endpoint; el alcance
(un usuario de IPRESS solo ve la suya) lo validan los casos de uso.

---|---|---|
| `ADMIN_SISTEMA` | Nacional | Todos |
| `GESTOR_CALIDAD_SEDE` | Nacional | Todos salvo Administradores |
| `GESTOR_CALIDAD_RED` | Su Red | Gestores de IPRESS y evaluadores de su Red |
| `GESTOR_CALIDAD_IPRES` | Su IPRESS | Evaluadores de su IPRESS |
| `DIRECTOR_IPRES` | Su IPRESS | — (ve el listado de su IPRESS) |
| `EVALUADOR_INTERNO` | Su IPRESS | — |

Permisos actuales (`same_quarkus/.../security/Permisos.java`): `usuarios.crud`, `usuarios.crudRed`,
`usuarios.crudIpress`, `usuarios.solicitudes`, `catalogos.crud`, `config.admin`, `accesos.ver`,
`dashboard.global`, `dashboard.red`, `dashboard.ipress`.

---

## API (prefijo `/samec-api` detrás de Nginx)

| Público (`@PermitAll`) | |
|---|---|
| `POST /auth/login`, `/auth/refresh`, `/auth/logout` | Sesión (refresh en cookie `samecRefresh`) |
| `POST /auth/olvide-password`, `/auth/restablecer-password` | Recuperar o fijar contraseña con OTP |
| `POST /usuarios/autorregistro`, `GET /roles/autorregistro` | Solicitud de cuenta |
| `GET /redes`, `/redes/{id}`, `/ipress?idRed=`, `/ipress/{id}` | Catálogos para el registro |
| `GET /health` | Chequeo simple (el detallado `/q/health` no se publica en Nginx) |

| Con sesión | |
|---|---|
| `GET /auth/me` | Usuario, roles, permisos y alcance vigentes |
| `GET/POST /usuarios`, `GET/PUT /usuarios/{id}`, `PATCH /usuarios/{id}/estado`, `POST /usuarios/{id}/restablecer-password`, `GET /usuarios/roles-asignables`, `GET /usuarios/{id}/bitacora` | Gestión de usuarios (alcance validado en el core) |
| `POST /usuarios/me/password` | Cambiar la propia contraseña |
| `GET /usuarios/solicitudes`, `PATCH /usuarios/{id}/registro-estado` | Solicitudes de registro |
| `GET /roles`, `GET /macroregiones` | Catálogos |
| `POST/PUT /redes…`, `POST/PUT /ipress…`, `PATCH …/estado` | Mantenimiento de catálogos (`catalogos.crud`) |
| `GET /accesos` | Historial de inicios de sesión (`accesos.ver`) |

| Core: catálogo LEA (leer: con sesión; escribir: `catalogos.crud`) | |
|---|---|
| `GET/POST /catalog/macro-processes`, `GET/PUT/DELETE …/{id}` | Macroprocesos (con su peso en el puntaje final) |
| `GET/POST /catalog/standards?macroProcessId=`, `GET/PUT/DELETE …/{id}` | Estándares |
| `GET/POST /catalog/criteria?standardId=&category=`, `GET/PUT/DELETE …/{id}` | Criterios con condiciones, fuentes auditables y normas (agregado completo) |
| `GET/POST /catalog/institutional-processes?parentId=`, `/catalog/normative-documents`, `/catalog/observation-types?activeOnly=` | Procesos, documentos normativos, tipos de observación |
| `GET /catalog/tree` | Árbol liviano macroproceso → estándar → criterio (pantalla "Macroprocesos, procesos y criterios") |
| `GET /catalog/institutional-processes/{id}/usage`, `GET /catalog/normative-documents/{id}/usage` | Qué condiciones de qué criterios usan un proceso o citan una norma |

Pantalla de administración: **Administración → Macroprocesos, procesos y criterios** (permiso
`catalogos.crud`). Reúne en una sola opción lo que samec-old repartía en 3 pantallas: árbol con
buscador y el editor relacionado de macroproceso, estándar y criterio (condiciones, fuentes, procesos
y normativa en una vista), más procesos institucionales, normativa y tipos de observación. Eliminar
algo en uso explica qué lo usa.

| Core: autoevaluación | Permiso |
|---|---|
| `GET /self-assessments?facilityId=&year=&status=`, `GET /self-assessments/{id}` | `autoevaluacion.ver` |
| `GET /self-assessments/{id}/checklist` | `autoevaluacion.ver` — árbol LEA con evidencias, revisiones, puntaje sugerido y otorgado |
| `GET /self-assessments/{id}/result` | `autoevaluacion.ver` — % por macroproceso y categoría, puntaje final y clasificación |
| `POST /self-assessments`, `POST /self-assessments/{id}/close` | `autoevaluacion.gestionar` |
| `POST /self-assessments/{id}/evidence-lock`, `DELETE /self-assessments/{id}/evidence-lock` | `evidencias.evaluar` — candado: cierra/reabre la carga documental del evaluado; al cerrar califica con 0 los criterios documentales sin ningún sustento (al reabrir se quitan) |
| `POST /self-assessments/{id}/evidence` (multipart: `auditSourceId`, `files`…, `externalUrl`, `notes`) | `evidencias.registrar` |
| `POST /evidence/{id}/files`, `DELETE /evidence/{id}/files/{fileId}`, `PATCH /evidence/{id}`, `POST /evidence/{id}/resubmit`, `DELETE /evidence/{id}` | `evidencias.registrar` |
| `GET /self-assessments/{id}/evidence?status=`, `GET /evidence/{id}` | `autoevaluacion.ver` |
| `POST /evidence/{id}/review` (`APPROVED` / `OBSERVED` + tipo) | `evidencias.evaluar` |
| `PUT /self-assessments/{id}/conditions/{conditionId}/review` (multipart: `met`, `comment`, `files`…) | `evidencias.evaluar` — verificación en campo |
| `DELETE /condition-reviews/{id}/files/{fileId}` | `evidencias.evaluar` |
| `PUT /self-assessments/{id}/criteria/{criterionId}/score` | `evidencias.evaluar` — distinto del sugerido exige justificación |
| `GET /files/{id}?inline=` | `autoevaluacion.ver` — descarga desde la BD (BLOB) |

**Catálogo LEA.** Se carga solo al arrancar cuando el catálogo está vacío (BD nueva), desde
`same_quarkus/src/main/resources/seed/lea.json`: 22 macroprocesos, 71 estándares, 337 criterios y
539 condiciones, con normas, procesos institucionales y los pesos del modelo de calificación; también
crea los tipos de observación. Ese JSON se genera desde el Excel del manual:

```bash
pip install openpyxl
python scripts/lea_a_json.py   # lee "00. Manual completo LEA ESSALUD (Y-O).xlsx" y lista los casos ambiguos
```

Con una BD ya cargada, los cambios al LEA se hacen por la API del catálogo (`samec.seed.lea=false`
desactiva la carga).

Archivos: PDF, JPG/PNG y Office; hasta 20 MB cada uno y 10 por petición. El tipo se detecta por la
extensión y se verifica con el contenido (no se confía en lo que declara el navegador).

Documentación interactiva en desarrollo: <http://localhost:8753/q/swagger-ui>.

---

## Desarrollo local

Requisitos: Java 21, Maven 3.9, Node 22/24, PostgreSQL local. Docker solo si se quiere probar el
envío real de correos (Kafka + email-service).

```bash
# 1. Base de datos (una vez)
psql -U postgres -c "CREATE DATABASE samec"

# 2. Core — http://localhost:8753 (Swagger en /q/swagger-ui)
cd same_quarkus
mvn quarkus:dev
#   Usa localhost:5432/samec con postgres/root; otra BD: SAMEC_DB_URL, SAMEC_DB_USER, SAMEC_DB_PASSWORD.
#   Crea las tablas, siembra catálogos, el LEA y un usuario de prueba por rol (clave Samec#Dev2026):
#   admin, gestor_sede, gestor_red, gestor_ipres, evaluador, evaluado.

# 3. Frontend — http://localhost:4200 (el proxy reenvía /samec-api al core)
cd same_angular
npm install
npm start

# 4. (Opcional) Correos reales: Kafka + email-service del compose
docker compose up -d kafka-samec email-service-samec   # requiere EMAIL_USERNAME en .env
```

Sin Kafka el sistema funciona igual: los correos fallan en segundo plano y quedan en el log.

Pruebas:

```bash
cd same_quarkus && mvn test        # 24 pruebas: H2 en memoria + canal de correo en memoria (sin Docker)
cd email && mvn test               # 12 pruebas de plantillas y contexto
cd same_angular && npm test        # Vitest
```

---

## Despliegue (Docker Compose)

```bash
cp .env.example .env               # completar BD, URL pública, admin inicial y remitente
sh scripts/generar-claves-jwt.sh   # una vez por ambiente → secrets/jwt/ (no se versiona)
docker compose build
docker compose up -d
docker compose ps
curl http://localhost:8754/samec-api/health
```

- **Base de datos**: externa. `SAMEC_DB_KIND=oracle` (por defecto, Oracle 19c de GCTIC) o
  `postgresql`; se fija al construir la imagen del core. Hibernate crea y actualiza las tablas al
  arrancar (mismo criterio que Auditorías).
- **Primer ingreso**: con `SAMEC_ADMIN_USERNAME/EMAIL/PASSWORD`, se crea ese administrador si no
  hay ninguno. Cambiar su contraseña al entrar.
- **HTTPS**: en cuanto lo haya, `SAMEC_COOKIE_SECURE=true`.
- **reCAPTCHA v3** (opcional): `SAMEC_RECAPTCHA_ENABLED=true`, `SAMEC_RECAPTCHA_SECRET` y
  `SAMEC_RECAPTCHA_SITE_KEY` (esta última se compila en el front).
- **Correo**: `MAIL_PROVIDER=exchange` usa el relay `mail.essalud.gob.pe:25` sin credenciales;
  `gmail-api` requiere las variables `GMAIL_OAUTH_*` (ver `email/.../GmailApiEmailService.java`).
- El puerto 8753 del core queda publicado para diagnóstico; en producción conviene quitarlo.

---

## Cómo agregar un módulo del core

Ejemplo: autoevaluaciones del LEA.

**Backend** (`same_quarkus/src/main/java/com/essalud/samec/<modulo>/`)

1. Entidades que extienden `EntidadAuditable` (trazabilidad automática). Nombres de tabla en
   español y sin palabras reservadas de Oracle; ids `Long` con `GenerationType.IDENTITY`.
2. Repositorio `@ApplicationScoped implements PanacheRepositoryBase<Entidad, Long>`.
3. Servicio con `@Transactional`: reglas, alcance con `UsuarioActual` (`alcance()`, `idIpress()`,
   `cubreIpress(...)`), bitácora con `BitacoraService`, correos con `CorreoPublisher`
   (plantilla genérica `notificacion.html` o una nueva en `email/src/main/resources/templates`).
   Errores: `ReglaNegocioException` (400), `RecursoNoEncontradoException` (404),
   `AccesoDenegadoException` (403).
4. Recurso JAX-RS: sin anotación exige sesión; acciones con `@PermissionsAllowed(Permisos.X)`.
5. Permisos nuevos en `security/Permisos.java` (y roles nuevos en `security/RoleSets.java`).
6. Pruebas `@QuarkusTest` con `TestSupport.token("usuario")`.

**Frontend** (`same_angular/src/app/features/<modulo>/`)

1. Modelo en `core/models`, servicio que use `ApiService`.
2. Componentes standalone con signals; errores con `mensajeDeError(err)`; confirmaciones con
   `ModalService`; avisos con `ToastService`.
3. Ruta hija del layout en `app.routes.ts` con `canActivate: [permissionGuard]` y `data.permission`.
4. Entrada en el menú (`NAV_ITEMS` de `shared/components/sidebar`) y el permiso nuevo en
   `core/constants/permissions.ts`.
