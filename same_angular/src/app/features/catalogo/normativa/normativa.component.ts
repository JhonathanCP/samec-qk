import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DocumentoNormativo, Uso } from '../../../core/models/catalogo.model';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { ModalService } from '../../../core/services/modal.service';
import { CatalogoStore } from '../catalogo.store';

interface Fila {
  original: DocumentoNormativo;
  borrador: DocumentoNormativo;
  usos: Uso[] | null;
  verUsos: boolean;
}

/** Documentos normativos: edición en la misma tabla y qué criterios citan cada uno. */
@Component({
  selector: 'app-normativa',
  imports: [FormsModule],
  template: `
    <div class="panel-blanco">
      <div class="herr">
        <input class="input" [ngModel]="texto()" (ngModelChange)="texto.set($event)" placeholder="Buscar norma…" aria-label="Buscar norma" />
        <span class="filtro-contador">{{ visibles().length }} de {{ filas().length }}</span>
        <button class="btn btn-azul btn-sm" (click)="agregar()" [disabled]="!!nueva"><i class="bi bi-plus-lg"></i> Nueva norma</button>
      </div>
      <div class="tabla-edit">
        <div class="te-cab cols"><span>Título</span><span>Enlace</span><span></span></div>
        @if (nueva) {
          <div class="te-fila cols nueva">
            <input class="input" [(ngModel)]="nueva.title" placeholder="Directiva N° …, título" />
            <input class="input" [(ngModel)]="nueva.url" placeholder="https://…" />
            <span class="te-acc">
              <button class="btn btn-secondary btn-sm" (click)="nueva = null">Cancelar</button>
              <button class="btn btn-azul btn-sm" [disabled]="!nueva.title.trim() || store.ocupado()" (click)="crear()">Crear</button>
            </span>
          </div>
        }
        @for (f of visibles(); track f.original.id) {
          <div class="te-fila cols">
            <input class="input" [(ngModel)]="f.borrador.title" />
            <input class="input" [(ngModel)]="f.borrador.url" placeholder="Sin enlace" />
            <span class="te-acc">
              @if (f.borrador.url) {
                <a class="btn btn-outline btn-sm" [href]="f.borrador.url" target="_blank" rel="noopener" title="Abrir"><i class="bi bi-box-arrow-up-right"></i></a>
              }
              <button class="btn btn-outline btn-sm" (click)="alternarUsos(f)" title="Qué criterios la citan"><i class="bi bi-diagram-3"></i> Usos</button>
              @if (cambio(f)) {
                <button class="btn btn-azul btn-sm" (click)="guardar(f)" [disabled]="store.ocupado() || !f.borrador.title.trim()">Guardar</button>
              }
              <button class="btn btn-quitar btn-sm" (click)="eliminar(f)" [disabled]="store.ocupado()" title="Eliminar"><i class="bi bi-trash"></i></button>
            </span>
            @if (f.verUsos) {
              <div class="usos-lista">
                @for (u of f.usos ?? []; track u.conditionId) {
                  <button class="hijo-fila" (click)="store.irA('c-' + u.criterionId)">
                    <span class="codigo">{{ u.criterionCode }}</span><span class="texto">{{ u.conditionDescription }}</span>
                    <span class="ir">Editar criterio <i class="bi bi-arrow-right"></i></span>
                  </button>
                } @empty { <p class="vacio">{{ f.usos ? 'Ningún criterio la cita.' : 'Cargando…' }}</p> }
              </div>
            }
          </div>
        }
      </div>
    </div>
  `,
  styleUrls: ['../estructura/editor.css', '../procesos/listas.css'],
  styles: [`
    .herr { padding: 0 0 12px; border: none; }
    .cols { grid-template-columns: minmax(0, 2.4fr) minmax(0, 1.4fr) auto; }
  `],
})
export class NormativaComponent {
  readonly store = inject(CatalogoStore);
  private readonly api = inject(CatalogoService);
  private readonly modal = inject(ModalService);

  readonly texto = signal('');
  readonly filas = signal<Fila[]>([]);
  nueva: DocumentoNormativo | null = null;

  readonly visibles = computed(() => {
    const t = this.texto().trim().toLowerCase();
    return this.filas().filter((f) => !t || f.original.title.toLowerCase().includes(t));
  });

  constructor() {
    effect(() => {
      const normas = this.store.normas();
      untracked(() => this.filas.set([...normas].sort((a, b) => a.title.localeCompare(b.title))
        .map((n) => ({ original: n, borrador: { ...n }, usos: null, verUsos: false }))));
    });
  }

  cambio(f: Fila): boolean {
    return f.borrador.title.trim() !== f.original.title || (f.borrador.url ?? '') !== (f.original.url ?? '');
  }

  agregar(): void {
    this.nueva = { id: null, title: '', url: null };
  }

  async crear(): Promise<void> {
    const r = await this.store.ejecutar(this.api.guardarNorma({ id: null, title: this.nueva!.title.trim(), url: this.nueva!.url?.trim() || null }),
      'Norma registrada.');
    if (!r) return;
    this.nueva = null;
    this.store.normas.update((l) => [...l, r]);
  }

  async guardar(f: Fila): Promise<void> {
    const r = await this.store.ejecutar(this.api.guardarNorma({ ...f.borrador, title: f.borrador.title.trim(), url: f.borrador.url?.trim() || null }),
      'Norma guardada.');
    if (r) this.store.normas.update((l) => l.map((n) => (n.id === r.id ? r : n)));
  }

  alternarUsos(f: Fila): void {
    f.verUsos = !f.verUsos;
    if (f.verUsos && !f.usos) {
      this.api.usosDeNorma(f.original.id!).subscribe({ next: (u) => (f.usos = u), error: () => (f.usos = []) });
    }
  }

  async eliminar(f: Fila): Promise<void> {
    if (!(await this.modal.confirm(`¿Eliminar "${f.original.title}"?`, 'Eliminar norma', 'Eliminar'))) return;
    if (await this.store.ejecutar(this.api.eliminarNorma(f.original.id!), 'Norma eliminada.')) {
      this.store.normas.update((l) => l.filter((n) => n.id !== f.original.id));
    }
  }
}
