import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { MensajeResponse } from '../models/sesion.model';
import {
  AutorregistroRequest, EntradaBitacora, EstadoRegistro, Rol, Usuario, UsuarioRequest,
} from '../models/usuario.model';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private readonly api = inject(ApiService);

  /** Usuarios dentro del alcance de quien consulta (lo filtra el core). */
  listar(): Observable<Usuario[]> {
    return this.api.get<Usuario[]>('/usuarios');
  }

  obtener(id: number): Observable<Usuario> {
    return this.api.get<Usuario>(`/usuarios/${id}`);
  }

  bitacora(id: number): Observable<EntradaBitacora[]> {
    return this.api.get<EntradaBitacora[]>(`/usuarios/${id}/bitacora`);
  }

  /** Roles que el usuario de la sesión puede asignar. */
  rolesAsignables(): Observable<Rol[]> {
    return this.api.get<Rol[]>('/usuarios/roles-asignables');
  }

  crear(req: UsuarioRequest): Observable<Usuario> {
    return this.api.post<Usuario>('/usuarios', req);
  }

  actualizar(id: number, req: UsuarioRequest): Observable<Usuario> {
    return this.api.put<Usuario>(`/usuarios/${id}`, req);
  }

  cambiarEstado(id: number, activo: boolean): Observable<Usuario> {
    return this.api.patch<Usuario>(`/usuarios/${id}/estado`, { activo });
  }

  restablecerPassword(id: number): Observable<MensajeResponse> {
    return this.api.post<MensajeResponse>(`/usuarios/${id}/restablecer-password`);
  }

  cambiarMiPassword(passwordActual: string, passwordNueva: string): Observable<MensajeResponse> {
    return this.api.post<MensajeResponse>('/usuarios/me/password', { passwordActual, passwordNueva });
  }

  // ── Autorregistro ──

  rolesAutorregistro(): Observable<Rol[]> {
    return this.api.get<Rol[]>('/roles/autorregistro');
  }

  autorregistro(req: AutorregistroRequest): Observable<MensajeResponse> {
    return this.api.post<MensajeResponse>('/usuarios/autorregistro', req);
  }

  solicitudes(estado: EstadoRegistro | null): Observable<Usuario[]> {
    return this.api.get<Usuario[]>('/usuarios/solicitudes', { estado });
  }

  resolverSolicitud(id: number, estado: 'APROBADO' | 'RECHAZADO', comentario?: string): Observable<Usuario> {
    return this.api.patch<Usuario>(`/usuarios/${id}/registro-estado`, { estado, comentario });
  }
}
