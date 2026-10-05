import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { UsuarioService } from '../../core/services/usuario.service';
import { ToastService } from '../../core/services/toast.service';
import { mensajeDeError } from '../../core/utils/http-error';
import { PASSWORD_AYUDA, passwordSegura, passwordsIguales } from '../auth/password.validators';

/** Cambio de la propia contraseña: exige la actual; el core cierra las demás sesiones abiertas. */
@Component({
  selector: 'app-cambiar-password',
  imports: [ReactiveFormsModule],
  template: `
    <div class="page-header">
      <div>
        <h2 class="page-title">Cambiar contraseña</h2>
        <p class="page-subtitle">Al cambiarla se cerrarán sus sesiones abiertas en otros equipos.</p>
      </div>
    </div>

    <form class="form-card" [formGroup]="form" (ngSubmit)="guardar()" novalidate>
      <div class="form-grid">
        <div class="field-group full">
          <label for="actual">Contraseña actual</label>
          <input id="actual" class="input" type="password" formControlName="passwordActual" autocomplete="current-password" />
        </div>
        <div class="field-group">
          <label for="nueva">Nueva contraseña</label>
          <input id="nueva" class="input" type="password" formControlName="password" autocomplete="new-password" />
          <span class="field-hint">{{ ayuda }}</span>
        </div>
        <div class="field-group">
          <label for="confirmar">Confirmar nueva contraseña</label>
          <input id="confirmar" class="input" type="password" formControlName="passwordConfirmar" autocomplete="new-password" />
          @if (form.hasError('passwordsDistintas') && form.controls.passwordConfirmar.touched) {
            <span class="field-error">Las contraseñas no coinciden.</span>
          }
        </div>
      </div>
      @if (error()) { <div class="alert-error" style="margin-top:16px">{{ error() }}</div> }
      <div class="form-actions">
        <button type="submit" class="btn btn-primary" [disabled]="guardando() || form.invalid">
          <i class="bi bi-check2"></i> {{ guardando() ? 'Guardando...' : 'Cambiar contraseña' }}
        </button>
      </div>
    </form>
  `,
  styles: [`.form-card { max-width: 640px; }`],
})
export class CambiarPasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly usuarios = inject(UsuarioService);
  private readonly toast = inject(ToastService);

  readonly ayuda = PASSWORD_AYUDA;
  readonly guardando = signal(false);
  readonly error = signal('');

  readonly form = this.fb.nonNullable.group(
    {
      passwordActual: ['', Validators.required],
      password: ['', [Validators.required, passwordSegura]],
      passwordConfirmar: ['', Validators.required],
    },
    { validators: passwordsIguales },
  );

  guardar(): void {
    if (this.form.invalid) return;
    this.guardando.set(true);
    this.error.set('');
    const { passwordActual, password } = this.form.getRawValue();
    this.usuarios.cambiarMiPassword(passwordActual, password).subscribe({
      next: (r) => {
        this.guardando.set(false);
        this.form.reset();
        this.toast.success(r.message);
      },
      error: (err) => {
        this.guardando.set(false);
        this.error.set(mensajeDeError(err));
      },
    });
  }
}
