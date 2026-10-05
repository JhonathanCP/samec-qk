import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { RolCodigo } from '../../../core/models/sesion.model';
import { Rol, Usuario } from '../../../core/models/usuario.model';
import { ModalService } from '../../../core/services/modal.service';
import { ToastService } from '../../../core/services/toast.service';
import { UsuarioService } from '../../../core/services/usuario.service';
import { AuthStore } from '../../../core/stores/auth.store';
import { mensajeDeError } from '../../../core/utils/http-error';
import { LoaderComponent } from '../../../shared/components/loader/loader.component';

/**
 * Usuarios dentro del alcance de quien consulta (el core ya filtra). Las acciones solo aparecen
 * sobre los usuarios que puede administrar: todos sus roles deben estar entre los que él asigna.
 */
@Component({
  selector: 'app-usuario-list',
  imports: [FormsModule, RouterLink, DatePipe, LoaderComponent],
  template: `
    <div class="page-header">
      <div>
        <h2 class="page-title">Usuarios</h2>
        <p class="page-subtitle">Cuentas de su alcance y sus roles de acceso</p>
      </div>
      <div class="page-actions">
        @if (asignables().size > 0) {
          <a routerLink="/usuarios/nuevo" class="btn btn-primary"><i class="bi bi-person-plus-fill"></i> Nuevo usuario</a>
        }
      </div>
    </div>

    <div class="filtros-bar">
      <div class="filtro-campo">
        <label for="buscar">Buscar</label>
        <input id="buscar" class="input" [ngModel]="texto()" (ngModelChange)="texto.set($event)" placeholder="Usuario, nombre o correo" />
      </div>
      <div class="filtro-campo">
        <label for="rol">Rol</label>
        <select id="rol" class="input" [ngModel]="rol()" (ngModelChange)="rol.set($event)">
          <option [ngValue]="null">Todos</option>
          @for (r of rolesEnLista(); track r.codigo) { <option [ngValue]="r.codigo">{{ r.nombre }}</option> }
        </select>
      </div>
      <div class="filtro-campo">
        <label for="estado">Estado</label>
        <select id="estado" class="input" [ngModel]="estado()" (ngModelChange)="estado.set($event)">
          <option value="todos">Todos</option>
          <option value="activos">Activos</option>
          <option value="inactivos">Inactivos</option>
        </select>
      </div>
      <span class="filtro-contador">{{ filtrados().length }} usuario(s)</span>
    </div>

    @if (error()) { <div class="alert-error">{{ error() }}</div> }

    @if (cargando()) {
      <app-loader label="Cargando usuarios..." />
    } @else {
      <div class="table-card">
        <table class="data-table">
          <thead>
            <tr><th>Usuario</th><th>Nombre</th><th>Roles</th><th>IPRESS / Red</th><th>Estado</th><th>Último acceso</th><th></th></tr>
          </thead>
          <tbody>
            @for (u of filtrados(); track u.id) {
              <tr>
                <td>{{ u.username }}<span class="celda-sub">{{ u.email }}</span></td>
                <td>{{ u.nombre }} {{ u.apellido }}</td>
                <td>@for (r of u.roles; track r.codigo) { <span class="badge badge-blue">{{ r.nombre }}</span> }</td>
                <td>{{ u.ipressNombre ?? u.redNombre ?? 'Nacional' }}</td>
                <td>
                  @if (solicitudSinResolver(u)) {
                    <span class="badge badge-yellow">Solicitud {{ u.registroEstado === 'RECHAZADO' ? 'rechazada' : 'pendiente' }}</span>
                  } @else {
                    <span class="badge" [class.badge-green]="u.activo" [class.badge-gray]="!u.activo">{{ u.activo ? 'Activo' : 'Inactivo' }}</span>
                  }
                </td>
                <td>{{ u.ultimoAcceso ? (u.ultimoAcceso | date: 'dd/MM/yyyy HH:mm') : '—' }}</td>
                <td>
                  <div class="acciones">
                    @if (u.id === miId()) {
                      <a [routerLink]="['/usuarios', u.id, 'editar']" class="btn btn-outline btn-sm">Mis datos</a>
                    } @else if (puedeAdministrar(u)) {
                      @if (solicitudSinResolver(u)) {
                        @if (auth.can('usuarios.solicitudes')) {
                          <a routerLink="/usuarios/solicitudes" class="btn btn-outline btn-sm">Ver solicitud</a>
                        }
                      } @else {
                        <a [routerLink]="['/usuarios', u.id, 'editar']" class="btn btn-outline btn-sm">Editar</a>
                        <button class="btn btn-warning btn-sm" [disabled]="ocupado() === u.id" (click)="restablecer(u)">
                          <i class="bi bi-key-fill"></i> Restablecer
                        </button>
                        <button class="btn btn-sm" [class.btn-danger]="u.activo" [class.btn-success]="!u.activo"
                                [disabled]="ocupado() === u.id" (click)="cambiarEstado(u)">
                          {{ u.activo ? 'Desactivar' : 'Activar' }}
                        </button>
                      }
                    } @else {
                      <span class="celda-sub" title="Este usuario lo administra otro rol">—</span>
                    }
                  </div>
                </td>
              </tr>
            } @empty {
              <tr><td colspan="7"><div class="empty-state"><i class="bi bi-people empty-state-icon"></i>
                <span class="empty-state-title">No hay usuarios que coincidan</span></div></td></tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class UsuarioListComponent implements OnInit {
  readonly auth = inject(AuthStore);
  private readonly usuarios = inject(UsuarioService);
  private readonly modal = inject(ModalService);
  private readonly toast = inject(ToastService);

  readonly lista = signal<Usuario[]>([]);
  readonly asignables = signal(new Set<RolCodigo>());
  readonly cargando = signal(true);
  readonly error = signal('');
  readonly ocupado = signal<number | null>(null);

  readonly texto = signal('');
  readonly rol = signal<RolCodigo | null>(null);
  readonly estado = signal<'todos' | 'activos' | 'inactivos'>('todos');

  readonly miId = computed(() => this.auth.user()?.id ?? null);

  readonly rolesEnLista = computed(() => {
    const vistos = new Map<string, Rol>();
    this.lista().forEach((u) => u.roles.forEach((r) => vistos.set(r.codigo, r)));
    return [...vistos.values()];
  });

  readonly filtrados = computed(() => {
    const t = this.texto().trim().toLowerCase();
    return this.lista().filter((u) =>
      (!t || `${u.username} ${u.nombre} ${u.apellido} ${u.email}`.toLowerCase().includes(t))
      && (!this.rol() || u.roles.some((r) => r.codigo === this.rol()))
      && (this.estado() === 'todos' || (this.estado() === 'activos') === u.activo));
  });

  ngOnInit(): void {
    forkJoin({ usuarios: this.usuarios.listar(), roles: this.usuarios.rolesAsignables() }).subscribe({
      next: ({ usuarios, roles }) => {
        this.lista.set(usuarios);
        this.asignables.set(new Set(roles.map((r) => r.codigo)));
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(mensajeDeError(err, 'No se pudo cargar el listado de usuarios.'));
        this.cargando.set(false);
      },
    });
  }

  puedeAdministrar(u: Usuario): boolean {
    return u.roles.every((r) => this.asignables().has(r.codigo));
  }

  solicitudSinResolver(u: Usuario): boolean {
    return u.autoregistrado && u.registroEstado !== 'APROBADO';
  }

  async restablecer(u: Usuario): Promise<void> {
    const ok = await this.modal.confirm(
      `Se invalidará la contraseña de ${u.nombre} ${u.apellido} y se le enviará un enlace a ${u.email} para fijar una nueva.`,
      'Restablecer contraseña', 'Restablecer');
    if (!ok) return;
    this.ocupado.set(u.id);
    this.usuarios.restablecerPassword(u.id).subscribe({
      next: (r) => {
        this.ocupado.set(null);
        this.toast.success(r.message);
      },
      error: (err) => {
        this.ocupado.set(null);
        this.toast.error(mensajeDeError(err));
      },
    });
  }

  async cambiarEstado(u: Usuario): Promise<void> {
    const accion = u.activo ? 'Desactivar' : 'Activar';
    const ok = await this.modal.confirm(
      u.activo
        ? `${u.nombre} ${u.apellido} no podrá ingresar y sus sesiones abiertas se cerrarán.`
        : `${u.nombre} ${u.apellido} podrá volver a ingresar.`,
      `${accion} usuario`, accion);
    if (!ok) return;
    this.ocupado.set(u.id);
    this.usuarios.cambiarEstado(u.id, !u.activo).subscribe({
      next: (actualizado) => {
        this.lista.update((l) => l.map((x) => (x.id === actualizado.id ? actualizado : x)));
        this.ocupado.set(null);
        this.toast.success(`Usuario ${actualizado.activo ? 'activado' : 'desactivado'}.`);
      },
      error: (err) => {
        this.ocupado.set(null);
        this.toast.error(mensajeDeError(err));
      },
    });
  }
}
