import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CATEGORIAS_IPRESS, ETIQUETA_CATEGORIA_MACRO, ETIQUETA_TIPO_CRITERIO } from '../../../core/constants/lea';
import { CriterionType, FacilityCategory, MacroProcessCategory } from '../../../core/models/autoevaluacion.model';
import { NodoMacroCat, ResumenCriterio } from '../../../core/models/catalogo.model';
import { CatalogoStore } from '../catalogo.store';
import { CriterioEditorComponent } from './criterio-editor.component';
import { EstandarEditorComponent } from './estandar-editor.component';
import { MacroEditorComponent } from './macro-editor.component';

/** Lo elegido en el árbol, leído de ?sel= (m-1, e-2, c-3, nm, ne-1, nc-2). */
interface Seleccion {
  tipo: 'macro' | 'estandar' | 'criterio' | 'nuevo-macro' | 'nuevo-estandar' | 'nuevo-criterio' | null;
  id: number | null;
}

/**
 * Árbol del catálogo (categoría → macroproceso → estándar → criterio) con buscador y filtros, y el
 * editor del elemento elegido al lado. Lo nuevo se agrega en su lugar del árbol.
 */
@Component({
  selector: 'app-estructura',
  imports: [FormsModule, MacroEditorComponent, EstandarEditorComponent, CriterioEditorComponent],
  template: `
    <div class="estructura">
      <aside class="arbol panel-blanco">
        <div class="arbol-herramientas">
          <input class="input" [ngModel]="texto()" (ngModelChange)="texto.set($event)" placeholder="Buscar código o texto…" aria-label="Buscar en el catálogo" />
          <div class="filtros">
            <select class="input" [ngModel]="categoria()" (ngModelChange)="categoria.set($event)" aria-label="Categoría de IPRESS">
              <option [ngValue]="null">Todas las categorías</option>
              @for (c of categorias; track c.value) { <option [ngValue]="c.value">Aplica a {{ c.label }}</option> }
            </select>
            <select class="input" [ngModel]="tipo()" (ngModelChange)="tipo.set($event)" aria-label="Tipo de criterio">
              <option [ngValue]="null">Todos los tipos</option>
              @for (t of tipos; track t.value) { <option [ngValue]="t.value">{{ t.label }}</option> }
            </select>
          </div>
          <div class="resumen">
            <span [class.mal]="store.sumaPesos() !== 100" title="Los pesos de los macroprocesos deben sumar 100 %">
              <i [class]="store.sumaPesos() === 100 ? 'bi bi-check-circle-fill' : 'bi bi-exclamation-triangle-fill'"></i>
              Pesos {{ store.sumaPesos() }} %
            </span>
            <span>{{ totalCriterios() }} criterios</span>
            <button class="btn btn-azul btn-sm" (click)="store.irA('nm')"><i class="bi bi-plus-lg"></i> Macroproceso</button>
          </div>
        </div>

        <div class="nodos" role="tree">
          @for (g of grupos(); track g.categoria) {
            <div class="grupo">{{ g.etiqueta }} <small>{{ g.peso }} %</small></div>
            @for (m of g.macros; track m.id) {
              <div class="fila macro" role="treeitem" [class.sel]="esSel('macro', m.id)" [attr.aria-expanded]="abierto('m', m.id)">
                <button class="flecha" (click)="alternar('m', m.id)" [attr.aria-label]="abierto('m', m.id) ? 'Plegar' : 'Desplegar'">
                  <i [class]="abierto('m', m.id) ? 'bi bi-chevron-down' : 'bi bi-chevron-right'"></i>
                </button>
                <button class="etiqueta" (click)="store.irA('m-' + m.id)">
                  <span class="codigo">{{ m.code }}</span> <span class="texto">{{ m.name }}</span>
                </button>
                <span class="conteo" title="Peso en el puntaje">{{ m.scoreWeight }}%</span>
              </div>
              @if (abierto('m', m.id)) {
                @for (e of m.standards; track e.id) {
                  <div class="fila estandar" role="treeitem" [class.sel]="esSel('estandar', e.id)">
                    <button class="flecha" (click)="alternar('e', e.id)" [attr.aria-label]="abierto('e', e.id) ? 'Plegar' : 'Desplegar'">
                      <i [class]="abierto('e', e.id) ? 'bi bi-chevron-down' : 'bi bi-chevron-right'"></i>
                    </button>
                    <button class="etiqueta" (click)="store.irA('e-' + e.id)" [title]="e.statement">
                      <span class="codigo">{{ e.code }}</span> <span class="texto">{{ e.statement }}</span>
                    </button>
                    <span class="conteo">{{ e.criteria.length }}</span>
                  </div>
                  @if (abierto('e', e.id)) {
                    @for (c of e.criteria; track c.id) {
                      <div class="fila criterio" role="treeitem" [class.sel]="esSel('criterio', c.id)">
                        <button class="etiqueta" (click)="store.irA('c-' + c.id)" [title]="c.statement">
                          <span class="codigo">{{ c.code }}</span> <span class="texto">{{ c.statement }}</span>
                        </button>
                        @if (c.weightTotal !== 2) {
                          <i class="bi bi-exclamation-triangle-fill aviso" title="Los pesos de sus condiciones no suman 2"></i>
                        }
                      </div>
                    }
                    <button class="agregar nivel3" (click)="store.irA('nc-' + e.id)"><i class="bi bi-plus"></i> Agregar criterio</button>
                  }
                }
                <button class="agregar nivel2" (click)="store.irA('ne-' + m.id)"><i class="bi bi-plus"></i> Agregar estándar</button>
              }
            }
          } @empty {
            <p class="vacio">Nada coincide con la búsqueda.</p>
          }
        </div>
      </aside>

      <section class="detalle">
        @switch (seleccion().tipo) {
          @case ('macro') { <app-macro-editor [macroId]="seleccion().id" /> }
          @case ('nuevo-macro') { <app-macro-editor [macroId]="null" /> }
          @case ('estandar') { <app-estandar-editor [estandarId]="seleccion().id" [macroIdNuevo]="null" /> }
          @case ('nuevo-estandar') { <app-estandar-editor [estandarId]="null" [macroIdNuevo]="seleccion().id" /> }
          @case ('criterio') { <app-criterio-editor [criterioId]="seleccion().id" [estandarIdNuevo]="null" /> }
          @case ('nuevo-criterio') { <app-criterio-editor [criterioId]="null" [estandarIdNuevo]="seleccion().id" /> }
          @default {
            <div class="panel-blanco bienvenida">
              <i class="bi bi-diagram-3"></i>
              <h3>Elija un macroproceso, estándar o criterio</h3>
              <p>
                Navegue el árbol de la izquierda o búsquelo por código o texto. Cada elemento se edita aquí junto con lo que
                contiene: un macroproceso con sus estándares, un estándar con sus criterios y un criterio con sus condiciones,
                fuentes auditables, procesos institucionales y normativa.
              </p>
            </div>
          }
        }
      </section>
    </div>
  `,
  styles: [`
    .estructura { display: grid; grid-template-columns: 360px minmax(0, 1fr); gap: 18px; align-items: start; }
    .arbol { position: sticky; top: 0; padding: 0; display: flex; flex-direction: column; max-height: calc(100vh - 210px); }
    .arbol-herramientas { padding: 14px; border-bottom: 1px solid var(--gray-200); display: flex; flex-direction: column; gap: 8px; }
    .filtros { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
    .filtros .input { padding: 7px 8px; font-size: 12.5px; }
    .resumen { display: flex; align-items: center; gap: 10px; font-size: 12px; color: var(--gray-600); }
    .resumen span:first-child { color: var(--success-text); font-weight: 700; }
    .resumen span.mal { color: var(--danger-text); }
    .resumen .btn { margin-left: auto; }
    .nodos { overflow-y: auto; padding: 6px 6px 12px; }
    .grupo {
      padding: 12px 8px 4px; font-size: 11px; font-weight: 800; color: var(--gray-500); text-transform: uppercase; letter-spacing: .06em;
    }
    .grupo small { font-weight: 600; color: var(--gray-400); margin-left: 4px; }
    .fila { display: flex; align-items: center; gap: 2px; border-radius: var(--radius-sm); min-height: 32px; }
    .fila:hover { background: var(--gray-50); }
    .fila.sel { background: var(--sam-azul-suave); box-shadow: inset 3px 0 0 var(--sam-azul); }
    .fila.estandar { padding-left: 18px; }
    .fila.criterio { padding-left: 54px; }
    .flecha { width: 24px; height: 24px; border: none; background: none; cursor: pointer; color: var(--gray-400); flex-shrink: 0; }
    .etiqueta {
      flex: 1; min-width: 0; display: flex; gap: 6px; align-items: baseline; border: none; background: none; cursor: pointer;
      text-align: left; padding: 5px 4px; font-size: 13px; color: var(--gray-700);
    }
    .macro .etiqueta { font-weight: 700; color: var(--gray-800); }
    .codigo { color: var(--sam-azul); font-weight: 700; white-space: nowrap; }
    .texto { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .conteo { font-size: 11px; color: var(--gray-400); padding: 0 8px; white-space: nowrap; }
    .aviso { color: #d97706; font-size: 12px; padding: 0 8px; }
    .agregar {
      display: flex; align-items: center; gap: 4px; border: none; background: none; cursor: pointer; color: var(--sam-azul);
      font-size: 12px; font-weight: 600; padding: 4px 6px; border-radius: var(--radius-sm);
    }
    .agregar:hover { background: var(--sam-azul-suave); }
    .agregar.nivel2 { margin-left: 44px; margin-bottom: 4px; }
    .agregar.nivel3 { margin-left: 78px; }
    .vacio { padding: 16px; color: var(--gray-400); font-size: 13px; text-align: center; }
    .detalle { min-width: 0; }
    .bienvenida { text-align: center; padding: 48px 32px; color: var(--gray-500); }
    .bienvenida i { font-size: 44px; color: var(--sam-azul); }
    .bienvenida h3 { color: var(--sam-azul); margin: 10px 0 8px; }
    .bienvenida p { max-width: 560px; margin: 0 auto; line-height: 1.6; font-size: 13.5px; }
    @media (max-width: 1023px) {
      .estructura { grid-template-columns: 1fr; }
      .arbol { position: static; max-height: 420px; }
    }
  `],
})
export class EstructuraComponent {
  readonly store = inject(CatalogoStore);
  readonly sel = input<string | undefined>();

