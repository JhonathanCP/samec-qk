# same_quarkus — core de SAMEC

Quarkus 3.33 LTS · Java 21. Visión general, despliegue y guía de módulos en el [README de SAMEC](../README.md).

```bash
mvn quarkus:dev     # http://localhost:8753 — Swagger: /q/swagger-ui — PostgreSQL local "samec"
mvn test            # H2 en memoria + canal de correo en memoria (sin Docker ni Kafka)
mvn package         # target/quarkus-app/ (SAMEC_DB_KIND=oracle|postgresql fija la BD al compilar)
```

## Paquetes (`com.essalud.samec`)

| Paquete | Contenido |
|---|---|
| `auth` | Login, refresh, logout, `/auth/me`, recuperación de contraseña (`password/`), historial de accesos (`acceso/`) |
| `security` | `RoleSets` (roles y quién administra a quién), `Permisos` (permiso → roles), `SesionAugmentor` (carga la sesión en cada petición), `UsuarioActual`, `PasswordHasher`, `RecaptchaService`, `IpCliente`, `token/` (JWT y refresh tokens), `ratelimit/` |
| `usuarios` | Usuarios, roles, autorregistro y solicitudes |
| `organizacion` | MacroRegión → Red → IPRESS |
| `shared` | `config/` (SamecConfig, DataInitializer), `error/` (excepciones y mapeo a JSON), `trazabilidad/` (EntidadAuditable), `bitacora/`, `correo/` (Kafka), `paginacion/`, `salud/` |

## Configuración

Todo en `src/main/resources/application.properties`; lo sensible entra por variables de entorno:

| Variable | Para qué | Por defecto (dev) |
|---|---|---|
| `SAMEC_DB_KIND` | `postgresql` u `oracle` (al compilar) | `postgresql` |
| `SAMEC_DB_URL`, `SAMEC_DB_USER`, `SAMEC_DB_PASSWORD` | Base de datos | `localhost:5432/samec`, `postgres`/`root` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka | `localhost:9097` |
| `SAMEC_JWT_PUBLIC_KEY`, `SAMEC_JWT_PRIVATE_KEY` | Claves RSA (solo prod; en dev/test se generan en memoria) | `/run/secrets/jwt/*.pem` |
| `SAMEC_APP_URL`, `SAMEC_CORS_ORIGINS` | URL del front (correos) y CORS | `http://localhost:4200` |
| `SAMEC_COOKIE_SECURE` | Cookie `Secure` (con HTTPS) | `false` |
| `SAMEC_RECAPTCHA_ENABLED`, `SAMEC_RECAPTCHA_SECRET` | reCAPTCHA v3 | desactivado |
| `SAMEC_ADMIN_USERNAME/EMAIL/PASSWORD` | Primer administrador en producción | — |
