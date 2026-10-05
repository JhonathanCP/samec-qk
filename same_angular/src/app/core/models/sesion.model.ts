import { Permission } from '../constants/permissions';

/** Alcance de datos según los roles: qué parte del país ve el usuario. */
export type Alcance = 'NACIONAL' | 'RED' | 'IPRESS';

/** Códigos de rol (security/RoleSets.java en el core). */
export type RolCodigo =
  | 'ADMIN_SISTEMA'
  | 'GESTOR_CALIDAD_SEDE'
  | 'GESTOR_CALIDAD_RED'
  | 'GESTOR_CALIDAD_IPRES'
  | 'EVALUADOR'
  | 'EVALUADO';

/** Usuario de la sesión: lo devuelven /auth/login, /auth/refresh y /auth/me. */
export interface UsuarioSesion {
  id: number;
  username: string;
  nombre: string;
  apellido: string;
  email: string;
  roles: RolCodigo[];
  permisos: Permission[];
  alcance: Alcance;
  idRed: number | null;
  redNombre: string | null;
  idIpress: number | null;
  ipressNombre: string | null;
}

export interface LoginRequest {
  username: string;
  password: string;
  recaptchaToken?: string;
}

/** El refresh token no viaja aquí: va en una cookie httpOnly que el navegador maneja solo. */
export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UsuarioSesion;
}

export interface MensajeResponse {
  message: string;
}
