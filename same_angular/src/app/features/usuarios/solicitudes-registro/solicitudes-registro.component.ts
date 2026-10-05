import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EstadoRegistro, Usuario } from '../../../core/models/usuario.model';
import { ModalService } from '../../../core/services/modal.service';
import { ToastService } from '../../../core/services/toast.service';
import { UsuarioService } from '../../../core/services/usuario.service';
import { mensajeDeError } from '../../../core/utils/http-error';
import { LoaderComponent } from '../../../shared/components/loader/loader.component';

/** Bandeja de solicitudes de autorregistro: aprobar habilita el ingreso; rechazar lo impide. */
@Component({
  selector: 'app-solicitudes-registro',
  imports: [FormsModule, DatePipe, LoaderComponent],
  template: `
    <div class="page-header">
      <div>
        <h2 class="page-title">Solicitudes de registro</h2>
        <p class="page-subtitle">Cuentas pedidas desde la pantalla pública de registro</p>
      </div>
    </div>

    <div class="filtros-bar">
      <div class="filtro-campo">
        <label for="estado">Estado</label>
        <select id="estado" class="input" [ngModel]="estado()" (ngModelChange)="estado.set($event); cargar()">
          <option [ngValue]="'PENDIENTE'">Pendientes</option>
          <option [ngValue]="'APROBADO'">Aprobadas</option>
          <option [ngValue]="'RECHAZADO'">Rechazadas</option>
          <option [ngValue]="null">Todas</option>
        </select>
      </div>
      <span class="filtro-contador">{{ filas().length }} solicitud(es)</span>
    </div>

    @if (error()) { <div class="alert-error">{{ error() }}</div> }
    @if (cargando()) {
      <app-loader label="Cargando solicitudes..." />
    } @else {
      <div class="table-card">
        <table class="data-table">
          <thead>
            <tr><th>Solicitante</th><th>Rol</th><th>IPRESS / Red</th><th>Solicitada</th><th>Estado</th><th></th></tr>
          </thead>
          <tbody>
            @for (s of filas(); track s.id) {
              <tr>
                <td>{{ s.nombre }} {{ s.apellido }}<span class="celda-sub">{{ s.email }}</span></td>
                <td>@for (r of s.roles; track r.codigo) { <span class="badge badge-blue">{{ r.nombre }}</span> }</td>
                <td>{{ s.ipressNombre ?? s.redNombre }}<span class="celda-sub">{{ s.ipressNombre ? s.redNombre : '' }}</span></td>
                <td>{{ s.registroSolicitadoAt | date: 'dd/MM/yyyy HH:mm' }}</td>
                <td>
                  <span class="badge" [class.badge-yellow]="s.registroEstado === 'PENDIENTE'"
                        [class.badge-green]="s.registroEstado === 'APROBADO'" [class.badge-red]="s.registroEstado === 'RECHAZADO'">
                    {{ s.registroEstado }}
                  </span>
                  @if (s.registroRevisadoPorNombre) {
                    <span class="celda-sub">{{ s.registroRevisadoPorNombre }} · {{ s.registroRevisadoAt | date: 'dd/MM/yyyy' }}</span>
                  }
                  @if (s.registroComentario) { <span class="celda-sub">“{{ s.registroComentario }}”</span> }
                </td>
                <td>
                  @if (s.registroEstado === 'PENDIENTE') {
                    <div class="acciones">
                      <button class="btn btn-success btn-sm" [disabled]="ocupado() === s.id" (click)="aprobar(s)">Aprobar</button>
                      <button class="btn btn-danger btn-sm" [disabled]="ocupado() === s.id" (click)="rechazar(s)">Rechazar</button>
                    </div>
                  }
                </td>
              </tr>
            } @empty {
              <tr><td colspan="6"><div class="empty-state"><i class="bi bi-inbox empty-state-icon"></i>
                <span class="empty-state-title">No hay solicitudes</span></div></td></tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class SolicitudesRegistroComponent implements OnInit {
  private readonly usuarios = inject(UsuarioService);
  private readonly modal = inject(ModalService);
  private readonly toast = inject(ToastService);

  readonly estado = signal<EstadoRegistro | null>('PENDIENTE');
  readonly filas = signal<Usuario[]>([]);
  readonly cargando = signal(true);
  readonly error = signal('');
  readonly ocupado = signal<number | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set('');
    this.usuarios.solicitudes(this.estado()).subscribe({
      next: (filas) => {
        this.filas.set(filas);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(mensajeDeError(err));
        this.cargando.set(false);
      },
    });
  }

  async aprobar(s: Usuario): Promise<void> {
    const ok = await this.modal.confirm(
      `${s.nombre} ${s.apellido} podrá ingresar como ${s.roles.map((r) => r.nombre).join(', ')}. Se le avisará por correo.`,
      'Aprobar solicitud', 'Aprobar');
    if (ok) this.resolver(s, 'APROBADO');
  }

  async rechazar(s: Usuario): Promise<void> {
    const datos = await this.modal.form('Rechazar solicitud', [
      { key: 'comentario', label: 'Motivo (se incluye en el correo al solicitante)', required: true },
    ], 'Rechazar');
    if (datos) this.resolver(s, 'RECHAZADO', datos['comentario']);
  }

  private resolver(s: Usuario, estado: 'APROBADO' | 'RECHAZADO', comentario?: string): void {
    this.ocupado.set(s.id);
    this.usuarios.resolverSolicitud(s.id, estado, comentario).subscribe({
      next: () => {
        this.ocupado.set(null);
        this.toast.success(estado === 'APROBADO' ? 'Solicitud aprobada.' : 'Solicitud rechazada.');
        this.cargar();
      },
      error: (err) => {
        this.ocupado.set(null);
        this.toast.error(mensajeDeError(err));
      },
    });
  }
}