  readonly categorias = CATEGORIAS_IPRESS;
  readonly tipos = (Object.keys(ETIQUETA_TIPO_CRITERIO) as CriterionType[]).map((t) => ({ value: t, label: ETIQUETA_TIPO_CRITERIO[t] }));

  readonly texto = signal('');
  readonly categoria = signal<FacilityCategory | null>(null);
  readonly tipo = signal<CriterionType | null>(null);
  private readonly abiertos = signal(new Set<string>());

  readonly seleccion = computed<Seleccion>(() => {
    const s = this.sel() ?? '';
    const id = Number(s.split('-')[1]) || null;
    if (s === 'nm') return { tipo: 'nuevo-macro', id: null };
    if (s.startsWith('ne-')) return { tipo: 'nuevo-estandar', id };
    if (s.startsWith('nc-')) return { tipo: 'nuevo-criterio', id };
    if (s.startsWith('m-')) return { tipo: 'macro', id };
    if (s.startsWith('e-')) return { tipo: 'estandar', id };
    if (s.startsWith('c-')) return { tipo: 'criterio', id };
    return { tipo: null, id: null };
  });

  readonly totalCriterios = computed(() =>
    this.store.arbol().reduce((n, m) => n + m.standards.reduce((k, e) => k + e.criteria.length, 0), 0));

