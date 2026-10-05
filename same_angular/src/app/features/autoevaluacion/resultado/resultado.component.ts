import { Component, effect, inject, signal, untracked } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ETIQUETA_CATEGORIA_MACRO, semaforo } from '../../../core/constants/lea';
import { MacroProcessCategory, Resultado } from '../../../core/models/autoevaluacion.model';
import { AutoevaluacionService } from '../../../core/services/autoevaluacion.service';
import { ModalService } from '../../../core/services/modal.service';
import { mensajeDeError } from '../../../core/utils/http-error';
import { LoaderComponent } from '../../../shared/components/loader/loader.component';
import { AutoevaluacionStore } from '../autoevaluacion.store';

/**
 * Puntaje de acreditación: % por categoría y macroproceso, puntaje final y clasificación
 * (semáforo: verde ≥ 85, amarillo 50–84, rojo < 50). Mientras está abierta es un avance; al
 * cerrarla queda el puntaje final.
 */
@Component({
  selector: 'app-resultado',
  imports: [DecimalPipe, RouterLink, LoaderComponent],
  template: `
    <div class="migas">
      <a routerLink="..">Macroprocesos</a>
      <span class="sep">&gt;</span>
      <span class="actual">Cálculo del puntaje</span>
    </div>
    @if (error()) { <div class="alert-error">{{ error() }}</div> }
    @if (!resultado()) {
      <app-loader label="Calculando puntaje..." />
    } @else {
      @let r = resultado()!;
      <div class="resumen">
        <div [class]="'final card ' + color(r.finalScore)">
          <span class="etiqueta">{{ r.assessment.status === 'CLOSED' ? 'Puntaje final' : 'Puntaje a la fecha' }}</span>
          <span class="valor">{{ r.finalScore | number: '1.2-2' }}<small>%</small></span>
          <span class="clasif">{{ r.classificationLabel }}</span>
          @if (!r.complete) {
            <span class="aviso"><i class="bi bi-info-circle"></i> Faltan calificar {{ r.criteriaCount - r.scoredCount }} de {{ r.criteriaCount }} criterios: cuentan como 0.</span>
          }
        </div>
        <div class="categorias card">
          <h3 class="section-title">Por categoría</h3>
          @for (c of r.categories; track c.category) {
            <div class="cat">
              <span class="cat-nombre">{{ categoria(c.category) }} <small>peso {{ c.weight | number: '1.0-2' }}%</small></span>
              <span class="barra"><span [class]="color(c.percentage)" [style.width.%]="c.percentage"></span></span>
              <span class="cat-valor">{{ c.percentage | number: '1.1-1' }}%</span>
            </div>
          }
        </div>
      </div>

      @if (store.puedeGestionar()) {
        <div class="cierre card">
          <div>
            <strong>Cerrar la autoevaluación</strong>
            <p>Guarda el puntaje final. Después ya no se podrán registrar ni revisar evidencias ni calificar.</p>
          </div>
          <button class="btn btn-primary" [disabled]="!r.complete || cerrando()" (click)="cerrar()"
                  [title]="r.complete ? '' : 'Debe calificar todos los criterios'">
            <i class="bi bi-lock-fill"></i> Cerrar autoevaluación
          </button>
        </div>
      }

      <div class="table-card">
        <table class="data-table">
          <thead>
            <tr><th>Macroproceso</th><th>Categoría</th><th class="num">Peso</th><th class="num">Calificados</th><th class="num">Puntos (E×1, P×2, R×3)</th><th>Avance</th></tr>
          </thead>
          <tbody>
            @for (m of r.macroProcesses; track m.macroProcessId) {
              <tr [class.inactivo]="!m.criteriaCount">
                <td><strong>{{ m.code }}</strong> {{ m.name }}</td>
                <td>{{ categoria(m.category) }}</td>
                <td class="num">{{ m.effectiveWeight | number: '1.2-2' }}%</td>
                <td class="num">{{ m.scoredCount }}/{{ m.criteriaCount }}</td>
                <td class="num">{{ m.weightedPoints }}/{{ m.weightedMaximum }}</td>
                <td class="avance">
                  @if (m.criteriaCount) {
                    <span class="barra"><span [class]="color(m.percentage)" [style.width.%]="m.percentage"></span></span>
                    <span>{{ m.percentage | number: '1.1-1' }}%</span>
                  } @else {
                    <span class="celda-sub">No aplica a la categoría</span>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
  styles: [`
    .resumen { display: grid; grid-template-columns: minmax(240px, 320px) 1fr; gap: 16px; margin-bottom: 16px; }
    .final { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; text-align: center; border-top: 5px solid; }
    .final.verde { border-top-color: #16a34a; }
    .final.amarillo { border-top-color: #f59e0b; }
    .final.rojo { border-top-color: #dc2626; }
    .etiqueta { font-size: 12px; text-transform: uppercase; letter-spacing: .06em; color: var(--gray-500); font-weight: 600; }
    .valor { font-size: 44px; font-weight: 800; color: var(--gray-900); line-height: 1.1; }
    .valor small { font-size: 20px; color: var(--gray-500); }
    .clasif { font-size: 13.5px; font-weight: 600; color: var(--gray-700); }
    .aviso { margin-top: 8px; font-size: 12px; color: var(--warning-text); }
    .categorias { display: flex; flex-direction: column; gap: 12px; }
    .cat { display: grid; grid-template-columns: 170px 1fr 56px; gap: 10px; align-items: center; font-size: 13px; }
    .cat-nombre small { display: block; color: var(--gray-500); font-size: 11.5px; }
    .cat-valor { text-align: right; font-weight: 700; }
    .barra { display: block; height: 10px; border-radius: 6px; background: var(--gray-100); overflow: hidden; }
    .barra span { display: block; height: 100%; border-radius: 6px; }
    .barra .verde { background: #16a34a; }
    .barra .amarillo { background: #f59e0b; }
    .barra .rojo { background: #dc2626; }
    .cierre { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
    .cierre p { margin: 4px 0 0; font-size: 12.5px; color: var(--gray-500); }
    .num { text-align: right; white-space: nowrap; }
    .avance { display: flex; align-items: center; gap: 8px; min-width: 160px; }
    .avance .barra { flex: 1; height: 8px; }
    .avance span:last-child { font-size: 12.5px; font-weight: 600; width: 48px; text-align: right; }
    tr.inactivo td { color: var(--gray-400); }
    @media (max-width: 767px) {
      .resumen { grid-template-columns: 1fr; }
      .cat { grid-template-columns: 1fr 48px; }
      .cat .barra { grid-column: 1 / -1; grid-row: 2; }
      .table-card { overflow-x: auto; }
    }
  `],
})
export class ResultadoComponent {
  readonly store = inject(AutoevaluacionStore);
  private readonly api = inject(AutoevaluacionService);
  private readonly modal = inject(ModalService);

  readonly resultado = signal<Resultado | null>(null);
  readonly error = signal('');
  readonly cerrando = signal(false);

  constructor() {
    effect(() => {
      this.store.checklist(); // recalcular cuando algo cambie
      untracked(() => this.cargar());
    });
  }

  color = (p: number | null | undefined) => semaforo(p);
  categoria = (c: MacroProcessCategory) => ETIQUETA_CATEGORIA_MACRO[c];

  async cerrar(): Promise<void> {
    const r = this.resultado();
    if (!r) return;
    const ok = await this.modal.confirm(
      `Se cerrará la autoevaluación ${r.assessment.periodYear} con un puntaje final de ${r.finalScore.toFixed(2)}% (${r.classificationLabel}). ` +
      'Ya no se podrá modificar. ¿Continuar?', 'Cerrar autoevaluación', 'Cerrar');
    if (!ok) return;
    this.cerrando.set(true);
    const exito = await this.store.ejecutar('cerrar', this.api.cerrar(this.store.id()), 'Autoevaluación cerrada.');
    this.cerrando.set(false);
    if (exito) await this.modal.success(`Puntaje final: ${r.finalScore.toFixed(2)}%`, 'Autoevaluación cerrada');
  }

  private cargar(): void {
    this.api.resultado(this.store.id()).subscribe({
      next: (r) => {
        this.resultado.set(r);
        this.error.set('');
      },
      error: (err) => this.error.set(mensajeDeError(err, 'No se pudo calcular el puntaje.')),
    });
  }
}
