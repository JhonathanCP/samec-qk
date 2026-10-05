import { Component, DoCheck, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CATEGORIAS_IPRESS, DISPONIBILIDAD_FUENTE, ETIQUETA_TECNICA, ETIQUETA_TIPO_CRITERIO,
} from '../../../core/constants/lea';
import { CriterionType, EvaluationTechnique, FacilityCategory, SourceAvailability } from '../../../core/models/autoevaluacion.model';
import { Condicion, Criterio, Fuente } from '../../../core/models/catalogo.model';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { ModalService } from '../../../core/services/modal.service';
import { mensajeDeError } from '../../../core/utils/http-error';
import { LoaderComponent } from '../../../shared/components/loader/loader.component';
import { SearchableSelectComponent, SelectOption } from '../../../shared/components/searchable-select/searchable-select.component';
import { CatalogoStore } from '../catalogo.store';

/**
 * Un criterio completo en una sola vista (en samec-old eran 3 pantallas): datos generales,
 * categorías, puntajes 0/1/2 y sus condiciones con proceso institucional, fuentes auditables
 * (lógica Y/O) o guía de verificación en campo, y normativa. Valida mientras se edita.
 */
@Component({
  selector: 'app-criterio-editor',
  imports: [FormsModule, LoaderComponent, SearchableSelectComponent],
  template: `
    @if (cargando()) {
      <app-loader label="Cargando el criterio..." />
    } @else if (error()) {
      <div class="alert-error">{{ error() }}</div>
    } @else {
      <div class="panel-blanco editor">
        <header class="cab">
          <span class="tipo">Criterio
            @if (ubicacion(); as u) {
              · <button class="enlace" (click)="store.irA('m-' + u.macroId)">{{ u.macro }}</button>
              › <button class="enlace" (click)="store.irA('e-' + u.estandarId)">{{ u.estandar }}</button>
            }
          </span>
          <h2>{{ form.code || 'Nuevo criterio' }}</h2>
        </header>

        <!-- Datos generales -->
        <section class="bloque">
          <h3>Datos generales</h3>
          <div class="campos">
            <label class="campo corto">Código <input class="input" [(ngModel)]="form.code" maxlength="20" placeholder="DIR1-1" /></label>
            <div class="campo largo">Tipo de criterio
              <div class="segmentos" role="radiogroup" aria-label="Tipo de criterio">
                @for (t of tipos; track t.value) {
                  <button type="button" [class.sel]="form.type === t.value" (click)="form.type = t.value" [attr.aria-pressed]="form.type === t.value">{{ t.label }}</button>
                }
              </div>
            </div>
            <div class="campo">Estándar
              <app-select [options]="store.opcionesEstandar()" [(ngModel)]="form.standardId" />
            </div>
            <label class="campo completo">Enunciado del criterio
              <textarea class="input" rows="2" [(ngModel)]="form.statement" placeholder="El establecimiento ha formulado…"></textarea>
            </label>
            <div class="campo completo">Categorías de IPRESS a las que aplica
              <div class="chips-cat">
                @for (c of categorias; track c.value) {
                  <button type="button" class="cat" [class.sel]="tieneCategoria(c.value)" (click)="alternarCategoria(c.value)"
                          [attr.aria-pressed]="tieneCategoria(c.value)">{{ c.label }}</button>
                }
                <button type="button" class="enlace mini" (click)="todasCategorias()">Todas</button>
                <button type="button" class="enlace mini" (click)="form.applicableCategories = []">Ninguna</button>
              </div>
            </div>
          </div>
        </section>

        <!-- Puntajes -->
        <section class="bloque">
          <h3>Qué significa cada puntaje</h3>
          <div class="niveles">
            @for (n of form.scoreLevels; track n.score) {
              <label class="nivel"><span class="p p{{ n.score }}">{{ n.score }}</span>
                <textarea class="input" rows="3" [(ngModel)]="n.description" [placeholder]="ayudaNivel[n.score]"></textarea>
              </label>
            }
          </div>
        </section>

        <!-- Condiciones -->
        <section class="bloque">
          <div class="bloque-cab">
            <h3>Condiciones <small>{{ form.conditions.length }}</small></h3>
            <span class="pesos" [class.mal]="sumaPesos() !== 2">
              <i [class]="sumaPesos() === 2 ? 'bi bi-check-circle-fill' : 'bi bi-exclamation-triangle-fill'"></i>
              Pesos: {{ textoPesos() }} = {{ sumaPesos() }} {{ sumaPesos() === 2 ? '' : '(deben sumar 2)' }}
            </span>
            <button class="btn btn-turquesa btn-sm" (click)="agregarCondicion()"><i class="bi bi-plus-lg"></i> Agregar condición</button>
          </div>

          @for (c of form.conditions; track c; let i = $index; let primero = $first; let ultimo = $last) {
            <article class="condicion" [class.abierta]="abierta(c)">
              <header (click)="alternar(c)">
                <span class="num">{{ i + 1 }}</span>
                <span class="cond-titulo">{{ c.description || '(sin descripción)' }}</span>
                <span class="chip">{{ tecnica(c.technique) }}</span>
                <span class="chip">Peso {{ c.scoreWeight }}</span>
                @if (c.technique === 'DOCUMENT_REVIEW') { <span class="chip">{{ c.sources.length }} fuente(s)</span> }
                <span class="acciones" (click)="$event.stopPropagation()">
                  <button class="ico" (click)="mover(form.conditions, i, -1)" [disabled]="primero" title="Subir"><i class="bi bi-arrow-up"></i></button>
                  <button class="ico" (click)="mover(form.conditions, i, 1)" [disabled]="ultimo" title="Bajar"><i class="bi bi-arrow-down"></i></button>
                  <button class="ico peligro" (click)="quitarCondicion(i)" title="Quitar condición"><i class="bi bi-trash"></i></button>
                </span>
                <i class="bi" [class.bi-chevron-down]="!abierta(c)" [class.bi-chevron-up]="abierta(c)"></i>
              </header>

              @if (abierta(c)) {
                <div class="cond-cuerpo">
                  <div class="campos">
                    <label class="campo completo">Descripción de la condición
                      <input class="input" [(ngModel)]="c.description" placeholder="Formulación del PEI" />
                    </label>
                    <div class="campo corto">Peso
                      <div class="segmentos" role="radiogroup" aria-label="Peso">
                        @for (p of [0, 1, 2]; track p) {
                          <button type="button" [class.sel]="c.scoreWeight === p" (click)="c.scoreWeight = p">{{ p }}</button>
                        }
                      </div>
                    </div>
                    <label class="campo">Técnica de evaluación
                      <select class="input" [ngModel]="c.technique" (ngModelChange)="cambiarTecnica(c, $event)">
                        @for (t of tecnicas; track t.value) { <option [ngValue]="t.value">{{ t.label }}</option> }
                      </select>
                    </label>
                    <label class="campo largo">Responsable en la IPRESS
                      <input class="input" [(ngModel)]="c.responsibleRole" maxlength="500" placeholder="Dirección, Jefe de calidad…" />
                    </label>
                    <label class="campo completo">Función de la IPRESS
                      <textarea class="input" rows="2" [(ngModel)]="c.facilityFunction" maxlength="1000"
                                placeholder="Qué hace la IPRESS para cumplir la condición"></textarea>
                    </label>
                    <div class="campo completo">Proceso institucional
                      <div class="fila-select">
                        <app-select [options]="opcionesProceso()" [ngModel]="c.institutionalProcessId ?? ''"
                                    (ngModelChange)="c.institutionalProcessId = $event === '' ? null : +$event" placeholder="— Sin proceso —" />
                        <button type="button" class="enlace mini" (click)="abrirNuevoProceso(c)"><i class="bi bi-plus"></i> Nuevo proceso</button>
                      </div>
                      @if (procesoNuevoPara === c) {
                        <div class="mini-form">
                          <input class="input" [(ngModel)]="procesoNombre" placeholder="Nombre del proceso o subproceso" />
                          <select class="input" [(ngModel)]="procesoPadre">
                            <option [ngValue]="null">Es un proceso principal</option>
                            @for (p of procesosRaiz(); track p.id) { <option [ngValue]="p.id">Subproceso de {{ p.name }}</option> }
                          </select>
                          <button class="btn btn-secondary btn-sm" (click)="procesoNuevoPara = null">Cancelar</button>
                          <button class="btn btn-azul btn-sm" [disabled]="!procesoNombre.trim() || store.ocupado()" (click)="crearProceso(c)">Crear y asignar</button>
                        </div>
                      }
                    </div>
                  </div>

                  @if (c.technique === 'DOCUMENT_REVIEW') {
                    <div class="sub">
                      <div class="sub-cab">
                        <h4>Fuentes auditables</h4>
                        @if (c.sources.length > 1) {
                          <div class="segmentos chico" role="radiogroup" aria-label="Lógica de las fuentes">
                            <button type="button" [class.sel]="(c.sourceLogic ?? 'ALL') === 'ALL'" (click)="c.sourceLogic = 'ALL'" title="Se exigen todas las fuentes">Y · todas</button>
                            <button type="button" [class.sel]="c.sourceLogic === 'ANY'" (click)="c.sourceLogic = 'ANY'" title="Basta con una de las fuentes">O · alguna</button>
                          </div>
                        }
                        <button class="enlace mini" (click)="agregarFuente(c)"><i class="bi bi-plus"></i> Agregar fuente</button>
                      </div>
                      @if (c.sources.length) {
                        <div class="tabla-fuentes">
                          <div class="tf-cab"><span>Documento o registro</span><span>Disponibilidad</span><span>Enlace</span><span class="c">Todo EsSalud</span><span></span></div>
                          @for (f of c.sources; track f; let j = $index; let fp = $first; let fu = $last) {
                            <div class="tf-fila">
                              <input class="input" [(ngModel)]="f.name" placeholder="Plan anual aprobado" [class.invalido]="!f.name.trim()" />
                              <select class="input" [(ngModel)]="f.availability" [title]="disponibilidad(f.availability).ayuda">
                                @for (d of disponibilidades; track d.value) { <option [ngValue]="d.value">{{ d.label }}</option> }
                              </select>
                              <input class="input" [(ngModel)]="f.referenceUrl" placeholder="https://…"
                                     [class.invalido]="f.availability === 'PREDEFINED' && !f.referenceUrl?.trim()" />
                              <input type="checkbox" class="c" [(ngModel)]="f.appliesSystemWide" title="Aplica a todo EsSalud" />
                              <span class="acciones">
                                <button class="ico" (click)="mover(c.sources, j, -1)" [disabled]="fp" title="Subir"><i class="bi bi-arrow-up"></i></button>
                                <button class="ico" (click)="mover(c.sources, j, 1)" [disabled]="fu" title="Bajar"><i class="bi bi-arrow-down"></i></button>
                                <button class="ico peligro" (click)="c.sources.splice(j, 1)" title="Quitar fuente"><i class="bi bi-x-lg"></i></button>
                              </span>
                            </div>
                          }
                        </div>
                      } @else {
                        <p class="vacio">Agregue al menos una fuente: es lo que la IPRESS sustenta.</p>
                      }
                    </div>
                  } @else {
                    <div class="sub">
                      <h4>Qué verificar en campo</h4>
                      <textarea class="input" rows="3" [(ngModel)]="c.verificationGuide"
                                placeholder="Un punto por línea: Higiene de manos, EPP, …"></textarea>
                    </div>
                  }

                  <div class="sub">
                    <div class="sub-cab">
                      <h4>Normativa</h4>
                      <button class="enlace mini" (click)="c.normativeReferences.push({ documentId: null, applicableExcerpt: null })"><i class="bi bi-plus"></i> Citar norma</button>
                      <button class="enlace mini" (click)="abrirNuevaNorma(c)"><i class="bi bi-journal-plus"></i> Registrar norma nueva</button>
                    </div>
                    @if (normaNuevaPara === c) {
                      <div class="mini-form">
                        <input class="input" [(ngModel)]="normaTitulo" placeholder="Directiva N° …-GG-ESSALUD-…, título" />
                        <input class="input" [(ngModel)]="normaUrl" placeholder="Enlace (opcional)" />
                        <button class="btn btn-secondary btn-sm" (click)="normaNuevaPara = null">Cancelar</button>
                        <button class="btn btn-azul btn-sm" [disabled]="!normaTitulo.trim() || store.ocupado()" (click)="crearNorma(c)">Registrar y citar</button>
                      </div>
                    }
                    @for (r of c.normativeReferences; track r; let k = $index) {
                      <div class="norma">
                        <app-select [options]="store.opcionesNorma()" [(ngModel)]="r.documentId" placeholder="Elija la norma…" />
                        <textarea class="input" rows="1" [(ngModel)]="r.applicableExcerpt" placeholder="Aplicación en EsSalud (extracto)"></textarea>
                        <button class="ico peligro" (click)="c.normativeReferences.splice(k, 1)" title="Quitar"><i class="bi bi-x-lg"></i></button>
                      </div>
                    }
                  </div>
                </div>
              }
            </article>
          } @empty {
            <p class="vacio">Agregue la primera condición.</p>
          }
        </section>
      </div>

      <div class="barra-guardar" [class.con-errores]="errores.length">
        @if (errores.length) {
          <details class="lista-errores">
            <summary><i class="bi bi-exclamation-triangle-fill"></i> {{ errores.length }} punto(s) por corregir</summary>
            <ul>@for (e of errores; track e) { <li>{{ e }}</li> }</ul>
          </details>
        } @else if (sucio) {
          <span class="ok"><i class="bi bi-pencil"></i> Cambios sin guardar</span>
        } @else {
          <span class="ok"><i class="bi bi-check-circle"></i> Todo guardado</span>
        }
        <span class="espacio"></span>
        @if (form.id) {
          <button class="btn btn-quitar" (click)="eliminar()" [disabled]="store.ocupado()"><i class="bi bi-trash"></i> Eliminar criterio</button>
        }
        @if (sucio && form.id) { <button class="btn btn-secondary" (click)="descartar()">Descartar cambios</button> }
        <button class="btn btn-azul" (click)="guardar()" [disabled]="store.ocupado() || errores.length > 0 || !sucio">
          <i class="bi bi-check-lg"></i> {{ form.id ? 'Guardar criterio' : 'Crear criterio' }}
        </button>
      </div>
    }
  `,
  styleUrls: ['./editor.css', './criterio-editor.css'],
})
export class CriterioEditorComponent implements DoCheck {
  readonly store = inject(CatalogoStore);
  private readonly api = inject(CatalogoService);
  private readonly modal = inject(ModalService);

