import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { Alcance, RolCodigo } from '../../../core/models/sesion.model';
import { EntradaBitacora, Rol, Usuario } from '../../../core/models/usuario.model';
import { OrganizacionService } from '../../../core/services/organizacion.service';
import { ToastService } from '../../../core/services/toast.service';
import { UsuarioService } from '../../../core/services/usuario.service';
import { AuthStore } from '../../../core/stores/auth.store';
import { mensajeDeError } from '../../../core/utils/http-error';
import { LoaderComponent } from '../../../shared/components/loader/loader.component';
import { SearchableSelectComponent } from '../../../shared/components/searchable-select/searchable-select.component';

/**
 * Alta y edición de usuarios. La ubicación depende de los roles: un rol de IPRESS pide IPRESS, uno
 * de Red pide Red y los nacionales ninguna. Quien tiene alcance de IPRESS solo crea en la suya y
 * quien tiene alcance de Red solo en IPRESS de su Red (el core lo vuelve a validar).
 */
@Component({
  selector: 'app-usuario-form',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, LoaderComponent, SearchableSelectComponent],
  template: `
    <div class="page-header">
      <div>
        <h2 class="page-title">{{ titulo() }}</h2>
        <p class="page-subtitle">
          @if (esNuevo()) { La persona recibirá un correo para establecer su contraseña. }
          @else if (esElMismo()) { Puede corregir sus datos; sus roles y ubicación los cambia un administrador. }
          @else { Los cambios de rol se aplican desde la siguiente acción del usuario. }
        </p>
      </div>
      <div class="page-actions">
        <a routerLink="/usuarios" class="btn btn-secondary"><i class="bi bi-arrow-left"></i> Volver</a>
      </div>
    </div>

    @if (cargando()) {
      <app-loader label="Cargando..." />
    } @else {
      <form class="form-card" [formGroup]="form" (ngSubmit)="guardar()" novalidate>
        <div class="form-grid">
          <div class="field-group">
            <label for="username">Usuario</label>
            <input id="username" class="input" formControlName="username" placeholder="nombre.apellido" />
            @if (esNuevo()) { <span class="field-hint">Con este usuario ingresará al sistema.</span> }
          </div>
          <div class="field-group">
            <label for="email">Correo</label>
            <input id="email" class="input" formControlName="email" type="email" placeholder="correo@essalud.gob.pe" />
          </div>
          <div class="field-group">
            <label for="nombre">Nombre</label>
            <input id="nombre" class="input" formControlName="nombre" />
          </div>
          <div class="field-group">
            <label for="apellido">Apellido</label>
            <input id="apellido" class="input" formControlName="apellido" />
          </div>

          <div class="field-group full">
            <label>Roles</label>
            @if (esElMismo()) {
              <div>@for (r of usuario()?.roles ?? []; track r.codigo) { <span class="badge badge-blue">{{ r.nombre }}</span> }</div>
            } @else {
              <div class="check-list">
                @for (r of rolesDisponibles(); track r.codigo) {
                  <label class="check-item" [class.selected]="seleccionados().has(r.codigo)">
                    <input type="checkbox" [checked]="seleccionados().has(r.codigo)" (change)="alternarRol(r.codigo)" />
                    <span>{{ r.nombre }}<small>{{ r.descripcion }}</small></span>
                  </label>
                }
              </div>
            }
          </div>

          @if (!esElMismo()) {
            @if (pideRed()) {
              <div class="field-group" [class.full]="alcanceRoles() === 'RED'">
                <label>Red Asistencial</label>
                <app-select [options]="opcionesRed()" formControlName="idRed" placeholder="-- Seleccione la Red --"
                            (selChange)="form.patchValue({ idIpress: null })" />
              </div>
            }
            @if (pideIpress()) {
              <div class="field-group">
                <label>IPRESS</label>
                <app-select [options]="opcionesIpress()" formControlName="idIpress" placeholder="-- Seleccione la IPRESS --" />
              </div>
            }
            @if (alcanceRoles() === 'IPRESS' && auth.alcance() === 'IPRESS') {
              <div class="field-group full"><span class="field-hint">Se registrará en su IPRESS: {{ auth.user()?.ipressNombre }}.</span></div>
            }
          }
        </div>

        @if (error()) { <div class="alert-error" style="margin-top:16px">{{ error() }}</div> }

        <div class="form-actions">
          <a routerLink="/usuarios" class="btn btn-secondary">Cancelar</a>
          <button type="submit" class="btn btn-primary" [disabled]="guardando() || !valido()">
            <i class="bi bi-check2"></i> {{ guardando() ? 'Guardando...' : 'Guardar' }}
          </button>
        </div>
      </form>

      @if (bitacora().length) {
        <div class="card historial">
          <h3 class="section-title">Historial de cambios</h3>
          <ul>
            @for (b of bitacora(); track b.id) {
              <li><b>{{ b.accion }}</b> · {{ b.usuarioNombre }} · {{ b.fecha | date: 'dd/MM/yyyy HH:mm' }}
                @if (b.detalle) { <span class="celda-sub">{{ b.detalle }}</span> }</li>
            }
          </ul>
        </div>
      }
    }
  `,
  styles: [`
    .historial { margin-top: 16px; }
    .historial ul { list-style: none; display: flex; flex-direction: column; gap: 8px; font-size: 13px; color: var(--gray-700); }
  `],
})
export class UsuarioFormComponent implements OnInit {
  /** Viene de la ruta :id/editar (withComponentInputBinding). */
  readonly id = input<string>();

