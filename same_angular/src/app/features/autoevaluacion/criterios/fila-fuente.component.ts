import { Component, computed, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { DISPONIBILIDAD_FUENTE, ESTADO_EVIDENCIA, ETIQUETA_TECNICA } from '../../../core/constants/lea';
import { EvaluationTechnique, NodoFuente } from '../../../core/models/autoevaluacion.model';
import { AutoevaluacionService } from '../../../core/services/autoevaluacion.service';
import { ModalService } from '../../../core/services/modal.service';
import { ArchivoChipComponent } from '../comunes/archivo-chip.component';
import { SelectorArchivosComponent } from '../comunes/selector-archivos.component';
import { AutoevaluacionStore } from '../autoevaluacion.store';

type Panel = 'cargar' | 'agregar' | 'detalle' | 'revisar' | null;

/**
 * Fila del Anexo 2 para una fuente auditable: tipo de documento | técnica | archivo | cumple |
 * observación al documento. El evaluado carga y corrige; el evaluador aprueba u observa.
 */
@Component({
  selector: 'app-fila-fuente',
  imports: [FormsModule, DatePipe, ArchivoChipComponent, SelectorArchivosComponent],
  template: `
    @let f = fuente();
    @let ev = f.evidence;
    <div class="fila" [class.observada]="ev?.status === 'OBSERVED'">
      <div class="celda tipo">
        <span class="etq-movil">Tipo de documento</span>
        <span class="nombre">{{ f.name }}</span>
        @if (f.availability !== 'PROVIDED_BY_FACILITY') {
          <span class="badge" [class]="'badge ' + disponibilidad().badge" [title]="disponibilidad().ayuda">{{ disponibilidad().label }}</span>
        }
      </div>
      <div class="celda tecnica"><span class="etq-movil">Técnica utilizada</span>{{ tecnicaTexto() }}</div>
      <div class="celda archivo">
        <span class="etq-movil">Archivo</span>
        @if (f.availability === 'PREDEFINED' && f.referenceUrl) {
          <a class="enlace" [href]="f.referenceUrl" target="_blank" rel="noopener"><i class="bi bi-box-arrow-up-right"></i> Documento institucional</a>
        } @else if (f.availability === 'NOT_IMPLEMENTED') {
          <span class="tenue">No implementado en EsSalud</span>
        } @else {
          @if (ev) {
            <div class="archivos">
              @for (a of ev.files; track a.id) {
                <app-archivo-chip [archivo]="a" [quitable]="editable()" [deshabilitado]="ocupado()" (quitar)="quitarArchivo(a.id, a.name)" />
              }
              @if (ev.externalUrl) {
                <a class="enlace" [href]="ev.externalUrl" target="_blank" rel="noopener"><i class="bi bi-link-45deg"></i> Enlace</a>
              }
            </div>
            @if (ev.notes) { <span class="nota" [title]="ev.notes"><i class="bi bi-chat-left-text"></i> {{ ev.notes }}</span> }
            <span class="meta">{{ ev.submittedByName }} · {{ ev.submittedAt | date: 'dd/MM/yy HH:mm' }}</span>
          } @else {
            <span class="tenue">Sin documento</span>
          }
          @if (store.puedeRegistrar()) {
            <div class="acciones">
              @if (!ev) {
                <button class="btn btn-turquesa btn-sm" (click)="abrir('cargar')" [disabled]="ocupado()"><i class="bi bi-upload"></i> Cargar</button>
              } @else if (editable()) {
                <button class="btn btn-turquesa btn-sm" (click)="abrir('agregar')" [disabled]="ocupado()" title="Agregar archivos"><i class="bi bi-plus-lg"></i> Cargar</button>
                @if (ev.status === 'OBSERVED') {
                  <button class="btn btn-azul btn-sm" (click)="reenviar()" [disabled]="ocupado()"><i class="bi bi-send"></i> Reenviar</button>
                }
                <button class="icono" (click)="abrirDetalle()" [disabled]="ocupado()" title="Enlace y notas"><i class="bi bi-pencil"></i></button>
                <button class="icono peligro" (click)="retirar()" [disabled]="ocupado()" title="Retirar el sustento"><i class="bi bi-trash"></i></button>
              }
            </div>
          }
        }
      </div>
      <div class="celda cumple">
        <span class="etq-movil">Cumple</span>
        <span class="radios" [attr.aria-label]="'Cumple: ' + (cumple() === true ? 'sí' : cumple() === false ? 'no' : 'pendiente')">
          <span class="radio-opc"><small>SI</small><span class="radio" [class.marcado]="cumple() === true"></span></span>
          <span class="radio-opc"><small>NO</small><span class="radio" [class.marcado]="cumple() === false"></span></span>
        </span>
        @if (ev?.status === 'PENDING_REVIEW') { <small class="estado">Por revisar</small> }
      </div>
      <div class="celda obs">
        <span class="etq-movil">Obs. a documento</span>
        @if (ultima(); as r) {
          @if (r.verdict === 'OBSERVED') {
            <span class="obs-texto" [title]="r.comment ?? ''"><i class="bi bi-exclamation-triangle-fill"></i> {{ r.observationTypeName }}@if (r.comment) {: {{ r.comment }}}</span>
          }
        }
        @if (store.puedeEvaluar() && ev?.status === 'PENDING_REVIEW') {
          <button class="icono azul" (click)="abrir('revisar')" [disabled]="ocupado()" title="Revisar el documento"><i class="bi bi-pencil-square"></i></button>
        }
      </div>
    </div>

    @switch (panel()) {
      @case ('cargar') {
        <div class="panel">
          <app-selector-archivos [(archivos)]="archivos" />
          <div class="dos">
            <label class="campo">Enlace externo (opcional)
              <input class="input" [(ngModel)]="enlace" placeholder="https://… (si está publicado en línea)" />
            </label>
            <label class="campo">Notas para el evaluador (opcional)
              <input class="input" [(ngModel)]="notas" maxlength="1000" />
            </label>
          </div>
          <div class="botones">
            <button class="btn btn-secondary btn-sm" (click)="cerrarPanel()">Cancelar</button>
            <button class="btn btn-turquesa btn-sm" (click)="cargar()" [disabled]="ocupado() || (!archivos().length && !enlace.trim())">
              <i class="bi bi-upload"></i> Cargar sustento
            </button>
          </div>
        </div>
      }
      @case ('agregar') {
        <div class="panel">
          <app-selector-archivos [(archivos)]="archivos" />
          <div class="botones">
            <button class="btn btn-secondary btn-sm" (click)="cerrarPanel()">Cancelar</button>
            <button class="btn btn-turquesa btn-sm" (click)="agregar()" [disabled]="ocupado() || !archivos().length">
              <i class="bi bi-upload"></i> Cargar {{ archivos().length || '' }} archivo(s)
            </button>
          </div>
        </div>
      }
      @case ('detalle') {
        <div class="panel">
          <div class="dos">
            <label class="campo">Enlace externo <input class="input" [(ngModel)]="enlace" placeholder="https://…" /></label>
            <label class="campo">Notas para el evaluador <input class="input" [(ngModel)]="notas" maxlength="1000" /></label>
          </div>
          <div class="botones">
            <button class="btn btn-secondary btn-sm" (click)="cerrarPanel()">Cancelar</button>
            <button class="btn btn-azul btn-sm" (click)="guardarDetalle()" [disabled]="ocupado()">Guardar</button>
          </div>
        </div>
      }
      @case ('revisar') {
        <div class="panel">
          <div class="dos">
            <label class="campo">Motivo (solo si observa)
              <select class="input" [(ngModel)]="tipoObservacion">
                <option [ngValue]="null">—</option>
                @for (t of store.tiposObservacion(); track t.id) { <option [ngValue]="t.id">{{ t.name }}</option> }
              </select>
            </label>
            <label class="campo">Comentario para la IPRESS
              <input class="input" [(ngModel)]="comentario" maxlength="2000" placeholder="Qué debe corregir" />
            </label>
          </div>
          <div class="botones">
            <button class="btn btn-secondary btn-sm" (click)="cerrarPanel()">Cancelar</button>
            <button class="btn btn-warning btn-sm" (click)="observar()" [disabled]="ocupado() || !tipoObservacion"><i class="bi bi-flag"></i> Observar</button>
            <button class="btn btn-success btn-sm" (click)="aprobar()" [disabled]="ocupado()"><i class="bi bi-check-lg"></i> Aprobar</button>
          </div>
        </div>
      }
    }
  `,
  styles: [`
    :host { display: block; }
    :host + :host .fila { border-top: 1px dashed var(--gray-300); }
    .fila { display: grid; grid-template-columns: var(--cols-fuente); gap: 10px; padding: 8px 0; align-items: start; }
    .fila.observada .archivo, .fila.observada .obs { color: var(--danger-text); }
    .celda { display: flex; flex-direction: column; gap: 4px; min-width: 0; font-size: 12.5px; color: var(--gray-700); }
    .etq-movil { display: none; font-size: 10.5px; font-weight: 700; color: var(--sam-azul); text-transform: uppercase; }
    .nombre { font-weight: 600; color: var(--gray-800); }
    .archivos { display: flex; flex-wrap: wrap; gap: 4px; }
    .enlace { color: var(--sam-azul); text-decoration: underline; font-size: 12.5px; }
    .tenue { color: var(--gray-400); }
    .nota { color: var(--gray-500); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .meta { font-size: 11px; color: var(--gray-400); }
    .acciones { display: flex; gap: 4px; flex-wrap: wrap; align-items: center; }
    .icono { border: none; background: none; cursor: pointer; color: var(--gray-500); font-size: 15px; padding: 2px 4px; }
    .icono:hover:not(:disabled) { color: var(--sam-azul); }
    .icono.peligro:hover:not(:disabled) { color: var(--danger-text); }
    .icono.azul { color: var(--sam-azul); font-size: 19px; }
    .cumple { align-items: center; }
    .radios { display: flex; gap: 10px; }
    .radio-opc { display: flex; flex-direction: column; align-items: center; gap: 2px; }
    .radio-opc small { font-size: 9.5px; color: var(--gray-500); }
    .radio { width: 13px; height: 13px; border-radius: 50%; border: 1.5px solid var(--gray-300); background: #fff; }
    .radio.marcado { border-color: var(--sam-azul); background: radial-gradient(var(--sam-azul) 45%, #fff 50%); }
    .estado { font-size: 10.5px; color: var(--sam-naranja); font-weight: 700; }
    .obs { align-items: center; text-align: center; }
    .obs-texto { font-size: 11.5px; }
    .panel {
      margin: 0 0 8px; padding: 10px 12px; border-radius: var(--radius-md); background: #fff; border: 1px solid var(--gray-200);
      display: flex; flex-direction: column; gap: 10px;
    }
    .dos { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
    .campo { display: flex; flex-direction: column; gap: 4px; font-size: 12px; font-weight: 600; color: var(--gray-600); }
    .botones { display: flex; gap: 6px; justify-content: flex-end; flex-wrap: wrap; }
    @media (max-width: 899px) {
      .fila { grid-template-columns: 1fr 1fr; }
      .etq-movil { display: block; }
      .tipo { grid-column: 1 / -1; }
      .dos { grid-template-columns: 1fr; }
      .cumple, .obs { align-items: flex-start; text-align: left; }
    }
  `],
})
export class FilaFuenteComponent {
  readonly store = inject(AutoevaluacionStore);
  private readonly api = inject(AutoevaluacionService);
  private readonly modal = inject(ModalService);

  readonly fuente = input.required<NodoFuente>();
  readonly tecnica = input.required<EvaluationTechnique>();

  readonly panel = signal<Panel>(null);
  readonly archivos = signal<File[]>([]);
  enlace = '';
  notas = '';
  tipoObservacion: number | null = null;
  comentario = '';

  readonly disponibilidad = computed(() => DISPONIBILIDAD_FUENTE[this.fuente().availability]);
  readonly tecnicaTexto = computed(() => ETIQUETA_TECNICA[this.tecnica()]);
  readonly ultima = computed(() => this.fuente().evidence?.reviews.at(-1) ?? null);
  readonly editable = computed(() => this.store.puedeRegistrar() && !!this.fuente().evidence
    && this.fuente().evidence!.status !== 'APPROVED');
  readonly ocupado = computed(() => this.store.ocupado() === 'fuente-' + this.fuente().id);
  /** Sí: aprobada o institucional; No: observada o no implementada; null: pendiente. */
  readonly cumple = computed<boolean | null>(() => {
    const f = this.fuente();
    if (f.availability === 'PREDEFINED') return true;
    if (f.availability === 'NOT_IMPLEMENTED') return false;
    const s = f.evidence?.status;
    return s === 'APPROVED' ? true : s === 'OBSERVED' ? false : null;
  });

  estado = (s: keyof typeof ESTADO_EVIDENCIA) => ESTADO_EVIDENCIA[s];

  abrir(panel: Panel): void {
    this.archivos.set([]);
    this.tipoObservacion = null;
    this.comentario = '';
    if (panel === 'cargar') {
      this.enlace = '';
      this.notas = '';
    }
    this.panel.set(panel);
  }

  abrirDetalle(): void {
    const ev = this.fuente().evidence;
    this.enlace = ev?.externalUrl ?? '';
    this.notas = ev?.notes ?? '';
    this.panel.set('detalle');
  }

  cerrarPanel(): void {
    this.panel.set(null);
  }

  async cargar(): Promise<void> {
    if (await this.accion(this.api.registrarEvidencia(this.store.id(), this.fuente().id, this.archivos(), this.enlace, this.notas),
      'Sustento cargado: queda pendiente de revisión.')) this.cerrarPanel();
  }

  async agregar(): Promise<void> {
    if (await this.accion(this.api.agregarArchivos(this.evidenciaId(), this.archivos()), 'Archivos cargados.')) this.cerrarPanel();
  }

  async guardarDetalle(): Promise<void> {
    if (await this.accion(this.api.actualizarDetalle(this.evidenciaId(), this.enlace, this.notas), 'Sustento actualizado.')) this.cerrarPanel();
  }

  async quitarArchivo(archivoId: number, nombre: string): Promise<void> {
    if (!(await this.modal.confirm(`¿Quitar "${nombre}"? El archivo se eliminará.`, 'Quitar archivo', 'Quitar'))) return;
    await this.accion(this.api.quitarArchivo(this.evidenciaId(), archivoId), 'Archivo quitado.');
  }

  async reenviar(): Promise<void> {
    if (!(await this.modal.confirm('¿Ya corrigió lo observado? El sustento volverá a revisión del evaluador.', 'Reenviar a revisión', 'Reenviar'))) return;
    await this.accion(this.api.reenviar(this.evidenciaId()), 'Sustento reenviado a revisión.');
  }

  async retirar(): Promise<void> {
    if (!(await this.modal.confirm('Se eliminará el sustento con todos sus archivos. ¿Continuar?', 'Retirar sustento', 'Retirar'))) return;
    await this.accion(this.api.retirar(this.evidenciaId()), 'Sustento retirado.');
  }

  async aprobar(): Promise<void> {
    if (await this.accion(this.api.revisarEvidencia(this.evidenciaId(), 'APPROVED', null, this.comentario), 'Documento aprobado.')) this.cerrarPanel();
  }

  async observar(): Promise<void> {
    if (await this.accion(this.api.revisarEvidencia(this.evidenciaId(), 'OBSERVED', this.tipoObservacion, this.comentario),
      'Documento observado: la IPRESS deberá corregirlo.')) this.cerrarPanel();
  }

  private accion<T>(obs: Observable<T>, exito: string): Promise<boolean> {
    return this.store.ejecutar('fuente-' + this.fuente().id, obs, exito);
  }

  private evidenciaId(): number {
    return this.fuente().evidence!.id;
  }
}