  readonly criterioId = input<number | null>(null);
  /** Estándar donde se crea (criterio nuevo). */
  readonly estandarIdNuevo = input<number | null>(null);

  readonly categorias = CATEGORIAS_IPRESS;
  readonly tipos = (Object.keys(ETIQUETA_TIPO_CRITERIO) as CriterionType[]).map((t) => ({ value: t, label: ETIQUETA_TIPO_CRITERIO[t] }));
  readonly tecnicas = (Object.keys(ETIQUETA_TECNICA) as EvaluationTechnique[]).map((t) => ({ value: t, label: ETIQUETA_TECNICA[t] }));
  readonly disponibilidades = (Object.keys(DISPONIBILIDAD_FUENTE) as SourceAvailability[])
    .map((d) => ({ value: d, label: DISPONIBILIDAD_FUENTE[d].label }));
  readonly ayudaNivel = ['0: no cumple…', '1: cumple parcialmente…', '2: cumple con todo lo establecido…'];

  readonly cargando = signal(false);
  readonly error = signal('');
  form: Criterio = this.nuevo(null);
  private original = '';
  errores: string[] = [];
  sucio = false;
  private readonly abiertas = new Set<Condicion>();

  procesoNuevoPara: Condicion | null = null;
  procesoNombre = '';
  procesoPadre: number | null = null;
  normaNuevaPara: Condicion | null = null;
  normaTitulo = '';
  normaUrl = '';

