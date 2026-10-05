import { Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { NodoCriterio } from '../../../core/models/autoevaluacion.model';
import { AutoevaluacionStore, avanceDeCriterio, criterioPendiente, semaforoCarga } from '../autoevaluacion.store';
import { CriterioModalComponent } from './criterio-modal.component';

/**
 * Criterios de un macroproceso (prototipo "Macroprocesos > Direccionamiento (DIR) - Criterios"):
 * puntaje alcanzado, sustentos requeridos, estado de carga (semáforo) y "Validar"/"Sustentar".
 */
@Component({
  selector: 'app-criterios',
  imports: [FormsModule, RouterLink, CriterioModalComponent],
  template: `
    @if (macro(); as m) {
      <div class="migas">
        <a [routerLink]="['..']">Macroprocesos</a>
        <span class="sep">&gt;</span>
        <span class="actual">{{ m.name }} ({{ m.code }}) - Criterios</span>
        <span class="extra">
          <input class="input buscar" [ngModel]="texto()" (ngModelChange)="texto.set($event)" placeholder="Buscar código o texto" aria-label="Buscar criterio" />
          <label class="toggle"><input type="checkbox" [ngModel]="soloPendientes()" (ngModelChange)="soloPendientes.set($event)" /> Solo pendientes</label>
        </span>
      </div>

      <section class="panel-blanco">
        <table class="tabla-proto">
          <thead>
            <tr>
              <th>Estándar</th><th class="izq">Criterio</th><th>Puntaje<br />alcanzado</th><th>Sustentos<br />requeridos</th>
              <th>Estado<br />carga</th><th>Sustento</th>
            </tr>
          </thead>
          <tbody>
            @for (fila of filas(); track fila.criterio.id) {
              @let c = fila.criterio;
              @let av = fila.avance;
              <tr>
                <td class="estandar" [title]="fila.estandarTexto">{{ fila.estandar }}</td>
                <td class="izq criterio">
                  <button type="button" class="codigo" (click)="abrir(c.id)">{{ c.code }}</button>
                  <span class="enunciado">{{ c.statement }}</span>
                </td>
                <td>
                  @if (c.score) {
                    <span class="puntaje p{{ c.score.score }}">{{ c.score.score }}</span>
                  } @else if (store.esEvaluador() && c.suggestedScore !== null) {
                    <span class="sugerido" title="Puntaje sugerido según los sustentos">{{ c.suggestedScore }}?</span>
                  } @else { <span class="tenue">—</span> }
                </td>
                <td>
                  @if (av.sustentosRequeridos) { {{ av.sustentosRequeridos }} documento{{ av.sustentosRequeridos === 1 ? '' : 's' }} }
                  @if (av.verificaciones) { <small class="campo">{{ av.verificaciones }} en campo</small> }
                  @if (!av.sustentosRequeridos && !av.verificaciones) { <span class="tenue">Institucional</span> }
                </td>
                <td>
                  <span class="semaforo" [class]="'semaforo ' + semaforo(av)"></span>
                  @if (av.sustentosRequeridos) { <small class="xy">{{ av.sustentosCargados }}/{{ av.sustentosRequeridos }}</small> }
                  @if (av.sustentosObservados) { <small class="obs">{{ av.sustentosObservados }} observado{{ av.sustentosObservados === 1 ? '' : 's' }}</small> }
                  @if (store.esEvaluador() && av.porRevisar) { <small class="rev">{{ av.porRevisar }} por revisar</small> }
                </td>
                <td>
                  <button class="btn btn-azul btn-sm" (click)="abrir(c.id)">
                    {{ store.puedeEvaluar() ? 'Validar' : store.puedeRegistrar() ? 'Sustentar' : 'Ver' }}
                  </button>
                </td>
              </tr>
            } @empty {
              <tr><td colspan="6"><div class="empty-state"><i class="bi bi-check2-all empty-state-icon"></i>
                <span class="empty-state-title">{{ soloPendientes() ? 'No hay criterios pendientes en este macroproceso' : 'Ningún criterio coincide' }}</span></div></td></tr>
            }
          </tbody>
        </table>
      </section>

      @if (abierto(); as c) {
        <app-criterio-modal [criterio]="c" (cerrar)="cerrarModal()" />
      }
    } @else {
      <div class="alert-error">Macroproceso no encontrado.</div>
    }
  `,
  styles: [`
    .buscar { width: 220px; padding: 7px 10px; }
    .toggle { display: inline-flex; align-items: center; gap: 6px; color: var(--gray-600); cursor: pointer; }
    .estandar { font-weight: 600; color: var(--sam-azul); white-space: nowrap; }
    .criterio { max-width: 520px; }
    .codigo { border: none; background: none; padding: 0; color: var(--sam-azul); font-weight: 700; font-size: 13.5px; cursor: pointer; }
    .codigo:hover { text-decoration: underline; }
    .enunciado {
      display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
      font-size: 12.5px; color: var(--gray-600); margin-top: 2px;
    }
    .puntaje { display: inline-flex; width: 28px; height: 28px; border-radius: 50%; align-items: center; justify-content: center; font-weight: 800; }
    .p0 { background: #fee2e2; color: #b91c1c; }
    .p1 { background: #fef3c7; color: #92400e; }
    .p2 { background: #dcfce7; color: #15803d; }
    .sugerido { color: var(--info-text); font-weight: 700; border: 1.5px dashed var(--info-border); border-radius: 50%; padding: 4px 6px; font-size: 12px; }
    .tenue { color: var(--gray-400); }
    .campo, .xy, .obs, .rev { display: block; font-size: 11px; }
    .campo { color: var(--gray-500); }
    .xy { color: var(--gray-600); margin-top: 2px; }
    .obs { color: var(--danger-text); font-weight: 700; }
    .rev { color: var(--sam-naranja); font-weight: 700; }
    @media (max-width: 1023px) {
      .panel-blanco { overflow-x: auto; }
      .buscar { width: 100%; }
      .migas .extra { margin-left: 0; width: 100%; }
    }
  `],
})
export class CriteriosComponent {
  readonly store = inject(AutoevaluacionStore);
  private readonly router = inject(Router);

  /** Parámetros de la ruta y de la URL (withComponentInputBinding). */
  readonly macroId = input.required<string>();
  readonly criterio = input<string | undefined>();

  readonly texto = signal('');
  readonly soloPendientes = signal(false);

  readonly macro = computed(() => this.store.macro(Number(this.macroId())));

  readonly filas = computed(() => {
    const m = this.macro();
    if (!m) return [];
    const texto = this.texto().trim().toLowerCase();
    const evaluador = this.store.puedeEvaluar();
    return m.standards.flatMap((e) => e.criteria
      .filter((c) => (!texto || c.code.toLowerCase().includes(texto) || c.statement.toLowerCase().includes(texto))
        && (!this.soloPendientes() || criterioPendiente(c, evaluador)))
      .map((c) => ({ criterio: c, estandar: e.code, estandarTexto: e.statement, avance: avanceDeCriterio(c) })));
  });

  /** Criterio abierto en el modal (?criterio=id, así sobrevive a recargar la página). */
  readonly abierto = computed<NodoCriterio | null>(() => {
    const id = Number(this.criterio());
    if (!id) return null;
    for (const e of this.macro()?.standards ?? []) {
      const c = e.criteria.find((x) => x.id === id);
      if (c) return c;
    }
    return null;
  });

  semaforo = semaforoCarga;

  abrir(id: number): void {
    this.router.navigate([], { queryParams: { criterio: id }, queryParamsHandling: 'merge' });
  }

  cerrarModal(): void {
    this.router.navigate([], { queryParams: { criterio: null }, queryParamsHandling: 'merge' });
  }
}
