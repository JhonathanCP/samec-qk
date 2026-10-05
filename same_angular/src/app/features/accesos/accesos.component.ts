import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Pagina } from '../../core/models/pagina.model';
import { ApiService } from '../../core/services/api.service';
import { mensajeDeError } from '../../core/utils/http-error';
import { LoaderComponent } from '../../shared/components/loader/loader.component';

interface Acceso {
  id: number;
  username: string;
  idUsuario: number | null;
  exito: boolean;
  motivo: string;
  ip: string | null;
  userAgent: string | null;
  fecha: string;
}

const MOTIVOS: Record<string, string> = {
  OK: 'Ingreso correcto',
  CREDENCIALES_INVALIDAS: 'Credenciales incorrectas',
  CUENTA_BLOQUEADA: 'Cuenta bloqueada temporalmente',
  RECAPTCHA_INVALIDO: 'reCAPTCHA inválido',
  REGISTRO_PENDIENTE: 'Solicitud pendiente',
  REGISTRO_RECHAZADO: 'Solicitud rechazada',
  CUENTA_DESACTIVADA: 'Cuenta desactivada',
};

/** Historial de inicios de sesión (solo Administrador). */
@Component({
  selector: 'app-accesos',
  imports: [FormsModule, DatePipe, LoaderComponent],
  template: `
    <div class="page-header">
      <div>
        <h2 class="page-title">Historial de accesos</h2>
        <p class="page-subtitle">Intentos de inicio de sesión, exitosos y fallidos</p>
      </div>
    </div>

    <div class="filtros-bar">
      <div class="filtro-campo">
        <label for="usuario">Usuario</label>
        <input id="usuario" class="input" [(ngModel)]="usuario" (keyup.enter)="cargar(0)" placeholder="Buscar..." />
      </div>
      <div class="filtro-campo">
        <label for="resultado">Resultado</label>
        <select id="resultado" class="input" [(ngModel)]="exito" (ngModelChange)="cargar(0)">
          <option [ngValue]="null">Todos</option>
          <option [ngValue]="true">Exitosos</option>
          <option [ngValue]="false">Fallidos</option>
        </select>
      </div>
      <button class="btn btn-secondary" (click)="cargar(0)"><i class="bi bi-search"></i> Buscar</button>
      @if (pagina(); as p) { <span class="filtro-contador">{{ p.totalElements }} registro(s)</span> }
    </div>

    @if (error()) { <div class="alert-error">{{ error() }}</div> }
    @if (cargando()) {
      <app-loader label="Cargando..." />
    } @else if (pagina(); as p) {
      <div class="table-card">
        <table class="data-table">
          <thead><tr><th>Fecha</th><th>Usuario</th><th>Resultado</th><th>IP</th><th>Navegador</th></tr></thead>
          <tbody>
            @for (a of p.content; track a.id) {
              <tr>
                <td>{{ a.fecha | date: 'dd/MM/yyyy HH:mm:ss' }}</td>
                <td>{{ a.username }}</td>
                <td><span class="badge" [class.badge-green]="a.exito" [class.badge-red]="!a.exito">{{ motivo(a.motivo) }}</span></td>
                <td>{{ a.ip }}</td>
                <td class="ua" [title]="a.userAgent ?? ''">{{ a.userAgent }}</td>
              </tr>
            } @empty {
              <tr><td colspan="5"><div class="empty-state"><span class="empty-state-title">Sin registros</span></div></td></tr>
            }
          </tbody>
        </table>
      </div>
      @if (p.totalPages > 1) {
        <div class="paginacion">
          <button class="btn btn-secondary btn-sm" [disabled]="p.page === 0" (click)="cargar(p.page - 1)">Anterior</button>
          <span>Página {{ p.page + 1 }} de {{ p.totalPages }}</span>
          <button class="btn btn-secondary btn-sm" [disabled]="p.page + 1 >= p.totalPages" (click)="cargar(p.page + 1)">Siguiente</button>
        </div>
      }
    }
  `,
  styles: [`
    .ua { max-width: 280px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--gray-500); }
    .paginacion { display: flex; align-items: center; justify-content: flex-end; gap: 12px; margin-top: 12px; font-size: 13px; }
  `],
})
export class AccesosComponent implements OnInit {
  private readonly api = inject(ApiService);

  usuario = '';
  exito: boolean | null = null;
  readonly pagina = signal<Pagina<Acceso> | null>(null);
  readonly cargando = signal(false);
  readonly error = signal('');

  ngOnInit(): void {
    this.cargar(0);
  }

  cargar(page: number): void {
    this.cargando.set(true);
    this.error.set('');
    this.api.get<Pagina<Acceso>>('/accesos', { username: this.usuario, exito: this.exito, page, size: 50 }).subscribe({
      next: (p) => {
        this.pagina.set(p);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(mensajeDeError(err));
        this.cargando.set(false);
      },
    });
  }

  motivo(codigo: string): string {
    return MOTIVOS[codigo] ?? codigo;
  }
}