  readonly opcionesProceso = computed<SelectOption[]>(() => [{ value: '', label: '— Sin proceso —' }, ...this.store.opcionesProceso()]);
  readonly procesosRaiz = computed(() => this.store.procesos().filter((p) => p.parentId === null)
    .sort((a, b) => a.name.localeCompare(b.name)));

  tecnica = (t: EvaluationTechnique) => ETIQUETA_TECNICA[t];
  disponibilidad = (d: SourceAvailability) => DISPONIBILIDAD_FUENTE[d];

  constructor() {
    effect(() => {
      const id = this.criterioId();
      const estandar = this.estandarIdNuevo();
      untracked(() => this.cargar(id, estandar));
    });
  }

  ubicacion(): { macroId: number; macro: string; estandarId: number; estandar: string } | null {
    const m = this.store.ubicarEstandar(Number(this.form.standardId));
    const e = m?.standards.find((x) => x.id === Number(this.form.standardId));
    return m && e ? { macroId: m.id, macro: `${m.code} ${m.name}`, estandarId: e.id, estandar: e.code } : null;
  }

  // ── Detección de cambios y validación (en vivo) ──

  ngDoCheck(): void {
    if (this.cargando()) return;
    this.sucio = JSON.stringify(this.form) !== this.original;
    if (this.store.sucio() !== this.sucio) queueMicrotask(() => this.store.sucio.set(this.sucio));
    this.errores = this.validar();
  }