  readonly auth = inject(AuthStore);
  private readonly fb = inject(FormBuilder);
  private readonly usuarios = inject(UsuarioService);
  private readonly organizacion = inject(OrganizacionService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly error = signal('');
  readonly usuario = signal<Usuario | null>(null);
  readonly rolesDisponibles = signal<Rol[]>([]);
  readonly seleccionados = signal(new Set<RolCodigo>());
  readonly bitacora = signal<EntradaBitacora[]>([]);

  private readonly redes = toSignal(this.organizacion.redes(), { initialValue: [] });
  private readonly ipress = toSignal(this.organizacion.ipress(), { initialValue: [] });

  readonly form = this.fb.group({
    username: ['', [Validators.required, Validators.maxLength(80)]],
    email: ['', [Validators.required, Validators.email]],
    nombre: ['', Validators.required],
    apellido: ['', Validators.required],
    idRed: [null as number | null],
    idIpress: [null as number | null],
  });
  private readonly valores = toSignal(this.form.valueChanges, { initialValue: this.form.value });

  readonly esNuevo = computed(() => !this.id());
  readonly esElMismo = computed(() => !!this.usuario() && this.usuario()!.id === this.auth.user()?.id);
  readonly titulo = computed(() => (this.esNuevo() ? 'Nuevo usuario' : this.esElMismo() ? 'Mis datos' : 'Editar usuario'));

  /** Alcance que exigen los roles marcados: IPRESS tiene precedencia sobre RED. */
  readonly alcanceRoles = computed<Alcance>(() => {
    const alcances = this.rolesDisponibles().filter((r) => this.seleccionados().has(r.codigo)).map((r) => r.alcance);
    return alcances.includes('IPRESS') ? 'IPRESS' : alcances.includes('RED') ? 'RED' : 'NACIONAL';
  });
  /** La Red se elige si el rol la pide, o para filtrar IPRESS cuando quien crea es nacional. */
  readonly pideRed = computed(() =>
    this.auth.alcance() === 'NACIONAL' && (this.alcanceRoles() === 'RED' || this.alcanceRoles() === 'IPRESS'));
  readonly pideIpress = computed(() => this.alcanceRoles() === 'IPRESS' && this.auth.alcance() !== 'IPRESS');

  readonly opcionesRed = computed(() => this.redes().map((r) => ({ value: r.id, label: r.nombre })));
  readonly opcionesIpress = computed(() => {
    const idRed = this.auth.alcance() === 'RED' ? this.auth.user()?.idRed : Number(this.valores().idRed ?? 0);
    return this.ipress().filter((i) => i.idRed === idRed).map((i) => ({ value: i.id, label: i.nombre }));
  });

  ngOnInit(): void {
    const id = this.id() ? Number(this.id()) : null;
    forkJoin({
      roles: this.usuarios.rolesAsignables(),
      usuario: id ? this.usuarios.obtener(id) : of(null),
      bitacora: id ? this.usuarios.bitacora(id) : of([]),
    }).subscribe({
      next: ({ roles, usuario, bitacora }) => {
        this.rolesDisponibles.set(roles);
        this.bitacora.set(bitacora);
        if (usuario) {
          this.usuario.set(usuario);
          this.seleccionados.set(new Set(usuario.roles.map((r) => r.codigo)));
          this.form.patchValue({
            username: usuario.username, email: usuario.email, nombre: usuario.nombre,
            apellido: usuario.apellido, idRed: usuario.idRed, idIpress: usuario.idIpress,
          });
          this.form.controls.username.disable();
        }
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(mensajeDeError(err, 'No se pudo cargar el usuario.'));
        this.cargando.set(false);
      },
    });
  }

  alternarRol(codigo: RolCodigo): void {
    const s = new Set(this.seleccionados());
    if (s.has(codigo)) s.delete(codigo);
    else s.add(codigo);
    this.seleccionados.set(s);
  }

  valido(): boolean {
    if (this.form.invalid) return false;
    if (this.esElMismo()) return true;
    if (this.seleccionados().size === 0) return false;
    if (this.alcanceRoles() === 'RED' && this.pideRed() && !this.valores().idRed) return false;
    return !(this.pideIpress() && !this.valores().idIpress);
  }

  guardar(): void {
    if (!this.valido()) return;
    this.guardando.set(true);
    this.error.set('');
    const v = this.form.getRawValue();
    const u = this.usuario();
    const req = {
      username: (v.username ?? '').trim(),
      nombre: (v.nombre ?? '').trim(),
      apellido: (v.apellido ?? '').trim(),
      email: (v.email ?? '').trim(),
      // Sobre sí mismo se reenvía lo que ya tiene: el core rechaza cambios de rol o ubicación.
      roles: this.esElMismo() ? u!.roles.map((r) => r.codigo) : [...this.seleccionados()],
      idRed: this.esElMismo() ? u!.idRed : this.alcanceRoles() === 'RED' ? this.idRedElegida() : null,
      idIpress: this.esElMismo() ? u!.idIpress : this.alcanceRoles() === 'IPRESS' ? this.idIpressElegida() : null,
    };
    const accion = u ? this.usuarios.actualizar(u.id, req) : this.usuarios.crear(req);
    accion.subscribe({
      next: () => {
        this.toast.success(u ? 'Usuario actualizado.' : 'Usuario creado. Se le envió un correo para establecer su contraseña.');
        this.router.navigate(['/usuarios']);
      },
      error: (err) => {
        this.guardando.set(false);
        this.error.set(mensajeDeError(err));
      },
    });
  }

  private idRedElegida(): number | null {
    return this.auth.alcance() === 'NACIONAL' ? Number(this.valores().idRed) || null : this.auth.user()?.idRed ?? null;
  }

  private idIpressElegida(): number | null {
    return this.auth.alcance() === 'IPRESS' ? this.auth.user()?.idIpress ?? null : Number(this.valores().idIpress) || null;
  }
}
