import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, finalize, shareReplay } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginRequest, LoginResponse, MensajeResponse, UsuarioSesion } from '../models/sesion.model';

@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/auth`;

  /** Renovación en curso: si varias peticiones reciben 401 a la vez, comparten un solo refresh. */
  private refreshEnCurso: Observable<LoginResponse> | null = null;

  login(req: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.base}/login`, req, { withCredentials: true });
  }

  /** El refresh token viaja solo en la cookie httpOnly; el core lo rota en cada llamada. */
  refresh(): Observable<LoginResponse> {
    if (!this.refreshEnCurso) {
      this.refreshEnCurso = this.http
        .post<LoginResponse>(`${this.base}/refresh`, {}, { withCredentials: true })
        .pipe(
          finalize(() => (this.refreshEnCurso = null)),
          shareReplay({ bufferSize: 1, refCount: false }),
        );
    }
    return this.refreshEnCurso;
  }

  logout(): Observable<MensajeResponse> {
    return this.http.post<MensajeResponse>(`${this.base}/logout`, {}, { withCredentials: true });
  }

  me(): Observable<UsuarioSesion> {
    return this.http.get<UsuarioSesion>(`${this.base}/me`);
  }

  olvidePassword(username: string, recaptchaToken?: string): Observable<MensajeResponse> {
    return this.http.post<MensajeResponse>(`${this.base}/olvide-password`, { username, recaptchaToken });
  }

  restablecerPassword(username: string, otp: string, password: string): Observable<MensajeResponse> {
    return this.http.post<MensajeResponse>(`${this.base}/restablecer-password`, { username, otp, password });
  }
}