  /** Árbol filtrado; con búsqueda o filtros activos, se muestra desplegado. */
  readonly filtrado = computed<NodoMacroCat[]>(() => {
    const texto = this.texto().trim().toLowerCase();
    const categoria = this.categoria();
    const tipo = this.tipo();
    if (!texto && !categoria && !tipo) return this.store.arbol();
    const coincide = (c: ResumenCriterio) =>
      (!categoria || c.applicableCategories.includes(categoria)) && (!tipo || c.type === tipo)
      && (!texto || c.code.toLowerCase().includes(texto) || c.statement.toLowerCase().includes(texto));
    return this.store.arbol()
      .map((m) => {
        const macroCoincide = !!texto && !categoria && !tipo && (m.code.toLowerCase().includes(texto) || m.name.toLowerCase().includes(texto));
        return {
          ...m,
          standards: m.standards
            .map((e) => {
              const estCoincide = !!texto && !categoria && !tipo && e.code.toLowerCase().includes(texto);
              return { ...e, criteria: macroCoincide || estCoincide ? e.criteria : e.criteria.filter(coincide) };
            })
            .filter((e) => macroCoincide || e.criteria.length || (!!texto && !categoria && !tipo && e.code.toLowerCase().includes(texto))),
        };
      })
      .filter((m) => m.standards.length);
  });

