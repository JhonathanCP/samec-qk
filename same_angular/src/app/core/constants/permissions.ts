/**
 * Permisos de acción. La asignación rol → permisos vive SOLO en el core
 * (same_quarkus/.../security/Permisos.java) y llega resuelta en la sesión (user.permisos):
 * aquí solo se declaran los nombres para tener tipado. Al agregar un permiso en el core,
 * agregarlo también a este tipo.
 */
export type Permission =
  | 'usuarios.crud'
  | 'usuarios.crudRed'
  | 'usuarios.crudIpress'
  | 'usuarios.solicitudes'
  | 'catalogos.crud'
  | 'config.admin'
  | 'accesos.ver'
  | 'autoevaluacion.ver'
  | 'autoevaluacion.gestionar'
  | 'evidencias.registrar'
  | 'evidencias.evaluar'
  | 'dashboard.global'
  | 'dashboard.red'
  | 'dashboard.ipress';

/** Cualquiera de estos permite entrar a la gestión de usuarios. */
export const PERMISOS_USUARIOS: Permission[] = ['usuarios.crud', 'usuarios.crudRed', 'usuarios.crudIpress'];