  sumaPesos(): number {
    return this.form.conditions.reduce((s, c) => s + Number(c.scoreWeight), 0);
  }

  textoPesos(): string {
    return this.form.conditions.map((c) => c.scoreWeight).join(' + ') || '0';
  }

  private validar(): string[] {
    const f = this.form;
    const e: string[] = [];
    if (!f.code?.trim()) e.push('Indique el código del criterio.');
    if (!f.standardId) e.push('Elija el estándar.');
    if (!f.statement?.trim()) e.push('Escriba el enunciado del criterio.');
    if (!f.applicableCategories.length) e.push('Marque al menos una categoría de IPRESS.');
    if (!f.conditions.length) e.push('Agregue al menos una condición.');
    else if (this.sumaPesos() !== 2) e.push(`Los pesos de las condiciones suman ${this.sumaPesos()}; deben sumar 2.`);
    f.conditions.forEach((c, i) => {
      const n = `Condición ${i + 1}`;
      if (!c.description?.trim()) e.push(`${n}: escriba la descripción.`);
      if (c.technique === 'DOCUMENT_REVIEW') {
        if (!c.sources.length) e.push(`${n}: agregue al menos una fuente auditable.`);
        c.sources.forEach((s, j) => {
          if (!s.name?.trim()) e.push(`${n}, fuente ${j + 1}: escriba el nombre.`);
          if (s.availability === 'PREDEFINED' && !s.referenceUrl?.trim()) e.push(`${n}, fuente ${j + 1}: una fuente institucional necesita su enlace.`);
        });
      }
      const docs = c.normativeReferences.map((r) => r.documentId);
      if (docs.some((d) => d === null)) e.push(`${n}: elija la norma de cada cita (o quítela).`);
      if (new Set(docs).size !== docs.length) e.push(`${n}: la misma norma está citada dos veces.`);
    });
    return e;
  }

