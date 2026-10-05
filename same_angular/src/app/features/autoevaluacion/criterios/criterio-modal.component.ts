import { Component, HostListener, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ESTADO_CONDICION, ETIQUETA_TIPO_CRITERIO } from '../../../core/constants/lea';
import { NodoCriterio } from '../../../core/models/autoevaluacion.model';
import { AutoevaluacionService } from '../../../core/services/autoevaluacion.service';
import { AutoevaluacionStore } from '../autoevaluacion.store';
import { FilaFuenteComponent } from './fila-fuente.component';
import { FilaVerificacionComponent } from './fila-verificacion.component';

/**
 * Modal del criterio (prototipo): descripción con los niveles 0/1/2, puntaje, "Anexo 2: Hoja de
 * Registro de Datos" (una fila por condición y fuente) y el sustento del puntaje.
 */
@Component({
  selector: 'app-criterio-modal',
  imports: [FormsModule, DatePipe, FilaFuenteComponent, FilaVerificacionComponent],
  template: `
    @let cr = criterio();
    <div class="fondo" (click)="cerrar.emit()"></div>
    <div class="dialogo" role="dialog" aria-modal="true" [attr.aria-label]="'Criterio ' + cr.code">
      <header class="cab">
        <h2 class="codigo">{{ cr.code }}</h2>
        <div class="descripcion">
          <span class="etq">Descripción del criterio <span class="badge badge-purple">{{ tipo() }}</span></span>
          <p>{{ cr.statement }}</p>
          @if (cr.scoreLevels.length) { <p class="niveles">{{ niveles() }}</p> }
        </div>
        <div class="puntaje">
          <span>PUNTAJE: {{ cr.score ? cr.score.score : '—' }}</span>
          @if (cr.score?.origin === 'PRE_SCORED') { <small>Automático (fuentes institucionales)</small> }
          @else if (cr.score) { <small>{{ cr.score.scoredByName }} · {{ cr.score.scoredAt | date: 'dd/MM/yyyy' }}</small> }
        </div>
        <button class="x" (click)="cerrar.emit()" aria-label="Cerrar"><i class="bi bi-x-lg"></i></button>
      </header>

      <div class="cuerpo">
        <h3 class="titulo-anexo">Anexo 2: Hoja de Registro de Datos</h3>
        <div class="tabla-anexo">
          <div class="cab-tabla">
            <span>Función en la IPRESS</span><span>Responsable</span>
            <span class="sub">
              <span>Tipo documento</span><span>Técnica utilizada</span><span>Archivo</span><span class="c">Cumple</span><span class="c">Obs. a documento</span>
            </span>
          </div>
          @for (c of cr.conditions; track c.id; let i = $index) {
            <section class="condicion">
              <div class="funcion">
                <span class="titulo-cond"><span class="num">{{ i + 1 }}</span> {{ c.description }}</span>
                <span class="chips">
                  <span class="badge badge-gray">Peso {{ c.scoreWeight }}</span>
                  @if (c.sources.length > 1) {
                    <span class="badge badge-gray">{{ c.sourceLogic === 'ANY' ? 'Basta una fuente (O)' : 'Todas las fuentes (Y)' }}</span>
                  }
                  <span class="badge" [class]="'badge ' + estado(c.status).badge">{{ estado(c.status).label }}</span>
                </span>
                @if (c.facilityFunction) { <p class="texto">{{ c.facilityFunction }}</p> }
                @if (c.normativeReferences.length) {
                  <details class="normativa">
                    <summary>Normativa ({{ c.normativeReferences.length }})</summary>
                    @for (r of c.normativeReferences; track r.documentId) {
                      <div class="norma">
                        @if (r.url) { <a [href]="r.url" target="_blank" rel="noopener">{{ r.title }}</a> } @else { <span>{{ r.title }}</span> }
                        @if (r.applicableExcerpt) { <p>Aplicación en EsSalud: {{ r.applicableExcerpt }}</p> }
                      </div>
                    }
                  </details>
                }
              </div>
              <div class="responsable"><span class="etq-movil">Responsable</span>{{ c.responsibleRole ?? '—' }}</div>
              <div class="filas">
                @if (c.technique === 'DOCUMENT_REVIEW') {
                  @for (f of c.sources; track f.id) { <app-fila-fuente [fuente]="f" [tecnica]="c.technique" /> }
                } @else {
                  <app-fila-verificacion [condicion]="c" />
                }
              </div>
            </section>
          }
        </div>

        <section class="sustento">
          <div class="sus-texto">
            <span class="etq-azul">Sustento del puntaje</span>
            @if (store.puedeEvaluar()) {
              <textarea class="input" rows="3" [(ngModel)]="justificacion" (ngModelChange)="tocado = true" maxlength="2000"
                        [placeholder]="requiereJustificacion() ? 'Obligatorio: el puntaje difiere del sugerido' : 'Por qué se asigna este puntaje'"></textarea>
            } @else {
              <p class="texto">{{ cr.score?.justification || 'Sin sustento registrado.' }}</p>
            }
          </div>
          <div class="sus-puntaje">
            <span class="etq-azul">Puntaje</span>
            <div class="botones-puntaje" role="radiogroup" aria-label="Puntaje del criterio">
              @for (p of [0, 1, 2]; track p) {
                <button type="button" class="bp p{{ p }}" [class.sel]="puntaje() === p" [disabled]="!store.puedeEvaluar()"
                        (click)="elegir(p)" [attr.aria-pressed]="puntaje() === p">{{ p }}</button>
              }
            </div>
            <small>{{ cr.suggestedScore !== null ? 'Sugerido según los sustentos: ' + cr.suggestedScore : 'El sugerido aparece cuando todas las condiciones están resueltas.' }}</small>
          </div>
        </section>
      </div>

      <footer class="pie">
        <button class="btn btn-gris" (click)="cerrar.emit()">Cerrar</button>
        @if (store.puedeEvaluar()) {
          <button class="btn btn-azul" (click)="grabar()" [disabled]="ocupado() || puntaje() === null || (requiereJustificacion() && !justificacion.trim())">
            Grabar
          </button>
        }
      </footer>
    </div>
  `,
  styles: [`
    :host { position: fixed; inset: 0; z-index: 1200; display: flex; align-items: center; justify-content: center; padding: 20px; }
    .fondo { position: absolute; inset: 0; background: rgba(15, 23, 42, .45); }
    .dialogo {
      position: relative; width: min(1280px, 100%); max-height: 100%; display: flex; flex-direction: column;
      background: #fff; border-radius: 22px; box-shadow: var(--shadow-xl); overflow: hidden;
      --cols-cond: 1.5fr .9fr 5.2fr; --cols-fuente: 1.35fr .85fr 1.6fr .6fr .8fr;
    }
    .cab { display: grid; grid-template-columns: auto 1fr auto; gap: 24px; align-items: start; padding: 26px 34px 16px; border-bottom: 2px solid var(--sam-azul); margin: 0 30px; padding-left: 4px; padding-right: 4px; }
    .codigo { margin: 0; font-size: 38px; font-weight: 900; color: var(--sam-azul); white-space: nowrap; line-height: 1; }
    .descripcion { font-size: 13.5px; color: var(--gray-700); }
    .descripcion p { margin: 2px 0 0; }
    .etq { font-weight: 600; display: flex; gap: 8px; align-items: center; }
    .niveles { color: var(--gray-600); }
    .puntaje { text-align: right; padding-right: 30px; }
    .puntaje span { font-size: 24px; font-weight: 800; color: var(--sam-naranja); white-space: nowrap; }
    .puntaje small { display: block; font-size: 11px; color: var(--gray-500); margin-top: 4px; }
    .x { position: absolute; top: 14px; right: 16px; border: none; background: none; font-size: 18px; color: var(--gray-500); cursor: pointer; }
    .x:hover { color: var(--gray-900); }
    .cuerpo { overflow-y: auto; padding: 14px 34px 20px; display: flex; flex-direction: column; gap: 16px; }
    .titulo-anexo { margin: 4px 0 0; color: var(--sam-azul); font-size: 21px; font-weight: 700; }
    .tabla-anexo { display: flex; flex-direction: column; gap: 6px; }
    .cab-tabla, .condicion { display: grid; grid-template-columns: var(--cols-cond); gap: 12px; }
    .cab-tabla {
      background: #e3e6eb; padding: 12px 14px; font-size: 11.5px; font-weight: 700; color: var(--sam-azul);
      text-transform: uppercase; align-items: end;
    }
    .cab-tabla .sub { display: grid; grid-template-columns: var(--cols-fuente); gap: 10px; }
    .cab-tabla .c { text-align: center; }
    .condicion { background: var(--sam-fila); padding: 10px 14px; align-items: start; }
    .funcion { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
    .titulo-cond { font-size: 13px; font-weight: 700; color: var(--gray-800); line-height: 1.35; }
    .num {
      display: inline-flex; width: 20px; height: 20px; border-radius: 50%; background: var(--sam-azul); color: #fff;
      font-size: 11px; align-items: center; justify-content: center; margin-right: 4px;
    }
    .chips { display: flex; flex-wrap: wrap; gap: 4px; }
    .texto { margin: 0; font-size: 12.5px; color: var(--gray-600); line-height: 1.4; }
    .normativa { font-size: 12px; }
    .normativa summary { cursor: pointer; color: var(--sam-azul); font-weight: 600; }
    .norma { margin-top: 6px; }
    .norma a { color: var(--sam-azul); text-decoration: underline; }
    .norma p { margin: 2px 0 0; color: var(--gray-500); font-style: italic; white-space: pre-line; }
    .responsable { font-size: 12.5px; color: var(--gray-700); }
    .etq-movil { display: none; font-size: 10.5px; font-weight: 700; color: var(--sam-azul); text-transform: uppercase; }
    .filas { min-width: 0; }
    .sustento { display: grid; grid-template-columns: 1fr auto; gap: 24px; align-items: start; border: 1px solid var(--gray-200); border-radius: var(--radius-md); padding: 14px 16px; }
    .sus-texto, .sus-puntaje { display: flex; flex-direction: column; gap: 6px; }
    .sus-puntaje small { font-size: 11.5px; color: var(--gray-500); max-width: 240px; }
    .etq-azul { font-size: 12px; font-weight: 700; color: var(--sam-azul); text-transform: uppercase; }
    textarea.input { resize: vertical; font-family: inherit; }
    .botones-puntaje { display: flex; gap: 6px; }
    .bp {
      width: 40px; height: 40px; border-radius: var(--radius-md); border: 1.5px solid var(--gray-200); background: #fff;
      font-weight: 800; font-size: 16px; cursor: pointer; color: var(--gray-600);
    }
    .bp:disabled { cursor: default; }
    .bp.sel.p0 { background: #fee2e2; border-color: #fca5a5; color: #b91c1c; }
    .bp.sel.p1 { background: #fef3c7; border-color: #fcd34d; color: #92400e; }
    .bp.sel.p2 { background: #dcfce7; border-color: #86efac; color: #15803d; }
    .pie { display: flex; justify-content: flex-end; gap: 14px; padding: 14px 34px 20px; border-top: 1px solid var(--gray-100); }
    .pie .btn { min-width: 140px; justify-content: center; padding: 11px 20px; font-size: 14px; }
    .btn-gris { background: #d4d6db; color: var(--sam-azul); }
    .btn-gris:hover { background: #c5c8ce; }
    @media (max-width: 899px) {
      :host { padding: 0; }
      .dialogo { border-radius: 0; height: 100%; }
      .cab { grid-template-columns: 1fr; gap: 8px; margin: 0 14px; padding-top: 18px; }
      .codigo { font-size: 28px; }
      .puntaje { text-align: left; padding-right: 0; }
      .cuerpo { padding: 12px 14px; }
      .cab-tabla { display: none; }
      .condicion { grid-template-columns: 1fr; }
      .etq-movil { display: block; }
      .sustento { grid-template-columns: 1fr; }
      .pie { padding: 12px 14px; }
    }
  `],
})
export class CriterioModalComponent {
  readonly store = inject(AutoevaluacionStore);
  private readonly api = inject(AutoevaluacionService);

