import { Component, Input, input, model } from '@angular/core';
import { ARCHIVOS_ACEPTADOS, ARCHIVOS_POR_ENVIO, TAMANIO_MAXIMO_MB } from '../../../core/constants/lea';

/**
 * Elige uno o varios archivos (clic o arrastrar y soltar). Valida en el navegador la extensión,
 * el tamaño y la cantidad para avisar antes de subir; el core vuelve a validar todo, incluido el
 * contenido real del archivo.
 */
@Component({
  selector: 'app-selector-archivos',
  template: `
    <div class="zona" [class.arrastrando]="arrastrando" (dragover)="alArrastrar($event)" (dragleave)="arrastrando = false"
         (drop)="alSoltar($event)" (click)="entrada.click()" role="button" tabindex="0"
         (keydown.enter)="entrada.click()" (keydown.space)="entrada.click(); $event.preventDefault()">
      <i class="bi bi-cloud-arrow-up"></i>
      <span><strong>Elegir archivos</strong> o arrástrelos aquí</span>
      <small>{{ ayuda() }}</small>
      <input #entrada type="file" [accept]="aceptados()" multiple hidden (change)="alElegir(entrada)" />
    </div>
    @if (error) { <div class="error"><i class="bi bi-exclamation-circle"></i> {{ error }}</div> }
    @if (archivos().length) {
      <ul class="lista">
        @for (a of archivos(); track $index) {
          <li>
            <i class="bi bi-paperclip"></i>
            <span class="nombre" [title]="a.name">{{ a.name }}</span>
            <span class="peso">{{ tamanio(a.size) }}</span>
            <button type="button" class="quitar" (click)="quitar($index)" [attr.aria-label]="'Quitar ' + a.name">
              <i class="bi bi-x-lg"></i>
            </button>
          </li>
        }
      </ul>
    }
  `,
  styles: [`
    .zona {
      display: flex; flex-direction: column; align-items: center; gap: 2px; padding: 14px; cursor: pointer;
      border: 1.5px dashed var(--gray-300); border-radius: var(--radius-md); background: var(--gray-50);
      color: var(--gray-600); font-size: 13px; text-align: center; transition: all var(--transition-fast);
    }
    .zona:hover, .zona:focus-visible, .zona.arrastrando {
      border-color: var(--essalud-main); background: var(--info-bg); color: var(--essalud-main); outline: none;
    }
    .zona i { font-size: 22px; }
    .zona small { color: var(--gray-500); font-size: 11.5px; }
    .error { margin-top: 6px; font-size: 12px; color: var(--danger-text); }
    .lista { list-style: none; margin: 8px 0 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
    .lista li {
      display: flex; align-items: center; gap: 8px; padding: 5px 8px; font-size: 12.5px;
      background: #fff; border: 1px solid var(--gray-200); border-radius: var(--radius-sm);
    }
    .nombre { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .peso { color: var(--gray-500); font-size: 11.5px; }
    .quitar { border: none; background: none; color: var(--gray-500); cursor: pointer; padding: 2px 4px; }
    .quitar:hover { color: var(--danger-text); }
  `],
})
export class SelectorArchivosComponent {
  readonly archivos = model<File[]>([]);
  readonly aceptados = input(ARCHIVOS_ACEPTADOS);
  readonly ayuda = input(`PDF, imágenes u Office · hasta ${TAMANIO_MAXIMO_MB} MB c/u · máx. ${ARCHIVOS_POR_ENVIO} por envío`);
  @Input() maximo = ARCHIVOS_POR_ENVIO;

  arrastrando = false;
  error = '';

  alArrastrar(e: DragEvent): void {
    e.preventDefault();
    this.arrastrando = true;
  }

  alSoltar(e: DragEvent): void {
    e.preventDefault();
    this.arrastrando = false;
    this.agregar(Array.from(e.dataTransfer?.files ?? []));
  }

  alElegir(entrada: HTMLInputElement): void {
    this.agregar(Array.from(entrada.files ?? []));
    entrada.value = '';
  }

  quitar(i: number): void {
    this.archivos.update((lista) => lista.filter((_, j) => j !== i));
    this.error = '';
  }

  tamanio(bytes: number): string {
    return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }

  private agregar(nuevos: File[]): void {
    this.error = '';
    const extensiones = this.aceptados().split(',');
    const validos: File[] = [];
    for (const f of nuevos) {
      const ext = '.' + (f.name.split('.').pop() ?? '').toLowerCase();
      if (!extensiones.includes(ext)) {
        this.error = `"${f.name}": tipo de archivo no permitido.`;
      } else if (f.size > TAMANIO_MAXIMO_MB * 1024 * 1024) {
        this.error = `"${f.name}" supera los ${TAMANIO_MAXIMO_MB} MB.`;
      } else if (f.size === 0) {
        this.error = `"${f.name}" está vacío.`;
      } else {
        validos.push(f);
      }
    }
    const total = [...this.archivos(), ...validos];
    if (total.length > this.maximo) {
      this.error = `Puede adjuntar hasta ${this.maximo} archivos por envío.`;
    }
    this.archivos.set(total.slice(0, this.maximo));
  }
}