  // ── Edición ──

  tieneCategoria(c: FacilityCategory): boolean {
    return this.form.applicableCategories.includes(c);
  }

  alternarCategoria(c: FacilityCategory): void {
    const lista = this.form.applicableCategories;
    this.form.applicableCategories = lista.includes(c) ? lista.filter((x) => x !== c)
      : this.categorias.map((x) => x.value).filter((x) => x === c || lista.includes(x));
  }

  todasCategorias(): void {
    this.form.applicableCategories = this.categorias.map((c) => c.value);
  }

  abierta(c: Condicion): boolean {
    return this.abiertas.has(c);
  }

  alternar(c: Condicion): void {
    if (this.abiertas.has(c)) this.abiertas.delete(c);
    else this.abiertas.add(c);
  }

  agregarCondicion(): void {
    const c: Condicion = {
      id: null, description: '', scoreWeight: this.form.conditions.length ? 1 : 2, technique: 'DOCUMENT_REVIEW',
      institutionalProcessId: null, facilityFunction: null, responsibleRole: null, sourceLogic: null, verificationGuide: null,
      normativeReferences: [], sources: [this.fuenteNueva()], displayOrder: this.form.conditions.length + 1,
    };
    this.form.conditions.push(c);
    this.abiertas.add(c);
  }

