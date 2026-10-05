import { Component, DoCheck, computed, effect, inject, input, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ETIQUETA_CATEGORIA_MACRO } from '../../../core/constants/lea';
import { MacroProcessCategory } from '../../../core/models/autoevaluacion.model';
import { MacroProceso } from '../../../core/models/catalogo.model';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { ModalService } from '../../../core/services/modal.service';
import { CatalogoStore } from '../catalogo.store';

/** Macroproceso: datos y peso en el puntaje final, con sus estándares. */
@Component({
  selector: 'app-macro-editor',
  imports: [FormsModule],
  template: `
    <div class="panel-blanco editor">
      <header class="cab">
        <span class="tipo">Macroproceso</span>
        <h2>{{ form.code || 'Nuevo macroproceso' }} @if (form.name) { <small>{{ form.name }}</small> }</h2>
      </header>

      <div class="campos">
        <label class="campo corto">Código <input class="input" [(ngModel)]="form.code" maxlength="20" placeholder="DIR" /></label>
        <label class="campo largo">Nombre <input class="input" [(ngModel)]="form.name" maxlength="250" placeholder="Direccionamiento" /></label>
        <label class="campo">Categoría
          <select class="input" [(ngModel)]="form.category">
            @for (c of categorias; track c.value) { <option [ngValue]="c.value">{{ c.label }}</option> }
          </select>
        </label>
        <label class="campo corto">Peso (%) <input class="input" type="number" min="0" max="100" step="0.5" [(ngModel)]="form.scoreWeight" /></label>
        <label class="campo corto">Orden <input class="input" type="number" min="1" [(ngModel)]="form.displayOrder" /></label>
      </div>
      <p class="pesos" [class.mal]="sumaConCambio() !== 100">
        <i [class]="sumaConCambio() === 100 ? 'bi bi-check-circle-fill' : 'bi bi-exclamation-triangle-fill'"></i>
        Con este peso, los macroprocesos suman {{ sumaConCambio() }} % (deben sumar 100 %: GER 35, PRE 40, APO 25).
      </p>

      @if (errores.length) { <ul class="errores">@for (e of errores; track e) { <li>{{ e }}</li> }</ul> }
      <div class="botones">
        @if (form.id) {
          <button class="btn btn-quitar" (click)="eliminar()" [disabled]="store.ocupado()"><i class="bi bi-trash"></i> Eliminar</button>
        }
        <span class="espacio"></span>
        @if (sucio) { <button class="btn btn-secondary" (click)="descartar()">Descartar cambios</button> }
        <button class="btn btn-azul" (click)="guardar()" [disabled]="store.ocupado() || errores.length > 0 || !sucio">
          <i class="bi bi-check-lg"></i> {{ form.id ? 'Guardar' : 'Crear macroproceso' }}
        </button>
      </div>
    </div>

    @if (nodo(); as m) {
      <div class="panel-blanco hijos">
        <div class="hijos-cab">
          <h3>Estándares de {{ m.code }} <small>{{ m.standards.length }}</small></h3>
          <button class="btn btn-turquesa btn-sm" (click)="store.irA('ne-' + m.id)"><i class="bi bi-plus-lg"></i> Agregar estándar</button>
        </div>
        @for (e of m.standards; track e.id) {
          <button class="hijo" (click)="store.irA('e-' + e.id)">
            <span class="codigo">{{ e.code }}</span>
            <span class="texto">{{ e.statement }}</span>
            <span class="chip">{{ e.criteria.length }} criterio(s)</span>
            <i class="bi bi-chevron-right"></i>
          </button>
        } @empty {
          <p class="vacio">Aún no tiene estándares.</p>
        }
      </div>
    }
  `,
  styleUrl: './editor.css',
})
export class MacroEditorComponent implements DoCheck {
  readonly store = inject(CatalogoStore);
  private readonly api = inject(CatalogoService);
  private readonly modal = inject(ModalService);

  readonly macroId = input<number | null>(null);
  readonly categorias = (Object.keys(ETIQUETA_CATEGORIA_MACRO) as MacroProcessCategory[])
    .map((c) => ({ value: c, label: ETIQUETA_CATEGORIA_MACRO[c] }));

  form: MacroProceso = this.vacio();
  private original = '';
  errores: string[] = [];
  sucio = false;

  readonly nodo = computed(() => this.store.arbol().find((m) => m.id === this.macroId()) ?? null);

  constructor() {
    effect(() => {
      const id = this.macroId();
      const nodo = this.nodo();
      untracked(() => {
        if (id && !nodo) return;
        this.form = nodo
          ? { id: nodo.id, code: nodo.code, name: nodo.name, category: nodo.category, scoreWeight: Number(nodo.scoreWeight), displayOrder: nodo.displayOrder }
          : this.vacio();
        this.original = JSON.stringify(this.form);
      });
    });
  }

  ngDoCheck(): void {
    this.sucio = JSON.stringify(this.form) !== this.original;
    if (this.store.sucio() !== this.sucio) queueMicrotask(() => this.store.sucio.set(this.sucio));
    this.errores = [];
    if (!this.form.code?.trim()) this.errores.push('Indique el código.');
    if (!this.form.name?.trim()) this.errores.push('Indique el nombre.');
    if (this.form.scoreWeight === null || this.form.scoreWeight < 0 || this.form.scoreWeight > 100) this.errores.push('El peso debe estar entre 0 y 100.');
    if (!this.form.displayOrder) this.errores.push('Indique el orden.');
  }

  sumaConCambio(): number {
    const anterior = this.nodo() ? Number(this.nodo()!.scoreWeight) : 0;
    return Math.round((this.store.sumaPesos() - anterior + Number(this.form.scoreWeight || 0)) * 100) / 100;
  }

  descartar(): void {
    this.form = JSON.parse(this.original);
  }

  async guardar(): Promise<void> {
    const r = await this.store.ejecutar(this.api.guardarMacro({ ...this.form, code: this.form.code.trim(), name: this.form.name.trim() }),
      this.form.id ? 'Macroproceso guardado.' : 'Macroproceso creado.');
    if (!r) return;
    this.original = JSON.stringify(this.form);
    this.store.sucio.set(false);
    this.store.recargarArbol();
    if (!this.form.id) this.store.irA('m-' + r.id, 'estructura', false);
  }

  async eliminar(): Promise<void> {
    if (!(await this.modal.confirm(`¿Eliminar el macroproceso ${this.form.code}?`, 'Eliminar macroproceso', 'Eliminar'))) return;
    if (await this.store.ejecutar(this.api.eliminarMacro(this.form.id!), 'Macroproceso eliminado.')) {
      this.store.recargarArbol();
      this.store.irA(null, 'estructura', false);
    }
  }

  private vacio(): MacroProceso {
    return { id: null, code: '', name: '', category: 'MANAGEMENT', scoreWeight: 0, displayOrder: this.store.arbol().length + 1 };
  }
}
