import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TipoObservacionCat } from '../../../core/models/catalogo.model';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { ModalService } from '../../../core/services/modal.service';
import { CatalogoStore } from '../catalogo.store';

interface Fila {
  original: TipoObservacionCat;
  borrador: TipoObservacionCat;
}

/** Motivos que el evaluador elige al observar un documento. Uno ya usado se desactiva en vez de eliminarse. */
@Component({
  selector: 'app-tipos-observacion',
  imports: [FormsModule],
  template: `
    <div class="panel-blanco">
      <div class="herr">
        <p class="ayuda">Motivos que el evaluador elige al observar un sustento. Los inactivos ya no se ofrecen, pero se conservan en el historial.</p>
        <button class="btn btn-azul btn-sm" (click)="nueva = { id: null, code: '', name: '', active: true }" [disabled]="!!nueva"><i class="bi bi-plus-lg"></i> Nuevo tipo</button>
      </div>
      <div class="tabla-edit">
        <div class="te-cab cols"><span>Código</span><span>Nombre</span><span class="c">Activo</span><span></span></div>
        @if (nueva) {
          <div class="te-fila cols nueva">
            <input class="input" [(ngModel)]="nueva.code" placeholder="WRONG_YEAR" maxlength="40" />
            <input class="input" [(ngModel)]="nueva.name" placeholder="El documento no corresponde al año" maxlength="250" />
            <input type="checkbox" class="c" [(ngModel)]="nueva.active" />
            <span class="te-acc">
              <button class="btn btn-secondary btn-sm" (click)="nueva = null">Cancelar</button>
              <button class="btn btn-azul btn-sm" [disabled]="!nueva.code.trim() || !nueva.name.trim() || store.ocupado()" (click)="crear()">Crear</button>
            </span>
          </div>
        }
        @for (f of filas(); track f.original.id) {
          <div class="te-fila cols">
            <input class="input" [(ngModel)]="f.borrador.code" maxlength="40" />
            <input class="input" [(ngModel)]="f.borrador.name" maxlength="250" />
            <input type="checkbox" class="c" [(ngModel)]="f.borrador.active" [attr.aria-label]="'Activo: ' + f.borrador.name" />
            <span class="te-acc">
              @if (cambio(f)) {
                <button class="btn btn-azul btn-sm" (click)="guardar(f)" [disabled]="store.ocupado() || !f.borrador.code.trim() || !f.borrador.name.trim()">Guardar</button>
              }
              <button class="btn btn-quitar btn-sm" (click)="eliminar(f)" [disabled]="store.ocupado()" title="Eliminar"><i class="bi bi-trash"></i></button>
            </span>
          </div>
        } @empty { <p class="vacio">No hay tipos de observación.</p> }
      </div>
    </div>
  `,
  styleUrls: ['../estructura/editor.css', '../procesos/listas.css'],
  styles: [`
    .herr { padding: 0 0 12px; border: none; }
    .ayuda { flex: 1; margin: 0; font-size: 13px; color: var(--gray-500); }
    .cols { grid-template-columns: minmax(0, 1fr) minmax(0, 2.4fr) 70px auto; }
    .c { justify-self: center; text-align: center; }
  `],
})
export class TiposObservacionComponent implements OnInit {
  readonly store = inject(CatalogoStore);
  private readonly api = inject(CatalogoService);
  private readonly modal = inject(ModalService);

  readonly filas = signal<Fila[]>([]);
  nueva: TipoObservacionCat | null = null;

  ngOnInit(): void {
    this.cargar();
  }

  cambio(f: Fila): boolean {
    return JSON.stringify(f.borrador) !== JSON.stringify(f.original);
  }

  async crear(): Promise<void> {
    const n = this.nueva!;
    if (await this.store.ejecutar(this.api.guardarTipoObservacion({ ...n, code: n.code.trim().toUpperCase(), name: n.name.trim() }),
      'Tipo de observación creado.')) {
      this.nueva = null;
      this.cargar();
    }
  }

  async guardar(f: Fila): Promise<void> {
    const b = f.borrador;
    if (await this.store.ejecutar(this.api.guardarTipoObservacion({ ...b, code: b.code.trim().toUpperCase(), name: b.name.trim() }),
      'Tipo de observación guardado.')) this.cargar();
  }

  async eliminar(f: Fila): Promise<void> {
    if (!(await this.modal.confirm(`¿Eliminar "${f.original.name}"?`, 'Eliminar tipo de observación', 'Eliminar'))) return;
    if (await this.store.ejecutar(this.api.eliminarTipoObservacion(f.original.id!), 'Tipo de observación eliminado.')) this.cargar();
  }

  private cargar(): void {
    this.api.tiposObservacion().subscribe({
      next: (l) => this.filas.set(l.map((t) => ({ original: t, borrador: { ...t } }))),
      error: () => {},
    });
  }
}
