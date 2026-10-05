import { Component, DoCheck, computed, effect, inject, input, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ETIQUETA_TIPO_CRITERIO } from '../../../core/constants/lea';
import { Estandar } from '../../../core/models/catalogo.model';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { ModalService } from '../../../core/services/modal.service';
import { SearchableSelectComponent } from '../../../shared/components/searchable-select/searchable-select.component';
import { CatalogoStore } from '../catalogo.store';

/** Estándar: código y enunciado (se puede mover a otro macroproceso), con sus criterios. */
@Component({
  selector: 'app-estandar-editor',
  imports: [FormsModule, SearchableSelectComponent],
  template: `
    <div class="panel-blanco editor">
      <header class="cab">
        <span class="tipo">Estándar @if (macro(); as m) { · <button class="enlace" (click)="store.irA('m-' + m.id)">{{ m.code }} {{ m.name }}</button> }</span>
        <h2>{{ form.code || 'Nuevo estándar' }}</h2>
      </header>
      <div class="campos">
        <label class="campo corto">Código <input class="input" [(ngModel)]="form.code" maxlength="20" placeholder="DIR-1" /></label>
        <div class="campo largo">Macroproceso
          <app-select [options]="store.opcionesMacro()" [(ngModel)]="form.macroProcessId" />
        </div>
        <label class="campo completo">Enunciado del estándar
          <textarea class="input" rows="3" [(ngModel)]="form.statement" placeholder="El establecimiento de salud…"></textarea>
        </label>
      </div>

      @if (errores.length) { <ul class="errores">@for (e of errores; track e) { <li>{{ e }}</li> }</ul> }
      <div class="botones">
        @if (form.id) {
          <button class="btn btn-quitar" (click)="eliminar()" [disabled]="store.ocupado()"><i class="bi bi-trash"></i> Eliminar</button>
        }
        <span class="espacio"></span>
        @if (sucio) { <button class="btn btn-secondary" (click)="descartar()">Descartar cambios</button> }
        <button class="btn btn-azul" (click)="guardar()" [disabled]="store.ocupado() || errores.length > 0 || !sucio">
          <i class="bi bi-check-lg"></i> {{ form.id ? 'Guardar' : 'Crear estándar' }}
        </button>
      </div>
    </div>

    @if (nodo(); as e) {
      <div class="panel-blanco hijos">
        <div class="hijos-cab">
          <h3>Criterios de {{ e.code }} <small>{{ e.criteria.length }}</small></h3>
          <button class="btn btn-turquesa btn-sm" (click)="store.irA('nc-' + e.id)"><i class="bi bi-plus-lg"></i> Agregar criterio</button>
        </div>
        @for (c of e.criteria; track c.id) {
          <button class="hijo" (click)="store.irA('c-' + c.id)">
            <span class="codigo">{{ c.code }}</span>
            <span class="texto">{{ c.statement }}</span>
            <span class="chip">{{ tipo(c.type) }}</span>
            <span class="chip">{{ c.conditions }} cond.</span>
            @if (c.weightTotal !== 2) { <span class="chip mal" title="Los pesos de sus condiciones no suman 2">Pesos {{ c.weightTotal }}</span> }
            <i class="bi bi-chevron-right"></i>
          </button>
        } @empty {
          <p class="vacio">Aún no tiene criterios.</p>
        }
      </div>
    }
  `,
  styleUrl: './editor.css',
})
export class EstandarEditorComponent implements DoCheck {
  readonly store = inject(CatalogoStore);
  private readonly api = inject(CatalogoService);
  private readonly modal = inject(ModalService);

  readonly estandarId = input<number | null>(null);
  /** Macroproceso donde se crea (estándar nuevo). */
  readonly macroIdNuevo = input<number | null>(null);

  form: Estandar = { id: null, macroProcessId: 0, code: '', statement: '' };
  private original = '';
  errores: string[] = [];
  sucio = false;

  readonly nodo = computed(() => {
    const id = this.estandarId();
    for (const m of this.store.arbol()) for (const e of m.standards) if (e.id === id) return e;
    return null;
  });
  readonly macro = computed(() => this.store.arbol().find((m) => m.id === (this.nodo() ? this.store.ubicarEstandar(this.nodo()!.id)?.id : this.macroIdNuevo())) ?? null);

  tipo = (t: keyof typeof ETIQUETA_TIPO_CRITERIO) => ETIQUETA_TIPO_CRITERIO[t];

  constructor() {
    effect(() => {
      const id = this.estandarId();
      const nodo = this.nodo();
      const macroNuevo = this.macroIdNuevo();
      untracked(() => {
        if (id && !nodo) return;
        if (nodo) {
          this.form = { id: nodo.id, macroProcessId: this.store.ubicarEstandar(nodo.id)!.id, code: nodo.code, statement: nodo.statement };
        } else {
          const m = this.store.arbol().find((x) => x.id === macroNuevo);
          this.form = { id: null, macroProcessId: macroNuevo ?? 0, code: m ? `${m.code}-${m.standards.length + 1}` : '', statement: '' };
        }
        this.original = JSON.stringify(this.form);
      });
    });
  }

  ngDoCheck(): void {
    this.sucio = JSON.stringify(this.form) !== this.original;
    if (this.store.sucio() !== this.sucio) queueMicrotask(() => this.store.sucio.set(this.sucio));
    this.errores = [];
    if (!this.form.code?.trim()) this.errores.push('Indique el código.');
    if (!this.form.macroProcessId) this.errores.push('Elija el macroproceso.');
    if (!this.form.statement?.trim()) this.errores.push('Escriba el enunciado del estándar.');
  }

  descartar(): void {
    this.form = JSON.parse(this.original);
  }

  async guardar(): Promise<void> {
    const r = await this.store.ejecutar(this.api.guardarEstandar({
      ...this.form, macroProcessId: Number(this.form.macroProcessId), code: this.form.code.trim(), statement: this.form.statement.trim(),
    }), this.form.id ? 'Estándar guardado.' : 'Estándar creado.');
    if (!r) return;
    this.original = JSON.stringify(this.form);
    this.store.sucio.set(false);
    this.store.recargarArbol();
    if (!this.form.id) this.store.irA('e-' + r.id, 'estructura', false);
  }

  async eliminar(): Promise<void> {
    if (!(await this.modal.confirm(`¿Eliminar el estándar ${this.form.code}?`, 'Eliminar estándar', 'Eliminar'))) return;
    if (await this.store.ejecutar(this.api.eliminarEstandar(this.form.id!), 'Estándar eliminado.')) {
      const macroId = this.form.macroProcessId;
      this.store.recargarArbol();
      this.store.irA('m-' + macroId, 'estructura', false);
    }
  }
}
