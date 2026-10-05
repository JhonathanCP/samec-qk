import { Alcance, RolCodigo } from './sesion.model';

export type EstadoRegistro = 'PENDIENTE' | 'APROBADO' | 'RECHAZADO';

export interface Rol {
  id: number;
  codigo: RolCodigo;
  nombre: string;
  descripcion: string;
  /** Qué ubicación pide el rol: NACIONAL (ninguna), RED o IPRESS. */
  alcance: Alcance;
}

export interface Usuario {
  id: number;
  username: string;
  nombre: string;
  apellido: string;
  email: string;
  activo: boolean;
  roles: Rol[];
  alcance: Alcance;
  idRed: number | null;
  redNombre: string | null;
  idIpress: number | null;
  ipressNombre: string | null;
  autoregistrado: boolean;
  registroEstado: EstadoRegistro;
  registroComentario: string | null;
  registroRevisadoPorNombre: string | null;
  registroRevisadoAt: string | null;
  registroSolicitadoAt: string | null;
  ultimoAcceso: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Alta o edición por un administrador (la contraseña la fija el usuario con el enlace del correo). */
export interface UsuarioRequest {
  username: string;
  nombre: string;
  apellido: string;
  email: string;
  roles: RolCodigo[];
  idRed: number | null;
  idIpress: number | null;
}

export interface AutorregistroRequest {
  nombre: string;
  apellido: string;
  email: string;
  password: string;
  rol: RolCodigo;
  idRed: number | null;
  idIpress: number | null;
  recaptchaToken?: string;
}

export interface EntradaBitacora {
  id: number;
  accion: string;
  detalle: string | null;
  usuarioNombre: string;
  fecha: string;
}
