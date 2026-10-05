# same_angular — frontend de SAMEC

Angular 21 (LTS), componentes standalone con signals. Visión general en el [README de SAMEC](../README.md).

```bash
npm install
npm start           # http://localhost:4200 — proxy.conf.json reenvía /samec-api al core (localhost:8753)
npm test            # Vitest
npm run build:prod  # set-env.js genera environment.prod.ts (API_URL, RECAPTCHA_SITE_KEY, DOMINIO_CORREO)
```

## Estructura (`src/app`)

| Carpeta | Contenido |
|---|---|
| `core/stores/auth.store.ts` | Sesión (usuario, access token, permisos). Se sincroniza entre pestañas. |
| `core/interceptors` | JWT (renueva la sesión ante un 401 y reintenta) y timeout |
| `core/guards` | `authGuard`, `guestGuard`, `permissionGuard` (permisos en `data`) |
| `core/services` | `ApiService` (base), auth, usuarios, organización, reCAPTCHA, toast, modal, renovación automática de la sesión |
| `core/constants/permissions.ts` | Nombres de permiso (la asignación rol → permiso vive solo en el core) |
| `shared` | Layout, menú (`NAV_ITEMS`), barra superior, modal, toast, loader, selector con búsqueda, `*appHasPermission` |
| `features` | `auth` (login, registro, recuperar contraseña), `inicio`, `usuarios` (listado, formulario, solicitudes), `cuenta`, `accesos` |

Para agregar un módulo del core, ver la guía en el README principal.
