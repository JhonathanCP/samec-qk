import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthApiService } from '../../../core/services/auth-api.service';
import { RecaptchaService } from '../../../core/services/recaptcha.service';
import { mensajeDeError } from '../../../core/utils/http-error';
import { LoaderComponent } from '../../../shared/components/loader/loader.component';
import { PASSWORD_AYUDA, passwordSegura, passwordsIguales } from '../password.validators';

type Paso = 'solicitar' | 'confirmar' | 'listo';

/**
 * Recuperar (o establecer por primera vez) la contraseña: se pide un código de 8 dígitos al
 * correo institucional y con él se fija la nueva. Las cuentas nuevas llegan aquí desde el correo
 * de bienvenida, con ?usuario=... ya escrito.
 */
@Component({
  selector: 'app-olvide-password',
  imports: [ReactiveFormsModule, RouterLink, LoaderComponent],
  styleUrl: '../auth-page.css',
  template: `
    <div class="login-wrapper">
      <div class="login-container">
        <div class="login-column">
          <div class="login-title-block">
            <div class="login-badge" aria-hidden="true"><i class="bi bi-key-fill"></i></div>
            <h1>Establecer contraseña</h1>
            <p>SAMEC · Oficina de Gestión de la Calidad y Humanización</p>
          </div>

          <div class="login-card">
            @switch (paso()) {
              @case ('solicitar') {
                <form [formGroup]="formSolicitar" (ngSubmit)="solicitar()" class="login-form" novalidate>
                  <p class="hint-text">Ingrese su usuario o correo institucional y le enviaremos un código de un solo uso.</p>
                  <div class="field-group">
                    <label for="usuario">Usuario o correo</label>
                    <div class="input-group">
                      <span class="input-icon" aria-hidden="true"><i class="bi bi-person-fill"></i></span>
                      <input id="usuario" formControlName="username" type="text" placeholder="nombre.apellido" autocomplete="username" />
                    </div>
                  </div>
                  @if (error()) { <div class="error-alert"><i class="bi bi-exclamation-triangle-fill"></i> {{ error() }}</div> }
                  <button type="submit" class="btn-login" [disabled]="cargando() || formSolicitar.invalid">
                    @if (cargando()) { <app-loader size="sm" [compact]="true" [onDark]="true" label="Enviando..." /> }
                    @else { <i class="bi bi-send-fill"></i> Enviar código }
                  </button>
                </form>
              }
              @case ('confirmar') {
                <form [formGroup]="formConfirmar" (ngSubmit)="confirmar()" class="login-form" novalidate>
                  <p class="hint-text">Si la cuenta existe, enviamos un código de 8 dígitos a su correo (vence en pocos minutos). Ingréselo junto con su nueva contraseña.</p>
                  <div class="field-group">
                    <label for="otp">Código recibido</label>
                    <div class="input-group">
                      <span class="input-icon" aria-hidden="true"><i class="bi bi-shield-lock-fill"></i></span>
                      <input id="otp" formControlName="otp" type="text" inputmode="numeric" maxlength="8" placeholder="00000000" autocomplete="one-time-code" />
                    </div>
                  </div>
                  <div class="field-group">
                    <label for="nueva">Nueva contraseña</label>
                    <div class="input-group">
                      <span class="input-icon" aria-hidden="true"><i class="bi bi-lock-fill"></i></span>
                      <input id="nueva" formControlName="password" [type]="verPassword() ? 'text' : 'password'" autocomplete="new-password" />
                      <button type="button" class="show-pass" (click)="verPassword.set(!verPassword())" aria-label="Mostrar u ocultar contraseña">
                        <i [class]="verPassword() ? 'bi bi-eye-slash-fill' : 'bi bi-eye-fill'"></i>
                      </button>
                    </div>
                    <span class="field-hint">{{ ayudaPassword }}</span>
                  </div>
                  <div class="field-group">
                    <label for="confirmar">Confirmar contraseña</label>
                    <div class="input-group">
                      <span class="input-icon" aria-hidden="true"><i class="bi bi-lock-fill"></i></span>
                      <input id="confirmar" formControlName="passwordConfirmar" [type]="verPassword() ? 'text' : 'password'" autocomplete="new-password" />
                    </div>
                    @if (formConfirmar.hasError('passwordsDistintas') && formConfirmar.controls.passwordConfirmar.touched) {
                      <span class="error-msg">Las contraseñas no coinciden</span>
                    }
                  </div>
                  @if (error()) { <div class="error-alert"><i class="bi bi-exclamation-triangle-fill"></i> {{ error() }}</div> }
                  <button type="submit" class="btn-login" [disabled]="cargando() || formConfirmar.invalid">
                    @if (cargando()) { <app-loader size="sm" [compact]="true" [onDark]="true" label="Verificando..." /> }
                    @else { <i class="bi bi-check-circle-fill"></i> Guardar contraseña }
                  </button>
                  <button type="button" class="link-btn" (click)="reiniciar()">¿No le llegó el código? Solicitar de nuevo</button>
                </form>
              }
              @case ('listo') {
                <div class="success-block">
                  <i class="bi bi-check-circle-fill success-icon"></i>
                  <h3>Contraseña actualizada</h3>
                  <p>Ya puede iniciar sesión con su nueva contraseña.</p>
                  <a routerLink="/auth/login" class="btn-login"><i class="bi bi-box-arrow-in-right"></i> Ir a iniciar sesión</a>
                </div>
              }
            }
            @if (paso() !== 'listo') {
              <a routerLink="/auth/login" class="register-link"><i class="bi bi-arrow-left"></i> Volver a iniciar sesión</a>
            }
          </div>
        </div>
      </div>
    </div>
  `,
})
export class OlvidePasswordComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly authApi = inject(AuthApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly recaptcha = inject(RecaptchaService);

  readonly ayudaPassword = PASSWORD_AYUDA;
  readonly paso = signal<Paso>('solicitar');
  readonly cargando = signal(false);
  readonly error = signal('');
  readonly verPassword = signal(false);
  private usuarioSolicitado = '';

  readonly formSolicitar = this.fb.nonNullable.group({ username: ['', Validators.required] });
  readonly formConfirmar = this.fb.nonNullable.group(
    {
      otp: ['', [Validators.required, Validators.pattern(/^\d{8}$/)]],
      password: ['', [Validators.required, passwordSegura]],
      passwordConfirmar: ['', Validators.required],
    },
    { validators: passwordsIguales },
  );

  ngOnInit(): void {
    this.recaptcha.mostrarSello(true);
    const usuario = this.route.snapshot.queryParamMap.get('usuario');
    if (usuario) this.formSolicitar.patchValue({ username: usuario });
  }

  ngOnDestroy(): void {
    this.recaptcha.mostrarSello(false);
  }

  async solicitar(): Promise<void> {
    if (this.formSolicitar.invalid) return;
    this.cargando.set(true);
    this.error.set('');
    const username = this.formSolicitar.getRawValue().username.trim();
    const token = await this.recaptcha.token('olvide_password');
    this.authApi.olvidePassword(username, token).subscribe({
      next: () => {
        this.usuarioSolicitado = username;
        this.paso.set('confirmar');
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(mensajeDeError(err, 'No se pudo procesar la solicitud. Intente nuevamente.'));
        this.cargando.set(false);
      },
    });
  }

  confirmar(): void {
    if (this.formConfirmar.invalid) return;
    this.cargando.set(true);
    this.error.set('');
    const { otp, password } = this.formConfirmar.getRawValue();
    this.authApi.restablecerPassword(this.usuarioSolicitado, otp, password).subscribe({
      next: () => {
        this.cargando.set(false);
        this.paso.set('listo');
      },
      error: (err) => {
        this.error.set(mensajeDeError(err, 'No se pudo verificar el código. Intente nuevamente.'));
        this.cargando.set(false);
      },
    });
  }

  reiniciar(): void {
    this.formConfirmar.reset();
    this.error.set('');
    this.paso.set('solicitar');
  }
}