  async quitarCondicion(i: number): Promise<void> {
    const c = this.form.conditions[i];
    if (c.id && !(await this.modal.confirm(`¿Quitar la condición ${i + 1}? Se aplicará al guardar.`, 'Quitar condición', 'Quitar'))) return;
    this.form.conditions.splice(i, 1);
  }

  async cambiarTecnica(c: Condicion, nueva: EvaluationTechnique): Promise<void> {
    if (nueva !== 'DOCUMENT_REVIEW' && c.sources.length) {
      const ok = await this.modal.confirm('Las condiciones que se verifican en campo no tienen fuentes auditables: se quitarán sus '
        + `${c.sources.length} fuente(s). ¿Continuar?`, 'Cambiar técnica', 'Cambiar');
      if (!ok) {
        c.technique = 'DOCUMENT_REVIEW';
        return;
      }
      c.sources = [];
      c.sourceLogic = null;
    }
    if (nueva === 'DOCUMENT_REVIEW' && !c.sources.length) c.sources = [this.fuenteNueva()];
    c.technique = nueva;
  }

  agregarFuente(c: Condicion): void {
    c.sources.push(this.fuenteNueva());
    if (c.sources.length > 1 && !c.sourceLogic) c.sourceLogic = 'ALL';
  }

  mover<T>(lista: T[], i: number, paso: number): void {
    const j = i + paso;
    if (j < 0 || j >= lista.length) return;
    [lista[i], lista[j]] = [lista[j], lista[i]];
  }

  abrirNuevoProceso(c: Condicion): void {
    this.procesoNuevoPara = c;
    this.procesoNombre = '';
    this.procesoPadre = null;
  }

  async crearProceso(c: Condicion): Promise<void> {
    const r = await this.store.ejecutar(this.api.guardarProceso({ id: null, parentId: this.procesoPadre, name: this.procesoNombre.trim() }),
      'Proceso registrado.');
    if (!r) return;
    this.store.procesos.update((l) => [...l, r]);
    c.institutionalProcessId = r.id;
    this.procesoNuevoPara = null;
  }

  abrirNuevaNorma(c: Condicion): void {
    this.normaNuevaPara = c;
    this.normaTitulo = '';
    this.normaUrl = '';
  }

  async crearNorma(c: Condicion): Promise<void> {
    const r = await this.store.ejecutar(this.api.guardarNorma({ id: null, title: this.normaTitulo.trim(), url: this.normaUrl.trim() || null }),
      'Norma registrada.');
    if (!r) return;
    this.store.normas.update((l) => [...l, r]);
    c.normativeReferences.push({ documentId: r.id, applicableExcerpt: null });
    this.normaNuevaPara = null;
  }

  // ── Guardar ──

  descartar(): void {
    this.form = JSON.parse(this.original);
    this.abiertas.clear();
  }

  async guardar(): Promise<void> {
    const r = await this.store.ejecutar(this.api.guardarCriterio(this.paraEnviar()),
      this.form.id ? `${this.form.code}: criterio guardado.` : `${this.form.code}: criterio creado.`);
    if (!r) return;
    const eraNuevo = !this.form.id;
    this.aplicar(r);
    this.store.sucio.set(false);
    this.store.recargarArbol();
    if (eraNuevo) this.store.irA('c-' + r.id, 'estructura', false);
  }

  async eliminar(): Promise<void> {
    if (!(await this.modal.confirm(`¿Eliminar el criterio ${this.form.code} con todas sus condiciones?`, 'Eliminar criterio', 'Eliminar'))) return;
    if (await this.store.ejecutar(this.api.eliminarCriterio(this.form.id!), 'Criterio eliminado.')) {
      const estandar = this.form.standardId;
      this.store.recargarArbol();
      this.store.irA('e-' + estandar, 'estructura', false);
    }
  }