  readonly grupos = computed(() => {
    const orden: MacroProcessCategory[] = ['MANAGEMENT', 'CARE_PROVISION', 'SUPPORT'];
    return orden
      .map((categoria) => {
        const macros = this.filtrado().filter((m) => m.category === categoria);
        const peso = Math.round(this.store.arbol().filter((m) => m.category === categoria)
          .reduce((s, m) => s + Number(m.scoreWeight), 0) * 100) / 100;
        return { categoria, etiqueta: ETIQUETA_CATEGORIA_MACRO[categoria], macros, peso };
      })
      .filter((g) => g.macros.length);
  });

  private readonly filtrando = computed(() => !!this.texto().trim() || !!this.categoria() || !!this.tipo());

  constructor() {
    // Al elegir algo (también por URL), se despliegan sus ancestros en el árbol.
    effect(() => {
      const s = this.seleccion();
      this.store.arbol();
      untracked(() => {
        if (s.tipo === 'criterio' && s.id) {
          const u = this.store.ubicarCriterio(s.id);
          if (u) this.abrir('m', u.macroId, 'e', u.estandarId);
        } else if ((s.tipo === 'estandar' || s.tipo === 'nuevo-criterio') && s.id) {
          const m = this.store.ubicarEstandar(s.id);
          if (m) this.abrir('m', m.id, 'e', s.id);
        } else if ((s.tipo === 'macro' || s.tipo === 'nuevo-estandar') && s.id) {
          this.abrir('m', s.id);
        }
      });
    });
  }

  abierto(nivel: 'm' | 'e', id: number): boolean {
    return this.filtrando() || this.abiertos().has(nivel + id);
  }

  alternar(nivel: 'm' | 'e', id: number): void {
    const s = new Set(this.abiertos());
    if (s.has(nivel + id)) s.delete(nivel + id);
    else s.add(nivel + id);
    this.abiertos.set(s);
  }

  esSel(tipo: 'macro' | 'estandar' | 'criterio', id: number): boolean {
    const s = this.seleccion();
    return s.tipo === tipo && s.id === id;
  }

  private abrir(...pares: (string | number)[]): void {
    const s = new Set(this.abiertos());
    for (let i = 0; i < pares.length; i += 2) s.add(`${pares[i]}${pares[i + 1]}`);
    this.abiertos.set(s);
  }
}
