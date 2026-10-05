import { Component, HostListener, OnInit, computed, inject, input } from '@angular/core';
import { LoaderComponent } from '../../shared/components/loader/loader.component';
import { CatalogoStore } from './catalogo.store';
import { EstructuraComponent } from './estructura/estructura.component';
import { NormativaComponent } from './normativa/normativa.component';
import { ProcesosComponent } from './procesos/procesos.component';
import { TiposObservacionComponent } from './tipos-observacion/tipos-observacion.component';

type Pestana = 'estructura' | 'procesos' | 'normativa' | 'observaciones';

/**
 * "Macroprocesos, procesos y criterios": todo el catálogo de acreditación en una sola opción. La
 * estructura (macroproceso → estándar → criterio) se edita en un árbol con su detalle al lado; los
 * procesos institucionales y la normativa muestran qué criterios los usan.
 */
@Component({
  selector: 'app-catalogo',
  imports: [LoaderComponent, EstructuraComponent, ProcesosComponent, NormativaComponent, TiposObservacionComponent],
  providers: [CatalogoStore],
  template: `
    <div class="migas">
      <span>Administración</span>
      <span class="sep">&gt;</span>
      <span class="actual">Macroprocesos, procesos y criterios</span>
    </div>

    <nav class="pestanas" role="tablist">
      @for (p of pestanas; track p.id) {
        <button role="tab" [class.sel]="actual() === p.id" [attr.aria-selected]="actual() === p.id" (click)="cambiar(p.id)">
          <i [class]="p.icono"></i> {{ p.texto }}
        </button>
      }
    </nav>

    @if (store.error()) { <div class="alert-error">{{ store.error() }}</div> }
    @if (store.cargando()) {
      <app-loader label="Cargando el catálogo..." />
    } @else {
      @switch (actual()) {
        @case ('estructura') { <app-estructura [sel]="sel()" /> }
        @case ('procesos') { <app-procesos /> }
        @case ('normativa') { <app-normativa /> }
        @case ('observaciones') { <app-tipos-observacion /> }
      }
    }
  `,
  styles: [`
    .pestanas { display: flex; gap: 4px; border-bottom: 1px solid var(--gray-200); margin-bottom: 16px; overflow-x: auto; }
    .pestanas button {
      display: inline-flex; align-items: center; gap: 6px; padding: 10px 16px; border: none; background: none; cursor: pointer;
      font-size: 14px; font-weight: 600; color: var(--gray-500); border-bottom: 3px solid transparent; margin-bottom: -1px;
      white-space: nowrap; text-transform: uppercase; letter-spacing: .02em;
    }
    .pestanas button:hover { color: var(--sam-azul); }
    .pestanas button.sel { color: var(--sam-azul); border-bottom-color: var(--sam-azul); }
  `],
})
export class CatalogoComponent implements OnInit {
  readonly store = inject(CatalogoStore);

  /** Parámetros de la URL (withComponentInputBinding). */
  readonly tab = input<string | undefined>();
  readonly sel = input<string | undefined>();

  readonly pestanas: { id: Pestana; texto: string; icono: string }[] = [
    { id: 'estructura', texto: 'Macroprocesos y criterios', icono: 'bi bi-diagram-3' },
    { id: 'procesos', texto: 'Procesos institucionales', icono: 'bi bi-diagram-2' },
    { id: 'normativa', texto: 'Normativa', icono: 'bi bi-journal-text' },
    { id: 'observaciones', texto: 'Tipos de observación', icono: 'bi bi-flag' },
  ];

  readonly actual = computed<Pestana>(() => {
    const t = this.tab();
    return t === 'procesos' || t === 'normativa' || t === 'observaciones' ? t : 'estructura';
  });

  ngOnInit(): void {
    this.store.cargarTodo();
  }

  cambiar(p: Pestana): void {
    this.store.irA(p === 'estructura' ? this.sel() ?? null : null, p);
  }

  /** Para el guard de la ruta: salir del módulo con cambios sin guardar pregunta antes. */
  puedeSalir(): Promise<boolean> {
    return this.store.puedeDescartar();
  }

  @HostListener('window:beforeunload', ['$event'])
  alSalirDelNavegador(e: BeforeUnloadEvent): void {
    if (this.store.sucio()) e.preventDefault();
  }
}
