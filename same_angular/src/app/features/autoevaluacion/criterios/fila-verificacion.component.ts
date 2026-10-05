import { Component, computed, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ETIQUETA_TECNICA, IMAGENES_ACEPTADAS } from '../../../core/constants/lea';
import { NodoCondicion } from '../../../core/models/autoevaluacion.model';
import { AutoevaluacionService } from '../../../core/services/autoevaluacion.service';
import { ModalService } from '../../../core/services/modal.service';
import { ArchivoChipComponent } from '../comunes/archivo-chip.component';
import { SelectorArchivosComponent } from '../comunes/selector-archivos.component';
import { AutoevaluacionStore } from '../autoevaluacion.store';

/**
 * Fila del Anexo 2 para una condición que se verifica en campo (observación, entrevista,
 * auditoría de HC): qué verificar | técnica | fotos | cumple | observación. Solo el evaluador la registra.
 */
@Component({
  selector: 'app-fila-verificacion',
  imports: [FormsModule, DatePipe, ArchivoChipComponent, SelectorArchivosComponent],
  template: `
    @let c = condicion();
    @let r = c.review;
    <div class="fila">
      <div class="celda tipo">
        <span class="etq-movil">Tipo de documento</span>
        <span class="nombre">Verificación en campo</span>
        @if (guia().length) {
          <ul class="guia">@for (l of guia(); track $index) { <li>{{ l }}</li> }</ul>
        }
      </div>
      <div class="celda"><span class="etq-movil">Técnica utilizada</span>{{ tecnica() }}</div>
      <div class="celda">
        <span class="etq-movil">Archivo</span>
        @if (r?.attachments?.length) {
          <div class="archivos">
            @for (a of r!.attachments; track a.id) {
              <app-archivo-chip [archivo]="a" [quitable]="store.puedeEvaluar()" [deshabilitado]="ocupado()" (quitar)="quitarFoto(r!.id, a.id, a.name)" />
            }
          </div>
        } @else {
          <span class="tenue">{{ r ? 'Sin fotos' : 'Pendiente de verificación' }}</span>
        }
        @if (r) { <span class="meta">{{ r.reviewedByName }} · {{ r.reviewedAt | date: 'dd/MM/yy HH:mm' }}</span> }
      </div>
      <div class="celda cumple">
        <span class="etq-movil">Cumple</span>
        <span class="radios">
          <button type="button" class="radio-opc" [disabled]="!store.puedeEvaluar()" (click)="abrir(true)" title="Cumple">
            <small>SI</small><span class="radio" [class.marcado]="r?.met === true"></span>
          </button>
          <button type="button" class="radio-opc" [disabled]="!store.puedeEvaluar()" (click)="abrir(false)" title="No cumple">
            <small>NO</small><span class="radio" [class.marcado]="r?.met === false"></span>
          </button>
        </span>
      </div>
      <div class="celda obs">
        <span class="etq-movil">Observación</span>
        @if (r?.comment) { <span class="obs-texto" [class.no]="r?.met === false">{{ r!.comment }}</span> }
        @if (store.puedeEvaluar()) {
          <button class="icono" (click)="abrir(r?.met ?? null)" title="Registrar verificación"><i class="bi bi-pencil-square"></i></button>
        }
      </div>
    </div>

    @if (abierto()) {
      <div class="panel">
        <div class="opciones">
          <label class="opcion" [class.sel]="cumpleSel === true"><input type="radio" [name]="'met-' + c.id" [value]="true" [(ngModel)]="cumpleSel" /> Cumple</label>
          <label class="opcion" [class.sel]="cumpleSel === false"><input type="radio" [name]="'met-' + c.id" [value]="false" [(ngModel)]="cumpleSel" /> No cumple</label>
        </div>
        <label class="campo">Comentario {{ cumpleSel === false ? '(obligatorio)' : '(opcional)' }}
          <input class="input" [(ngModel)]="comentario" maxlength="2000"
                 [placeholder]="cumpleSel === false ? 'Indique por qué no cumple' : 'Lo verificado en campo'" />
        </label>
        <app-selector-archivos [(archivos)]="fotos" [aceptados]="imagenes" ayuda="Fotos JPG o PNG · hasta 20 MB c/u" />
        <div class="botones">
          <button class="btn btn-secondary btn-sm" (click)="abierto.set(false)">Cancelar</button>
          <button class="btn btn-azul btn-sm" (click)="guardar()"
                  [disabled]="ocupado() || cumpleSel === null || (cumpleSel === false && !comentario.trim())">Grabar verificación</button>
        </div>
      </div>
    }
  `,
  styles: [`
    :host { display: block; }
    .fila { display: grid; grid-template-columns: var(--cols-fuente); gap: 10px; padding: 8px 0; align-items: start; }
    .celda { display: flex; flex-direction: column; gap: 4px; min-width: 0; font-size: 12.5px; color: var(--gray-700); }
    .etq-movil { display: none; font-size: 10.5px; font-weight: 700; color: var(--sam-azul); text-transform: uppercase; }
    .nombre { font-weight: 600; color: var(--gray-800); }
    .guia { margin: 0; padding-left: 16px; color: var(--gray-600); font-size: 12px; }
    .archivos { display: flex; flex-wrap: wrap; gap: 4px; }
    .tenue { color: var(--gray-400); }
    .meta { font-size: 11px; color: var(--gray-400); }
    .cumple { align-items: center; }
    .radios { display: flex; gap: 10px; }
    .radio-opc { display: flex; flex-direction: column; align-items: center; gap: 2px; border: none; background: none; padding: 0; cursor: pointer; }
    .radio-opc:disabled { cursor: default; }
    .radio-opc small { font-size: 9.5px; color: var(--gray-500); }
    .radio { width: 13px; height: 13px; border-radius: 50%; border: 1.5px solid var(--gray-300); background: #fff; }
    .radio.marcado { border-color: var(--sam-azul); background: radial-gradient(var(--sam-azul) 45%, #fff 50%); }
    .obs { align-items: center; text-align: center; }
    .obs-texto { font-size: 11.5px; }
    .obs-texto.no { color: var(--danger-text); }
    .icono { border: none; background: none; cursor: pointer; color: var(--sam-azul); font-size: 19px; padding: 2px 4px; }
    .panel {
      margin: 0 0 8px; padding: 10px 12px; border-radius: var(--radius-md); background: #fff; border: 1px solid var(--gray-200);
      display: flex; flex-direction: column; gap: 10px;
    }
    .opciones { display: flex; gap: 8px; }
    .opcion {
      display: inline-flex; align-items: center; gap: 6px; padding: 6px 12px; font-size: 13px; cursor: pointer;
      border: 1.5px solid var(--gray-200); border-radius: var(--radius-md); background: #fff;
    }
    .opcion.sel { border-color: var(--sam-azul); background: var(--sam-azul-suave); }
    .campo { display: flex; flex-direction: column; gap: 4px; font-size: 12px; font-weight: 600; color: var(--gray-600); }
    .botones { display: flex; gap: 6px; justify-content: flex-end; }
    @media (max-width: 899px) {
      .fila { grid-template-columns: 1fr 1fr; }
      .etq-movil { display: block; }
      .tipo { grid-column: 1 / -1; }
      .cumple, .obs { align-items: flex-start; text-align: left; }
    }
  `],
})
export class FilaVerificacionComponent {
  readonly store = inject(AutoevaluacionStore);
  private readonly api = inject(AutoevaluacionService);
  private readonly modal = inject(ModalService);

