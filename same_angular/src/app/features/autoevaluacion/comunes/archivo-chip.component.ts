import { Component, inject, input, output } from '@angular/core';
import { ArchivoVista } from '../../../core/models/autoevaluacion.model';
import { AutoevaluacionStore } from '../autoevaluacion.store';

/** Un archivo guardado: clic para verlo o descargarlo; opcionalmente con botón para quitarlo. */
@Component({
  selector: 'app-archivo-chip',
  template: `
    <span class="chip">
      <button type="button" class="abrir" (click)="store.abrirArchivo(archivo().id, archivo().name)"
              [title]="'Abrir ' + archivo().name">
        <i [class]="icono()"></i>
        <span class="nombre">{{ archivo().name }}</span>
      </button>
      @if (quitable()) {
        <button type="button" class="quitar" (click)="quitar.emit()" [disabled]="deshabilitado()"
                [attr.aria-label]="'Quitar ' + archivo().name" title="Quitar archivo">
          <i class="bi bi-x-lg"></i>
        </button>
      }
    </span>
  `,
  styles: [`
    .chip {
      display: inline-flex; align-items: center; max-width: 100%; border: 1px solid var(--gray-200);
      border-radius: var(--radius-full); background: #fff; font-size: 12.5px; overflow: hidden;
    }
    .abrir {
      display: inline-flex; align-items: center; gap: 6px; padding: 4px 10px; border: none; background: none;
      cursor: pointer; color: var(--essalud-main); min-width: 0;
    }
    .abrir:hover .nombre { text-decoration: underline; }
    .nombre { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 260px; }
    .quitar {
      border: none; border-left: 1px solid var(--gray-200); background: none; padding: 4px 8px;
      cursor: pointer; color: var(--gray-500);
    }
    .quitar:hover:not(:disabled) { color: var(--danger-text); background: var(--danger-bg); }
  `],
})
export class ArchivoChipComponent {
  readonly store = inject(AutoevaluacionStore);
  readonly archivo = input.required<ArchivoVista>();
  readonly quitable = input(false);
  readonly deshabilitado = input(false);
  readonly quitar = output<void>();

  icono(): string {
    const tipo = this.archivo().contentType;
    if (tipo === 'application/pdf') return 'bi bi-file-earmark-pdf-fill';
    if (tipo.startsWith('image/')) return 'bi bi-file-earmark-image';
    if (tipo.includes('sheet') || tipo.includes('excel')) return 'bi bi-file-earmark-spreadsheet';
    if (tipo.includes('word')) return 'bi bi-file-earmark-word';
    return 'bi bi-file-earmark';
  }
}
