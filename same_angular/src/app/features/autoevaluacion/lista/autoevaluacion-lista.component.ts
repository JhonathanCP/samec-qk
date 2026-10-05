import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  CATEGORIAS_IPRESS, COLOR_CLASIFICACION, ETIQUETA_CATEGORIA, ETIQUETA_ESTADO_AUTOEVALUACION,
} from '../../../core/constants/lea';
import { AssessmentStatus, Autoevaluacion, FacilityCategory } from '../../../core/models/autoevaluacion.model';
import { Ipress } from '../../../core/models/organizacion.model';
import { AutoevaluacionService } from '../../../core/services/autoevaluacion.service';
import { OrganizacionService } from '../../../core/services/organizacion.service';
import { ToastService } from '../../../core/services/toast.service';
import { AuthStore } from '../../../core/stores/auth.store';
import { mensajeDeError } from '../../../core/utils/http-error';
import { LoaderComponent } from '../../../shared/components/loader/loader.component';
import { SearchableSelectComponent, SelectOption } from '../../../shared/components/searchable-select/searchable-select.component';

/**
 * Autoevaluaciones dentro del alcance del usuario (el evaluador y el evaluado ven las de su
 * IPRESS; el administrador, todas). El evaluador y el administrador inician la del año.
 */
@Component({
  selector: 'app-autoevaluacion-lista',
  imports: [FormsModule, RouterLink, DecimalPipe, LoaderComponent, SearchableSelectComponent],
  template: `
    <div class="migas">
      <span class="actual">Autoevaluaciones</span>
      <span class="extra">
        @if (auth.can('autoevaluacion.gestionar') && !formulario()) {
          <button class="btn btn-azul btn-sm" (click)="abrirFormulario()"><i class="bi bi-plus-lg"></i> Iniciar autoevaluación</button>
        }
      </span>
    </div>

    @if (formulario()) {
      <div class="card nueva">
        <h3 class="section-title"><i class="bi bi-clipboard-plus"></i> Nueva autoevaluación</h3>
        <p class="ayuda">
          Se abre con los criterios de acreditación que aplican a la categoría de la IPRESS. Los criterios cuyas fuentes
          auditables son todas institucionales quedan calificados con 2 automáticamente.
        </p>
        <div class="form-grid">
          <div class="field-group full">
            <label class="field-label">IPRESS</label>
            @if (miIpress(); as ipress) {
              <input class="input" [value]="ipress" disabled />
            } @else {
              <app-select [options]="opcionesIpress()" [(ngModel)]="nuevaIpress" placeholder="-- Seleccione la IPRESS --" />
            }
          </div>
          <div class="field-group">
            <label class="field-label" for="anio">Año</label>
            <input id="anio" class="input" type="number" [(ngModel)]="nuevoAnio" min="2000" [max]="anioActual + 1" />
          </div>
          <div class="field-group">
            <label class="field-label" for="categoria">Categoría de la IPRESS</label>
            <select id="categoria" class="input" [(ngModel)]="nuevaCategoria">
              <option [ngValue]="null" disabled>Seleccione…</option>
              @for (c of categorias; track c.value) { <option [ngValue]="c.value">{{ c.label }}</option> }
            </select>
            @if (nivelSugerido()) { <span class="field-hint">Nivel registrado de la IPRESS: {{ nivelSugerido() }}</span> }
          </div>
        </div>
        <div class="form-actions">
          <button class="btn btn-secondary" (click)="formulario.set(false)">Cancelar</button>
          <button class="btn btn-azul" (click)="iniciar()" [disabled]="iniciando() || !ipressElegida() || !nuevaCategoria || !nuevoAnio">
            <i class="bi bi-play-fill"></i> Iniciar
          </button>
        </div>
      </div>
    }

    <div class="filtros-bar">
      @if (auth.alcance() === 'NACIONAL') {
        <div class="filtro-campo ipress">
          <label>IPRESS</label>
          <app-select [options]="opcionesIpressFiltro()" [ngModel]="filtroIpress()" (ngModelChange)="filtrarIpress($event)" placeholder="Todas" />
        </div>
      }
      <div class="filtro-campo">
        <label for="f-anio">Año</label>
        <input id="f-anio" class="input" type="number" [ngModel]="filtroAnio()" (ngModelChange)="filtroAnio.set($event); buscar()" placeholder="Todos" />
      </div>
      <div class="filtro-campo">
        <label for="f-estado">Estado</label>
        <select id="f-estado" class="input" [ngModel]="filtroEstado()" (ngModelChange)="filtroEstado.set($event); buscar()">
          <option [ngValue]="null">Todos</option>
          <option value="OPEN">En curso</option>
          <option value="CLOSED">Cerradas</option>
        </select>
      </div>
      <span class="filtro-contador">{{ total() }} autoevaluación(es)</span>
    </div>

    @if (error()) { <div class="alert-error">{{ error() }}</div> }

    @if (cargando()) {
      <app-loader label="Cargando autoevaluaciones..." />
    } @else {
      <div class="panel-blanco tabla">
        <table class="tabla-proto">
          <thead>
            <tr><th>IPRESS</th><th>Año</th><th>Categoría</th><th>Estado</th><th>Total puntaje obtenido</th><th>Ingresar</th></tr>
          </thead>
          <tbody>
            @for (a of filas(); track a.id) {
              <tr>
                <td>{{ a.facilityName }}<span class="celda-sub">{{ a.facilityCode }}</span></td>
                <td>{{ a.periodYear }}</td>
                <td>{{ categoria(a.facilityCategory) }}</td>
                <td>
                  <span class="badge" [class.badge-green]="a.status === 'OPEN'" [class.badge-gray]="a.status === 'CLOSED'">{{ estado(a.status) }}</span>
                  @if (a.evidenceLocked && a.status === 'OPEN') {
                    <span class="celda-sub candado" title="Carga documental del evaluado cerrada por el evaluador"><i class="bi bi-lock-fill"></i> Carga del evaluado cerrada</span>
                  }
                </td>
                <td>
                  @if (a.finalScore !== null) {
                    <span class="puntaje" [class]="'puntaje ' + color(a)">{{ a.finalScore | number: '1.2-2' }}%</span>
                    <span class="celda-sub">{{ a.classificationLabel }}</span>
                  } @else { <span class="celda-sub">En curso</span> }
                </td>
                <td><a [routerLink]="['/autoevaluaciones', a.id]" class="btn btn-ir">Ir</a></td>
              </tr>
            } @empty {
              <tr><td colspan="6">
                <div class="empty-state">
                  <i class="bi bi-clipboard2-check empty-state-icon"></i>
                  <span class="empty-state-title">No hay autoevaluaciones</span>
                  @if (auth.can('autoevaluacion.gestionar')) {
                    <span class="empty-state-sub">Inicie la autoevaluación del año con el botón "Iniciar autoevaluación".</span>
                  } @else {
                    <span class="empty-state-sub">El evaluador de su IPRESS debe iniciar la autoevaluación del año.</span>
                  }
                </div>
              </td></tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
  styles: [`
    .nueva { margin-bottom: 16px; }
    .tabla { overflow-x: auto; }
    .tabla td:first-child { font-weight: 600; color: var(--gray-800); }
    .nueva .ayuda { margin: 0 0 14px; font-size: 13px; color: var(--gray-500); }
    .section-title i { color: var(--essalud-main); margin-right: 4px; }
    .filtro-campo.ipress { min-width: 320px; }
    .puntaje { font-weight: 700; }
    .candado { color: #9a3412; }
    .puntaje.verde { color: #15803d; }
    .puntaje.amarillo { color: #b45309; }
    .puntaje.rojo { color: #b91c1c; }
    @media (max-width: 767px) {
      .form-grid { grid-template-columns: 1fr; }
      .filtro-campo.ipress { min-width: 0; width: 100%; }
      .table-card { overflow-x: auto; }
    }
  `],
})
export class AutoevaluacionListaComponent implements OnInit {
  readonly auth = inject(AuthStore);
  private readonly api = inject(AutoevaluacionService);
  private readonly organizacion = inject(OrganizacionService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  readonly categorias = CATEGORIAS_IPRESS;
  readonly anioActual = new Date().getFullYear();

  readonly filas = signal<Autoevaluacion[]>([]);
  readonly total = signal(0);
  readonly cargando = signal(true);
  readonly error = signal('');
  readonly filtroIpress = signal<number | null>(null);
  readonly filtroAnio = signal<number | null>(null);
  readonly filtroEstado = signal<AssessmentStatus | null>(null);

  readonly formulario = signal(false);
  readonly iniciando = signal(false);
  readonly ipress = signal<Ipress[]>([]);
  nuevaIpress: number | null = null;
  nuevoAnio: number = this.anioActual;
  nuevaCategoria: FacilityCategory | null = null;

  /** Un evaluador solo inicia la de su IPRESS. */
  readonly miIpress = computed(() => this.auth.alcance() === 'IPRESS' ? this.auth.user()?.ipressNombre ?? null : null);
  readonly opcionesIpress = computed<SelectOption[]>(() =>
    this.ipress().map((i) => ({ value: i.id, label: `${i.codigo} · ${i.nombre}` })));
  readonly opcionesIpressFiltro = computed<SelectOption[]>(() => [{ value: '', label: 'Todas' }, ...this.opcionesIpress()]);

  ngOnInit(): void {
    this.buscar();
    if (this.auth.alcance() !== 'IPRESS') {
      this.organizacion.ipress().subscribe({ next: (l) => this.ipress.set(l), error: () => {} });
    }
  }

  categoria = (c: FacilityCategory) => ETIQUETA_CATEGORIA[c];
  estado = (s: AssessmentStatus) => ETIQUETA_ESTADO_AUTOEVALUACION[s];
  color = (a: Autoevaluacion) => (a.classification ? COLOR_CLASIFICACION[a.classification] : '');

  ipressElegida(): number | null {
    return this.auth.alcance() === 'IPRESS' ? this.auth.user()?.idIpress ?? null : this.nuevaIpress;
  }

  nivelSugerido(): string | null {
    const id = this.ipressElegida();
    const nivel = this.ipress().find((i) => i.id === id)?.nivelComplejidad;
    return nivel ?? null;
  }

  filtrarIpress(valor: number | string | null): void {
    this.filtroIpress.set(valor === '' || valor === null ? null : Number(valor));
    this.buscar();
  }

  buscar(): void {
    this.cargando.set(true);
    this.api.buscar({ facilityId: this.filtroIpress(), year: this.filtroAnio() || null, status: this.filtroEstado(), size: 100 })
      .subscribe({
        next: (p) => {
          this.filas.set(p.content);
          this.total.set(p.totalElements);
          this.error.set('');
          this.cargando.set(false);
        },
        error: (err) => {
          this.error.set(mensajeDeError(err, 'No se pudieron cargar las autoevaluaciones.'));
          this.cargando.set(false);
        },
      });
  }

  abrirFormulario(): void {
    this.nuevaIpress = null;
    this.nuevoAnio = this.anioActual;
    this.nuevaCategoria = null;
    this.formulario.set(true);
  }

  iniciar(): void {
    const ipress = this.ipressElegida();
    if (!ipress || !this.nuevaCategoria) return;
    this.iniciando.set(true);
    this.api.iniciar(ipress, Number(this.nuevoAnio), this.nuevaCategoria).subscribe({
      next: (a) => {
        this.toast.success(`Autoevaluación ${a.periodYear} iniciada.`);
        this.router.navigate(['/autoevaluaciones', a.id]);
      },
      error: (err) => {
        this.toast.error(mensajeDeError(err, 'No se pudo iniciar la autoevaluación.'));
        this.iniciando.set(false);
      },
    });
  }
}
