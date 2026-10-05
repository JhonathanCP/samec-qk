import { Component, OnInit, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ESTADO_EVIDENCIA } from '../../../core/constants/lea';
import { Evidencia, SubmissionStatus } from '../../../core/models/autoevaluacion.model';
import { AutoevaluacionService } from '../../../core/services/autoevaluacion.service';
import { mensajeDeError } from '../../../core/utils/http-error';
import { LoaderComponent } from '../../../shared/components/loader/loader.component';
import { ArchivoChipComponent } from '../comunes/archivo-chip.component';
import { AutoevaluacionStore } from '../autoevaluacion.store';

/**
 * Evidencias de la autoevaluación por estado. Para el evaluador es su bandeja de revisión (por
 * revisar, la más antigua primero) con aprobar u observar en la misma fila.
 */
@Component({
  selector: 'app-bandeja-evidencias',
  imports: [FormsModule, RouterLink, DatePipe, LoaderComponent, ArchivoChipComponent],
  template: `
    <div class="migas">
      <a routerLink="..">Macroprocesos</a>
      <span class="sep">&gt;</span>
      <span class="actual">{{ store.esEvaluador() ? 'Bandeja de revisión' : 'Sustentos cargados' }}</span>
    </div>
    <div class="barra">
      <div class="estados" role="tablist">
        @for (e of estados; track e.valor) {
          <button type="button" role="tab" class="estado" [class.sel]="estado() === e.valor" (click)="estado.set(e.valor)"
                  [attr.aria-selected]="estado() === e.valor">{{ e.etiqueta }}</button>
        }
      </div>
      <span class="filtro-contador">{{ total() }} evidencia(s)</span>
    </div>

    @if (error()) { <div class="alert-error">{{ error() }}</div> }
    @if (cargando()) {
      <app-loader label="Cargando evidencias..." />
    } @else {
      <div class="lista">
        @for (ev of evidencias(); track ev.id) {
          <article class="item">
            <div class="cab">
              <span class="codigo">{{ ev.criterionCode }}</span>
              <span class="fuente">{{ ev.auditSourceName }}</span>
              <span class="badge" [class]="'badge ' + etiqueta(ev.status).badge">{{ etiqueta(ev.status).label }}</span>
              @if (ubicar(ev.auditSourceId); as u) {
                <a class="ir" [routerLink]="['..', 'macroprocesos', u.macroId]" [queryParams]="{ criterio: u.criterioId }">Ir al criterio <i class="bi bi-arrow-right"></i></a>
              }
            </div>
            <div class="archivos">
              @for (a of ev.files; track a.id) { <app-archivo-chip [archivo]="a" /> }
              @if (ev.externalUrl) {
                <a class="enlace" [href]="ev.externalUrl" target="_blank" rel="noopener"><i class="bi bi-link-45deg"></i> Enlace</a>
              }
            </div>
            @if (ev.notes) { <p class="nota"><i class="bi bi-chat-left-text"></i> {{ ev.notes }}</p> }
            @if (ev.reviews.at(-1); as r) {
              @if (r.verdict === 'OBSERVED') {
                <p class="obs"><i class="bi bi-exclamation-triangle-fill"></i> {{ r.observationTypeName }}{{ r.comment ? ': ' + r.comment : '' }}</p>
              }
            }
            <div class="pie">
              <span class="meta">{{ ev.submittedByName }} · {{ ev.submittedAt | date: 'dd/MM/yyyy HH:mm' }}</span>
              @if (store.puedeEvaluar() && ev.status === 'PENDING_REVIEW') {
                @if (observando() === ev.id) {
                  <div class="observar">
                    <select class="input" [(ngModel)]="tipoObservacion" aria-label="Motivo de la observación">
                      <option [ngValue]="null" disabled>Motivo…</option>
                      @for (t of store.tiposObservacion(); track t.id) { <option [ngValue]="t.id">{{ t.name }}</option> }
                    </select>
                    <input class="input" [(ngModel)]="comentario" placeholder="Comentario para la IPRESS" maxlength="2000" />
                    <button class="btn btn-secondary btn-sm" (click)="observando.set(null)">Cancelar</button>
                    <button class="btn btn-warning btn-sm" [disabled]="!tipoObservacion || ocupado() === ev.id" (click)="observar(ev)">Observar</button>
                  </div>
                } @else {
                  <div class="acciones">
                    <button class="btn btn-success btn-sm" [disabled]="ocupado() === ev.id" (click)="aprobar(ev)"><i class="bi bi-check-lg"></i> Aprobar</button>
                    <button class="btn btn-warning btn-sm" [disabled]="ocupado() === ev.id" (click)="abrirObservar(ev.id)"><i class="bi bi-flag"></i> Observar</button>
                  </div>
                }
              }
            </div>
          </article>
        } @empty {
          <div class="empty-state">
            <i class="bi bi-inbox empty-state-icon"></i>
            <span class="empty-state-title">{{ vacio[estado()] }}</span>
          </div>
        }
      </div>
    }
  `,
  styles: [`
    .barra { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
    .estados { display: inline-flex; background: #fff; border: 1px solid var(--gray-200); border-radius: var(--radius-md); padding: 3px; }
    .estado { border: none; background: none; padding: 6px 12px; border-radius: var(--radius-sm); font-size: 13px; cursor: pointer; color: var(--gray-600); }
    .estado.sel { background: var(--sam-azul); color: #fff; font-weight: 600; }
    .lista { display: flex; flex-direction: column; gap: 8px; }
    .ir { font-size: 12.5px; font-weight: 700; color: var(--sam-azul); }
    .ir:hover { text-decoration: underline; }
    .item { background: #fff; border: 1px solid var(--gray-200); border-radius: var(--radius-md); padding: 12px 14px; display: flex; flex-direction: column; gap: 8px; }
    .cab { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
    .codigo { font-weight: 700; color: var(--essalud-main); font-size: 13px; }
    .fuente { flex: 1; font-size: 13px; font-weight: 600; color: var(--gray-800); min-width: 200px; }
    .archivos { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
    .enlace { font-size: 12.5px; color: var(--essalud-main); }
    .nota { margin: 0; font-size: 12.5px; color: var(--gray-600); }
    .obs { margin: 0; font-size: 12.5px; color: var(--danger-text); }
    .pie { display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; }
    .meta { font-size: 11.5px; color: var(--gray-500); }
    .acciones, .observar { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }
    .observar .input { width: auto; min-width: 180px; }
  `],
})
export class BandejaEvidenciasComponent implements OnInit {
  readonly store = inject(AutoevaluacionStore);
  private readonly api = inject(AutoevaluacionService);