  private paraEnviar(): Criterio {
    const t = (v: string | null | undefined) => (v ?? '').trim() || null;
    return {
      ...this.form,
      standardId: Number(this.form.standardId),
      code: this.form.code.trim(),
      statement: this.form.statement.trim(),
      scoreLevels: this.form.scoreLevels.filter((n) => n.description?.trim()).map((n) => ({ score: n.score, description: n.description.trim() })),
      conditions: this.form.conditions.map((c, i) => {
        const documental = c.technique === 'DOCUMENT_REVIEW';
        const fuentes: Fuente[] = documental ? c.sources.map((s, j) => ({
          ...s, name: s.name.trim(), referenceUrl: t(s.referenceUrl), displayOrder: j + 1,
        })) : [];
        return {
          ...c,
          description: c.description.trim(),
          facilityFunction: t(c.facilityFunction),
          responsibleRole: t(c.responsibleRole),
          verificationGuide: documental ? null : t(c.verificationGuide),
          sourceLogic: fuentes.length > 1 ? c.sourceLogic ?? 'ALL' : null,
          sources: fuentes,
          normativeReferences: c.normativeReferences.map((r) => ({ documentId: r.documentId, applicableExcerpt: t(r.applicableExcerpt) })),
          displayOrder: i + 1,
        };
      }),
    };
  }

  // ── Carga ──

  private cargar(id: number | null, estandarNuevo: number | null): void {
    this.error.set('');
    this.abiertas.clear();
    if (!id) {
      this.form = this.nuevo(estandarNuevo);
      this.original = JSON.stringify(this.form);
      this.form.conditions.forEach((c) => this.abiertas.add(c));
      return;
    }
    this.cargando.set(true);
    this.api.criterio(id).subscribe({
      next: (c) => {
        this.aplicar(c);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(mensajeDeError(err, 'No se pudo cargar el criterio.'));
        this.cargando.set(false);
      },
    });
  }

  /** Lleva el criterio del core al formulario (siempre con los 3 niveles de puntaje). */
  private aplicar(c: Criterio): void {
    this.form = {
      id: c.id, standardId: c.standardId, code: c.code, statement: c.statement, type: c.type,
      applicableCategories: this.categorias.map((x) => x.value).filter((x) => c.applicableCategories.includes(x)),
      scoreLevels: [0, 1, 2].map((s) => ({ score: s, description: c.scoreLevels.find((n) => n.score === s)?.description ?? '' })),
      conditions: [...c.conditions].sort((a, b) => a.displayOrder - b.displayOrder).map((x) => ({
        id: x.id, description: x.description, scoreWeight: x.scoreWeight, technique: x.technique,
        institutionalProcessId: x.institutionalProcessId, facilityFunction: x.facilityFunction, responsibleRole: x.responsibleRole,
        sourceLogic: x.sourceLogic, verificationGuide: x.verificationGuide,
        normativeReferences: x.normativeReferences.map((r) => ({ documentId: r.documentId, applicableExcerpt: r.applicableExcerpt })),
        sources: [...x.sources].sort((a, b) => a.displayOrder - b.displayOrder).map((s) => ({
          id: s.id, name: s.name, referenceUrl: s.referenceUrl, availability: s.availability,
          appliesSystemWide: s.appliesSystemWide, displayOrder: s.displayOrder,
        })),
        displayOrder: x.displayOrder,
      })),
    };
    this.original = JSON.stringify(this.form);
  }

  private nuevo(estandarId: number | null): Criterio {
    const est = estandarId ? this.store.arbol().flatMap((m) => m.standards).find((e) => e.id === estandarId) : null;
    const base = est ? est.code.replace('-', '') : '';
    const c: Criterio = {
      id: null, standardId: estandarId ?? 0, code: base ? `${base}-${(est?.criteria.length ?? 0) + 1}` : '', statement: '',
      type: 'STRUCTURE', applicableCategories: this.categorias.map((x) => x.value),
      scoreLevels: [0, 1, 2].map((s) => ({ score: s, description: '' })), conditions: [],
    };
    c.conditions.push({
      id: null, description: '', scoreWeight: 2, technique: 'DOCUMENT_REVIEW', institutionalProcessId: null,
      facilityFunction: null, responsibleRole: null, sourceLogic: null, verificationGuide: null,
      normativeReferences: [], sources: [this.fuenteNueva()], displayOrder: 1,
    });
    return c;
  }

  private fuenteNueva(): Fuente {
    return { id: null, name: '', referenceUrl: null, availability: 'PROVIDED_BY_FACILITY', appliesSystemWide: false, displayOrder: 1 };
  }
}
