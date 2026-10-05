import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ProcesoInstitucional, Uso } from '../../../core/models/catalogo.model';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { ModalService } from '../../../core/services/modal.service';
import { CatalogoStore } from '../catalogo.store';

/** Procesos institucionales (proceso → subproceso) y qué condiciones de qué criterios los usan. */
@Component({
  selector: 'app-procesos',
  imports: [FormsModule],
  template: `
    <div class="dos-col">
      <aside class="panel-blanco lista">
        <div class="herr">
          <input class="input" [ngModel]="texto()" (ngModelChange)="texto.set($event)" placeholder="Buscar proceso…" aria-label="Buscar proceso" />
          <button class="btn btn-azul btn-sm" (click)="nuevo(null)"><i class="bi bi-plus-lg"></i> Proceso</button>
        </div>
        <div class="items">
          @for (r of arbol(); track r.proceso.id) {
            <button class="item raiz" [class.sel]="elegido()?.id === r.proceso.id" (click)="elegir(r.proceso)">
              <i class="bi bi-diagram-2"></i> {{ r.proceso.name }} @if (r.hijos.length) { <small>{{ r.hijos.length }}</small> }
            </button>
            @for (h of r.hijos; track h.id) {
              <button class="item hijo" [class.sel]="elegido()?.id === h.id" (click)="elegir(h)">{{ h.name }}</button>
            }
          } @empty { <p class="vacio">Nada coincide.</p> }
        </div>
      </aside>

      <section class="detalle">
        @if (elegido(); as p) {
          <div class="panel-blanco editor">
            <header class="cab">
              <span class="tipo">{{ p.parentId ? 'Subproceso' : 'Proceso institucional' }}</span>
              <h2>{{ form.name || (p.id ? p.name : 'Nuevo') }}</h2>
            </header>
            <div class="campos">
              <label class="campo largo">Nombre <input class="input" [(ngModel)]="form.name" maxlength="250" /></label>
              <label class="campo largo">Ubicación
                <select class="input" [(ngModel)]="form.parentId" [disabled]="hijosDe(p.id).length > 0">
                  <option [ngValue]="null">Proceso principal</option>
                  @for (r of raices(); track r.id) { @if (r.id !== p.id) { <option [ngValue]="r.id">Subproceso de {{ r.name }}</option> } }
                </select>
              </label>
            </div>
            <div class="botones">
              @if (p.id) { <button class="btn btn-quitar" (click)="eliminar(p)" [disabled]="store.ocupado()"><i class="bi bi-trash"></i> Eliminar</button> }
              <span class="espacio"></span>
              <button class="btn btn-azul" (click)="guardar()" [disabled]="store.ocupado() || !form.name.trim() || !cambiado()">
                <i class="bi bi-check-lg"></i> {{ p.id ? 'Guardar' : 'Crear' }}
              </button>
            </div>
          </div>

          @if (p.id && !p.parentId) {
            <div class="panel-blanco hijos">
              <div class="hijos-cab">
                <h3>Subprocesos <small>{{ hijosDe(p.id).length }}</small></h3>
                <button class="btn btn-turquesa btn-sm" (click)="nuevo(p.id)"><i class="bi bi-plus-lg"></i> Agregar subproceso</button>
              </div>
              @for (h of hijosDe(p.id); track h.id) {
                <button class="hijo-fila" (click)="elegir(h)"><span>{{ h.name }}</span><i class="bi bi-chevron-right"></i></button>
              } @empty { <p class="vacio">No tiene subprocesos.</p> }
            </div>
          }

          @if (p.id) {
            <div class="panel-blanco hijos">
              <div class="hijos-cab"><h3>Dónde se usa <small>{{ usos().length }}</small></h3></div>
              @for (u of usos(); track u.conditionId) {
                <button class="hijo-fila" (click)="store.irA('c-' + u.criterionId)">
                  <span class="codigo">{{ u.criterionCode }}</span>
                  <span class="texto">{{ u.conditionDescription }}</span>
                  <span class="ir">Editar criterio <i class="bi bi-arrow-right"></i></span>
                </button>
              } @empty { <p class="vacio">Ninguna condición lo usa.</p> }
            </div>
          }
        } @else {
          <div class="panel-blanco bienvenida">
            <i class="bi bi-diagram-2"></i>
            <h3>Procesos institucionales</h3>
            <p>Elija un proceso para editarlo y ver qué condiciones de qué criterios lo usan.</p>
          </div>
        }
      </section>
    </div>
  `,
  styleUrls: ['../estructura/editor.css', './listas.css'],
})
export class ProcesosComponent {
  readonly store = inject(CatalogoStore);
  private readonly api = inject(CatalogoService);
  private readonly modal = inject(ModalService);

  readonly texto = signal('');
  readonly elegido = signal<ProcesoInstitucional | null>(null);
  readonly usos = signal<Uso[]>([]);
  form: ProcesoInstitucional = { id: null, parentId: null, name: '' };

  readonly raices = computed(() => this.store.procesos().filter((p) => p.parentId === null).sort((a, b) => a.name.localeCompare(b.name)));

  readonly arbol = computed(() => {
    const t = this.texto().trim().toLowerCase();
    return this.raices()
      .map((r) => ({ proceso: r, hijos: this.hijosDe(r.id).filter((h) => !t || h.name.toLowerCase().includes(t) || r.name.toLowerCase().includes(t)) }))
      .filter((x) => !t || x.proceso.name.toLowerCase().includes(t) || x.hijos.length);
  });

  hijosDe(id: number | null): ProcesoInstitucional[] {
    return id === null ? [] : this.store.procesos().filter((p) => p.parentId === id).sort((a, b) => a.name.localeCompare(b.name));
  }

  cambiado(): boolean {
    const p = this.elegido();
    return !p?.id || p.name !== this.form.name.trim() || p.parentId !== this.form.parentId;
  }

  elegir(p: ProcesoInstitucional): void {
    this.elegido.set(p);
    this.form = { ...p };
    this.usos.set([]);
    if (p.id) this.api.usosDeProceso(p.id).subscribe({ next: (u) => this.usos.set(u), error: () => {} });
  }

  nuevo(padre: number | null): void {
    this.elegir({ id: null, parentId: padre, name: '' });
  }

  async guardar(): Promise<void> {
    const r = await this.store.ejecutar(this.api.guardarProceso({ ...this.form, name: this.form.name.trim() }),
      this.form.id ? 'Proceso guardado.' : 'Proceso creado.');
    if (!r) return;
    this.store.procesos.update((l) => [...l.filter((x) => x.id !== r.id), r]);
    this.elegir(r);
  }

  async eliminar(p: ProcesoInstitucional): Promise<void> {
    if (!(await this.modal.confirm(`¿Eliminar "${p.name}"?`, 'Eliminar proceso', 'Eliminar'))) return;
    if (await this.store.ejecutar(this.api.eliminarProceso(p.id!), 'Proceso eliminado.')) {
      this.store.procesos.update((l) => l.filter((x) => x.id !== p.id));
      this.elegido.set(null);
    }
  }
}
