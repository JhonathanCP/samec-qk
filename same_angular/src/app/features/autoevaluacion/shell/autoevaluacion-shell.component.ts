import { Component, effect, inject, input, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink, RouterOutlet } from '@angular/router';
import { LoaderComponent } from '../../../shared/components/loader/loader.component';
import { AutoevaluacionStore } from '../autoevaluacion.store';

/**
 * Contenedor de una autoevaluación: carga sus datos una vez y los comparte (AutoevaluacionStore)
 * con macroprocesos, criterios, bandeja y cálculo del puntaje.
 */
@Component({
  selector: 'app-autoevaluacion-shell',
  imports: [RouterOutlet, RouterLink, DatePipe, LoaderComponent],
  providers: [AutoevaluacionStore],
  template: `
    @if (store.error() && !store.checklist()) {
      <div class="alert-error">{{ store.error() }}</div>
      <a routerLink="/autoevaluaciones" class="btn btn-outline">Volver a autoevaluaciones</a>
    } @else if (store.cargando()) {
      <app-loader label="Cargando la autoevaluación..." />
    } @else {
      @if (store.cargaCerrada() && store.abierta()) {
        @let a = store.autoevaluacion()!;
        <div class="candado" role="status">
          <i class="bi bi-lock-fill"></i>
          <span>
            <strong>Carga documental del evaluado cerrada</strong> por {{ a.evidenceLockedByName }} el {{ a.evidenceLockedAt | date: 'dd/MM/yyyy HH:mm' }}.
            {{ store.esEvaluador() ? 'El evaluado ya no puede registrar ni corregir sustentos.' : 'Ya no puede registrar ni corregir sustentos; consulte con su evaluador.' }}
          </span>
        </div>
      }
      <router-outlet />
    }
  `,
  styles: [`
    .candado {
      display: flex; align-items: center; gap: 12px; margin-bottom: 14px; padding: 12px 18px; border-radius: var(--radius-md);
      background: #fff7ed; border: 1px solid #fdba74; color: #9a3412; font-size: 13.5px;
    }
    .candado i { font-size: 20px; color: var(--sam-naranja); }
  `],
})
export class AutoevaluacionShellComponent {
  readonly store = inject(AutoevaluacionStore);
  readonly id = input.required<string>();

  constructor() {
    effect(() => {
      const id = Number(this.id());
      untracked(() => this.store.iniciar(id));
    });
  }
}