  readonly condicion = input.required<NodoCondicion>();
  readonly imagenes = IMAGENES_ACEPTADAS;

  readonly abierto = signal(false);
  readonly fotos = signal<File[]>([]);
  cumpleSel: boolean | null = null;
  comentario = '';

  readonly guia = computed(() => (this.condicion().verificationGuide ?? '').split('\n').filter((l) => l.trim()));
  readonly tecnica = computed(() => ETIQUETA_TECNICA[this.condicion().technique]);
  readonly ocupado = computed(() => this.store.ocupado() === 'condicion-' + this.condicion().id);

  abrir(cumple: boolean | null): void {
    if (!this.store.puedeEvaluar()) return;
    const r = this.condicion().review;
    this.cumpleSel = cumple;
    this.comentario = r?.comment ?? '';
    this.fotos.set([]);
    this.abierto.set(true);
  }

  async guardar(): Promise<void> {
    const ok = await this.store.ejecutar('condicion-' + this.condicion().id,
      this.api.verificarCondicion(this.store.id(), this.condicion().id, this.cumpleSel === true, this.comentario, this.fotos()),
      'Verificación registrada.');
    if (ok) this.abierto.set(false);
  }

  async quitarFoto(verificacionId: number, archivoId: number, nombre: string): Promise<void> {
    if (!(await this.modal.confirm(`¿Quitar "${nombre}"?`, 'Quitar foto', 'Quitar'))) return;
    await this.store.ejecutar('condicion-' + this.condicion().id, this.api.quitarFotoVerificacion(verificacionId, archivoId), 'Foto quitada.');
  }
}
