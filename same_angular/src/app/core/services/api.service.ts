import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

type Params = Record<string, string | number | boolean | null | undefined>;

/** Base de los servicios del core: arma la URL y omite los parámetros vacíos. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  get<T>(path: string, params?: Params): Observable<T> {
    return this.http.get<T>(`${this.base}${path}`, { params: this.params(params) });
  }

  post<T>(path: string, body: unknown = {}): Observable<T> {
    return this.http.post<T>(`${this.base}${path}`, body);
  }

  put<T>(path: string, body: unknown): Observable<T> {
    return this.http.put<T>(`${this.base}${path}`, body);
  }

  patch<T>(path: string, body: unknown = {}): Observable<T> {
    return this.http.patch<T>(`${this.base}${path}`, body);
  }

  delete<T>(path: string): Observable<T> {
    return this.http.delete<T>(`${this.base}${path}`);
  }

  /** multipart/form-data (archivos de evidencia, fotos): el navegador arma el boundary. */
  postForm<T>(path: string, form: FormData): Observable<T> {
    return this.http.post<T>(`${this.base}${path}`, form);
  }

  putForm<T>(path: string, form: FormData): Observable<T> {
    return this.http.put<T>(`${this.base}${path}`, form);
  }

  /** Para subir archivos: el campo del archivo se llama "file". */
  postArchivo<T>(path: string, archivo: File, extra?: Record<string, string>): Observable<T> {
    const fd = new FormData();
    fd.append('file', archivo);
    Object.entries(extra ?? {}).forEach(([k, v]) => fd.append(k, v));
    return this.http.post<T>(`${this.base}${path}`, fd);
  }

  descargar(path: string, params?: Params): Observable<Blob> {
    return this.http.get(`${this.base}${path}`, { params: this.params(params), responseType: 'blob' });
  }

  private params(params?: Params): HttpParams {
    let p = new HttpParams();
    Object.entries(params ?? {}).forEach(([k, v]) => {
      if (v !== null && v !== undefined && v !== '') p = p.set(k, String(v));
    });
    return p;
  }
}
