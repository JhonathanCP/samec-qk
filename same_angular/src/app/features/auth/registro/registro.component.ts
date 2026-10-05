import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { RolCodigo } from '../../../core/models/sesion.model';
import { Rol } from '../../../core/models/usuario.model';
import { OrganizacionService } from '../../../core/services/organizacion.service';
import { RecaptchaService } from '../../../core/services/recaptcha.service';
import { UsuarioService } from '../../../core/services/usuario.service';
import { mensajeDeError } from '../../../core/utils/http-error';
import { SearchableSelectComponent } from '../../../shared/components/searchable-select/searchable-select.component';
import { PASSWORD_AYUDA, passwordSegura, passwordsIguales } from '../password.validators';

/**
 * Autorregistro: la persona pide su cuenta y queda PENDIENTE hasta que el Gestor de Calidad de
 * Sede Central la apruebe (Solicitudes de registro). Su usuario será su correo sin el dominio.
 */
@Component({
  selector: 'app-registro',
  imports: [ReactiveFormsModule, RouterLink, SearchableSelectComponent],
  styleUrl: '../auth-page.css',
  template: `
    <div class="login-wrapper desde-arriba">
      <div class="login-container">
        <div class="login-column ancha">
          <div class="login-title-block">
            <div class="login-badge" aria-hidden="true"><i class="bi bi-person-plus-fill"></i></div>
            <h1>Solicitar cuenta</h1>
            <p>SAMEC · Oficina de Gestión de la Calidad y Humanización</p>
          </div>

          <div class="login-card">
            @if (!enviado()) {
              <form [formGroup]="form" (ngSubmit)="enviar()" class="login-form" novalidate>
                <div class="two-col">
                  <div class="field-group">
                    <label for="nombre">Nombre</label>
                    <div class="input-group"><input id="nombre" formControlName="nombre" type="text" /></div>
                  </div>
                  <div class="field-group">
                    <label for="apellido">Apellido</label>
                    <div class="input-group"><input id="apellido" formControlName="apellido" type="text" /></div>
                  </div>
                </div>

                <div class="field-group">
                  <label for="correo">Correo institucional</label>
                  <div class="input-group">
                    <span class="input-icon" aria-hidden="true"><i class="bi bi-envelope-fill"></i></span>
                    <input id="correo" formControlName="email" type="text" placeholder="nombre.apellido" autocomplete="username" />
                    <span class="email-suffix">&#64;{{ dominio }}</span>
                  </div>
                  <span class="field-hint">Su usuario para ingresar será esta misma dirección, sin el dominio.</span>
                </div>

                <div class="two-col">
                  <div class="field-group">
                    <label for="pass">Contraseña</label>
                    <div class="input-group">
                      <input id="pass" formControlName="password" [type]="verPassword() ? 'text' : 'password'" autocomplete="new-password" />
                      <button type="button" class="show-pass" (click)="verPassword.set(!verPassword())" aria-label="Mostrar u ocultar contraseña">
                        <i [class]="verPassword() ? 'bi bi-eye-slash-fill' : 'bi bi-eye-fill'"></i>
                      </button>
                    </div>
                  </div>
                  <div class="field-group">
                    <label for="pass2">Confirmar contraseña</label>
                    <div class="input-group">
                      <input id="pass2" formControlName="passwordConfirmar" [type]="verPassword() ? 'text' : 'password'" autocomplete="new-password" />
                    </div>
                  </div>
                </div>
                <span class="field-hint">{{ ayudaPassword }}</span>
                @if (form.hasError('passwordsDistintas') && form.controls.passwordConfirmar.touched) {
                  <span class="error-msg">Las contraseñas no coinciden.</span>
                }

                <div class="field-group">
                  <label>¿Qué rol va a desempeñar?</label>
                  <div class="roles-grid">
                    @for (rol of roles(); track rol.codigo) {
                      <label class="role-option" [class.selected]="form.value.rol === rol.codigo">
                        <input type="radio" formControlName="rol" [value]="rol.codigo" (change)="alCambiarRol()" />
                        {{ rol.nombre }}
                      </label>
                    }
                  </div>
                </div>

                @if (rolSeleccionado(); as rol) {
                  <div class="field-group">
                    <label>Red Asistencial</label>
                    <app-select [options]="opcionesRed()" formControlName="idRed" (selChange)="form.patchValue({ idIpress: null })"
                                placeholder="-- Seleccione su Red --" />
                  </div>
                  @if (rol.alcance === 'IPRESS') {
                    <div class="field-group">
                      <label>IPRESS</label>
                      <app-select [options]="opcionesIpress()" formControlName="idIpress" placeholder="-- Seleccione su IPRESS --" />
                      @if (!form.value.idRed) { <span class="field-hint">Seleccione primero su Red para ver sus IPRESS.</span> }
                    </div>
                  }
                }

                @if (error()) { <div class="error-alert"><i class="bi bi-exclamation-triangle-fill"></i> {{ error() }}</div> }

                <button type="submit" class="btn-login" [disabled]="enviando() || !valido()">
                  @if (enviando()) { Enviando... } @else { <i class="bi bi-send-fill"></i> Enviar solicitud }
                </button>
              </form>
              <a routerLink="/auth/login" class="register-link"><i class="bi bi-arrow-left"></i> ¿Ya tiene cuenta? Inicie sesión</a>
            } @else {
              <div class="success-block">
                <i class="bi bi-check-circle-fill success-icon"></i>
                <h3>Solicitud enviada</h3>
                <p>Su solicitud quedó <b>pendiente</b> de revisión por el Gestor de Calidad de Sede Central.
                  Le avisaremos por correo; una vez aprobada, podrá ingresar con su usuario y la contraseña que registró.</p>
                <a routerLink="/auth/login" class="btn-login"><i class="bi bi-box-arrow-in-right"></i> Volver al inicio de sesión</a>
              </div>
            }
          </div>
        </div>
      </div>
    </div>
  `,
})
export class RegistroComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly usuarios = inject(UsuarioService);
  private readonly organizacion = inject(OrganizacionService);
  private readonly recaptcha = inject(RecaptchaService);

  readonly dominio = environment.dominioCorreo;
  readonly ayudaPassword = PASSWORD_AYUDA;
  readonly verPassword = signal(false);
  readonly enviando = signal(false);
  readonly enviado = signal(false);
  readonly error = signal('');

  readonly roles = toSignal(this.usuarios.rolesAutorregistro(), { initialValue: [] as Rol[] });
  private readonly redes = toSignal(this.organizacion.redes(), { initialValue: [] });
  private readonly ipress = toSignal(this.organizacion.ipress(), { initialValue: [] });

  readonly form = this.fb.group(
    {
      nombre: ['', Validators.required],
      apellido: ['', Validators.required],
      email: ['', Validators.required],
      password: ['', [Validators.required, passwordSegura]],
      passwordConfirmar: ['', Validators.required],
      rol: ['' as RolCodigo | '', Validators.required],
      idRed: [null as number | null, Validators.required],
      idIpress: [null as number | null],
    },
    { validators: passwordsIguales },
  );

  private readonly valores = toSignal(this.form.valueChanges, { initialValue: this.form.value });

  readonly rolSeleccionado = computed(() => this.roles().find((r) => r.codigo === this.valores().rol) ?? null);
  readonly opcionesRed = computed(() => this.redes().map((r) => ({ value: r.id, label: r.nombre })));
  readonly opcionesIpress = computed(() => {
    const idRed = Number(this.valores().idRed ?? 0);
    return this.ipress().filter((i) => i.idRed === idRed).map((i) => ({ value: i.id, label: i.nombre }));
  });

  ngOnInit(): void {
    this.recaptcha.mostrarSello(true);
  }

  ngOnDestroy(): void {
    this.recaptcha.mostrarSello(false);
  }

  alCambiarRol(): void {
    this.form.patchValue({ idIpress: null });
  }

  valido(): boolean {
    return this.form.valid && (this.rolSeleccionado()?.alcance !== 'IPRESS' || !!this.form.value.idIpress);
  }

  async enviar(): Promise<void> {
    if (!this.valido()) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set('');
    this.enviando.set(true);
    const v = this.form.getRawValue();
    const local = (v.email ?? '').trim().split('@')[0];
    this.usuarios.autorregistro({
      nombre: v.nombre!.trim(),
      apellido: v.apellido!.trim(),
      email: `${local}@${this.dominio}`,
      password: v.password!,
      rol: v.rol as RolCodigo,
      idRed: v.idRed ? Number(v.idRed) : null,
      idIpress: this.rolSeleccionado()?.alcance === 'IPRESS' && v.idIpress ? Number(v.idIpress) : null,
      recaptchaToken: await this.recaptcha.token('registro'),
    }).subscribe({
      next: () => {
        this.enviando.set(false);
        this.enviado.set(true);
      },
      error: (err) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(err, 'No se pudo registrar la solicitud. Intente nuevamente.'));
      },
    });
  }
}
