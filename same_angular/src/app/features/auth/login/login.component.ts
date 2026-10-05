import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthApiService } from '../../../core/services/auth-api.service';
import { RecaptchaService } from '../../../core/services/recaptcha.service';
import { AuthStore } from '../../../core/stores/auth.store';
import { mensajeDeError } from '../../../core/utils/http-error';
import { LoaderComponent } from '../../../shared/components/loader/loader.component';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, LoaderComponent],
  styleUrl: '../auth-page.css',
  template: `
    <div class="login-wrapper">
      <div class="login-container">
        <div class="login-column">
          <div class="login-title-block">
            <div class="login-badge" aria-hidden="true"><i class="bi bi-shield-fill-check"></i></div>
            <h1>SAMEC</h1>
            <p>Oficina de Gestión de la Calidad y Humanización</p>
          </div>

          <div class="login-card">
            <form [formGroup]="form" (ngSubmit)="ingresar()" class="login-form" novalidate>
              <div class="field-group">
                <label for="username">Usuario o correo institucional</label>
                <div class="input-group">
                  <span class="input-icon" aria-hidden="true"><i class="bi bi-person-fill"></i></span>
                  <input id="username" formControlName="username" type="text" placeholder="nombre.apellido"
                         autocomplete="username" [class.error]="invalido('username')" />
                </div>
                @if (invalido('username')) { <span class="error-msg">Ingrese su usuario o correo</span> }
              </div>

              <div class="field-group">
                <label for="password">Contraseña</label>
                <div class="input-group">
                  <span class="input-icon" aria-hidden="true"><i class="bi bi-lock-fill"></i></span>
                  <input id="password" formControlName="password" [type]="verPassword() ? 'text' : 'password'"
                         placeholder="Ingrese su contraseña" autocomplete="current-password"
                         [class.error]="invalido('password')" />
                  <button type="button" class="show-pass" (click)="verPassword.set(!verPassword())" aria-label="Mostrar u ocultar contraseña">
                    <i [class]="verPassword() ? 'bi bi-eye-slash-fill' : 'bi bi-eye-fill'"></i>
                  </button>
                </div>
                @if (invalido('password')) { <span class="error-msg">Ingrese su contraseña</span> }
                <a routerLink="/auth/olvide-password" class="forgot-link">¿Olvidó su contraseña?</a>
              </div>

              @if (error()) { <div class="error-alert"><i class="bi bi-exclamation-triangle-fill"></i> {{ error() }}</div> }

              <button type="submit" class="btn-login" [disabled]="cargando() || form.invalid">
                @if (cargando()) {
                  <app-loader size="sm" [compact]="true" [onDark]="true" label="Ingresando..." />
                } @else {
                  <i class="bi bi-box-arrow-in-right"></i> Iniciar sesión
                }
              </button>
            </form>

            <a routerLink="/auth/registro" class="register-link">
              <i class="bi bi-person-plus"></i> ¿No tiene cuenta? Solicítela aquí
            </a>
            <footer class="login-footer">© {{ anio }} EsSalud · Oficina de Gestión de la Calidad y Humanización</footer>
          </div>
        </div>
      </div>
    </div>
  `,
})
export class LoginComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly authApi = inject(AuthApiService);
  private readonly authStore = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly recaptcha = inject(RecaptchaService);

  readonly anio = new Date().getFullYear();
  readonly cargando = signal(false);
  readonly error = signal('');
  readonly verPassword = signal(false);

  readonly form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  ngOnInit(): void {
    this.recaptcha.mostrarSello(true);
  }

  ngOnDestroy(): void {
    this.recaptcha.mostrarSello(false);
  }

  async ingresar(): Promise<void> {
    if (this.form.invalid) return;
    this.cargando.set(true);
    this.error.set('');

    const recaptchaToken = await this.recaptcha.token('login');
    const { username, password } = this.form.getRawValue();
    this.authApi.login({ username: username.trim(), password, recaptchaToken }).subscribe({
      next: (res) => {
        this.authStore.setSession(res.user, res.accessToken);
        const volver = this.route.snapshot.queryParamMap.get('volver');
        this.router.navigateByUrl(volver && volver.startsWith('/') && !volver.startsWith('/auth') ? volver : '/inicio');
      },
      error: (err) => {
        // 401 credenciales, 403 cuenta pendiente/rechazada/desactivada, 429 bloqueo: el core manda el texto.
        this.error.set(mensajeDeError(err, 'No se pudo iniciar sesión. Intente nuevamente.'));
        this.cargando.set(false);
      },
    });
  }

  invalido(campo: 'username' | 'password'): boolean {
    const c = this.form.controls[campo];
    return c.invalid && c.touched;
  }
}