  readonly estados: { valor: SubmissionStatus; etiqueta: string }[] = [
    { valor: 'PENDING_REVIEW', etiqueta: 'Por revisar' },
    { valor: 'OBSERVED', etiqueta: 'Observadas' },
    { valor: 'APPROVED', etiqueta: 'Aprobadas' },
  ];

  readonly vacio: Record<SubmissionStatus, string> = {
    PENDING_REVIEW: 'No hay evidencias por revisar',
    OBSERVED: 'No hay evidencias observadas',
    APPROVED: 'Aún no hay evidencias aprobadas',
  };

  readonly estado = signal<SubmissionStatus>('PENDING_REVIEW');
  readonly evidencias = signal<Evidencia[]>([]);
  readonly total = signal(0);
  readonly cargando = signal(true);
  readonly error = signal('');
  readonly observando = signal<number | null>(null);
  readonly ocupado = computed(() => {
    const clave = this.store.ocupado();
    return clave?.startsWith('bandeja-') ? Number(clave.slice(8)) : null;
  });
  tipoObservacion: number | null = null;
  comentario = '';

  constructor() {
    // Cambió el estado elegido o el checklist (alguien actuó): se vuelve a consultar.
    effect(() => {
      this.estado();
      this.store.checklist();
      untracked(() => this.cargar());
    });
  }

  /** ?estado=OBSERVED (enlace "Sustentos observados" del evaluado). */
  readonly estadoInicial = input<string | undefined>(undefined, { alias: 'estado' });

  ngOnInit(): void {
    const inicial = this.estadoInicial();
    if (inicial === 'OBSERVED' || inicial === 'APPROVED' || inicial === 'PENDING_REVIEW') this.estado.set(inicial);
    else if (this.store.puedeRegistrar() && !this.store.puedeEvaluar()) this.estado.set('OBSERVED');
  }

  ubicar(fuenteId: number) {
    return this.store.ubicarFuente(fuenteId);
  }

  etiqueta(s: SubmissionStatus) {
    return ESTADO_EVIDENCIA[s];
  }

  abrirObservar(id: number): void {
    this.tipoObservacion = null;
    this.comentario = '';
    this.observando.set(id);
  }

  async aprobar(ev: Evidencia): Promise<void> {
    await this.store.ejecutar('bandeja-' + ev.id, this.api.revisarEvidencia(ev.id, 'APPROVED', null, ''), 'Evidencia aprobada.');
  }

  async observar(ev: Evidencia): Promise<void> {
    const ok = await this.store.ejecutar('bandeja-' + ev.id,
      this.api.revisarEvidencia(ev.id, 'OBSERVED', this.tipoObservacion, this.comentario), 'Evidencia observada.');
    if (ok) this.observando.set(null);
  }

  private cargar(): void {
    this.cargando.set(this.evidencias().length === 0);
    this.api.evidenciasPorEstado(this.store.id(), this.estado(), 0, 100).subscribe({
      next: (p) => {
        this.evidencias.set(p.content);
        this.total.set(p.totalElements);
        this.error.set('');
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(mensajeDeError(err));
        this.cargando.set(false);
      },
    });
  }
}