  readonly criterio = input.required<NodoCriterio>();
  readonly cerrar = output<void>();

  readonly puntaje = signal<number | null>(null);
  justificacion = '';
  /** El evaluador ya eligió: no se le reemplaza con el sugerido al recargar. */
  tocado = false;

  readonly tipo = computed(() => ETIQUETA_TIPO_CRITERIO[this.criterio().type]);
  readonly niveles = computed(() => this.criterio().scoreLevels.map((n) => `${n.score}: ${n.description}`).join('; ') + '.');
  readonly requiereJustificacion = computed(() => {
    const sugerido = this.criterio().suggestedScore;
    return sugerido !== null && this.puntaje() !== null && this.puntaje() !== sugerido;
  });
  readonly ocupado = computed(() => this.store.ocupado() === 'criterio-' + this.criterio().id);

  estado = (s: keyof typeof ESTADO_CONDICION) => ESTADO_CONDICION[s];

  constructor() {
    effect(() => {
      const c = this.criterio();
      untracked(() => {
        if (this.tocado) return;
        // El evaluado solo ve el puntaje otorgado; el evaluador arranca con el sugerido.
        this.puntaje.set(c.score?.score ?? (this.store.puedeEvaluar() ? c.suggestedScore : null));
        this.justificacion = c.score?.origin === 'MANUAL' ? c.score.justification ?? '' : '';
      });
    });
  }

  @HostListener('document:keydown.escape')
  alEscape(): void {
    this.cerrar.emit();
  }

  elegir(p: number): void {
    this.tocado = true;
    this.puntaje.set(p);
  }

  async grabar(): Promise<void> {
    const p = this.puntaje();
    if (p === null) return;
    const ok = await this.store.ejecutar('criterio-' + this.criterio().id,
      this.api.calificar(this.store.id(), this.criterio().id, p, this.justificacion), `${this.criterio().code}: puntaje ${p} grabado.`);
    if (ok) this.tocado = false;
  }
}
