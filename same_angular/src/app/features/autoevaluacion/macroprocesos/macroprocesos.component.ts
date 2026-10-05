import { Component, computed, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ETIQUETA_CATEGORIA, ETIQUETA_ESTADO_AUTOEVALUACION, semaforo } from '../../../core/constants/lea';
import { AutoevaluacionService } from '../../../core/services/autoevaluacion.service';
import { ModalService } from '../../../core/services/modal.service';
import { ToastService } from '../../../core/services/toast.service';
import { mensajeDeError } from '../../../core/utils/http-error';
import { AutoevaluacionStore, porcentaje } from '../autoevaluacion.store';

/**
 * Pantalla principal de la autoevaluación (prototipo "Carga documental > Macroprocesos"): avance
 * por macroproceso, recuento de criterios con puntaje 0 y total obtenido con su clasificación.
 */
@Component({
  selector: 'app-macroprocesos',
  imports: [RouterLink, DecimalPipe],
  template: `
    @let a = store.autoevaluacion()!;
    <div class="migas">
      <a routerLink="/autoevaluaciones">Autoevaluaciones</a>
      <span class="sep">&gt;</span>
      <span>{{ store.esEvaluador() ? 'Evaluación' : 'Carga documental' }} {{ a.periodYear }}</span>
      <span class="sep">&gt;</span>
      <span class="actual">Macroprocesos</span>
      <span class="extra">
        <span class="ipress">{{ a.facilityName }} · {{ categoria(a.facilityCategory) }}</span>
        <span class="badge" [class.badge-green]="a.status === 'OPEN'" [class.badge-gray]="a.status === 'CLOSED'">{{ estado(a.status) }}</span>
      </span>
    </div>

    <div class="accesos">
      @if (store.esEvaluador()) {
        <a routerLink="bandeja" class="acceso">
          <i class="bi bi-inbox"></i> Bandeja de revisión
          @if (store.avanceTotal().porRevisar) { <span class="contador">{{ store.avanceTotal().porRevisar }}</span> }
        </a>
      } @else {
        <a routerLink="bandeja" [queryParams]="{ estado: 'OBSERVED' }" class="acceso">
          <i class="bi bi-exclamation-triangle"></i> Sustentos observados
          @if (store.avanceTotal().sustentosObservados) { <span class="contador">{{ store.avanceTotal().sustentosObservados }}</span> }
        </a>
      }
      <a routerLink="puntaje" class="acceso"><i class="bi bi-calculator"></i> Cálculo del puntaje</a>
      @if (store.esEvaluador() && store.abierta()) {
        <button type="button" class="acceso interruptor" [class.cerrado]="store.cargaCerrada()" (click)="alternarCarga()"
                [disabled]="store.ocupado() === 'candado'" role="switch" [attr.aria-checked]="!store.cargaCerrada()"
                [title]="store.cargaCerrada() ? 'Reabrir la carga documental del evaluado' : 'Cerrar la carga documental del evaluado'">
          <i [class]="store.cargaCerrada() ? 'bi bi-lock-fill' : 'bi bi-unlock'"></i>
          Carga documental del evaluado: {{ store.cargaCerrada() ? 'cerrada' : 'abierta' }}
          <span class="pista"><span class="perilla"></span></span>
        </button>
      }
    </div>

    <div class="grilla">
      <section class="panel-blanco tabla-macros">
        <table class="tabla-proto">
          <thead>
            <tr>
              <th>Macroproceso</th><th>Total<br />estándares</th><th>Criterios</th><th>Puntaje<br />alcanzado</th>
              <th>Avance<br />sustento</th><th>{{ store.esEvaluador() ? 'Validar' : 'Sustentar' }}</th>
            </tr>
          </thead>
          <tbody>
            @for (m of store.macros(); track m.id) {
              @let av = avance(m.id);
              <tr>
                <td class="nombre">{{ m.name }} <small>{{ m.code }}</small></td>
                <td>{{ av.estandares }}</td>
                <td>{{ av.criterios }}</td>
                <td>{{ av.puntaje }}</td>
                <td>
                  <span [title]="av.sustentosCargados + ' de ' + av.sustentosRequeridos + ' sustentos cargados · ' + av.sustentosAprobados + ' aprobados'">
                    {{ pct(av.sustentosCargados, av.sustentosRequeridos) }}%
                  </span>
                  @if (store.esEvaluador() && av.porRevisar) { <small class="por-revisar">{{ av.porRevisar }} por revisar</small> }
                  @if (!store.esEvaluador() && av.sustentosObservados) { <small class="observados">{{ av.sustentosObservados }} observado(s)</small> }
                </td>
                <td><a [routerLink]="['macroprocesos', m.id]" class="btn btn-ir">Ir</a></td>
              </tr>
            }
          </tbody>
        </table>

        @if (store.resultado(); as r) {
          <div class="total">
            <div class="total-linea">
              <span class="total-texto">Total puntaje obtenido:</span>
              <span class="total-valor" [class]="'total-valor ' + color(r.finalScore)">{{ r.finalScore | number: '1.0-2' }} %</span>
              <span class="pill" [class]="'pill ' + color(r.finalScore)">{{ textoClasificacion(r.classification) }}</span>
            </div>
            @if (!r.complete) {
              <p class="aviso">Puntaje a la fecha: faltan calificar {{ r.criteriaCount - r.scoredCount }} de {{ r.criteriaCount }} criterios (cuentan como 0).</p>
            }
            @if (store.puedeGestionar()) {
              <button class="btn btn-azul btn-sm" [disabled]="!r.complete || store.ocupado() === 'cerrar'" (click)="cerrar()"
                      [title]="r.complete ? '' : 'Debe calificar todos los criterios'">
                <i class="bi bi-lock-fill"></i> Cerrar autoevaluación
              </button>
            }
          </div>
          <div class="estructura">
            <span>Estructura de puntaje:</span>
            <span>No aprobado: reiniciar desde autoeval. (&lt; 50%)</span>
            <span>No aprobado: subsanar en 6 meses (70%-84%)</span>
            <span>No aprobado: subsanar en 9 meses (50%-69%)</span>
            <span>Aprobado (85% o más)</span>
          </div>
        }
      </section>

      <aside class="lateral">
        <section class="panel-blanco">
          <h3 class="titulo-centrado">Recuento de criterios por puntaje</h3>
          <div class="leyenda"><strong>Puntajes:</strong> <span class="punto"></span> 0</div>
          @if (recuentoCero().length) {
            <div class="barras">
              @for (b of recuentoCero(); track b.codigo) {
                <div class="barra-fila" [title]="b.cantidad + ' criterio(s) con puntaje 0'">
                  <span class="barra-nombre">{{ b.nombre }}</span>
                  <span class="barra"><span [style.width.%]="(b.porcentaje * 100) / maximoCero()"></span></span>
                  <span class="barra-valor">{{ b.porcentaje | number: '1.2-2' }}%</span>
                </div>
              }
            </div>
            <p class="pie-grafico">Porcentaje de los criterios con puntaje 0, por macroproceso (oportunidad de mejora)</p>
          } @else {
            <p class="sin-datos"><i class="bi bi-bar-chart"></i> Aún no hay criterios calificados con 0.</p>
          }
        </section>

        <section class="panel-blanco">
          <h3 class="titulo-centrado">Generación de reportes finales</h3>
          <div class="reportes">
            <button class="btn btn-outline btn-sm" disabled title="Próximamente"><i class="bi bi-file-earmark-excel text-excel"></i> Reporte Acredita</button>
            <button class="btn btn-outline btn-sm" disabled title="Próximamente"><i class="bi bi-file-earmark-word text-word"></i> Generar Anexo 2</button>
            <button class="btn btn-outline btn-sm" disabled title="Próximamente"><i class="bi bi-file-earmark-word text-word"></i> Generar Anexo 4</button>
          </div>
          <p class="pie-grafico">Disponibles próximamente.</p>
        </section>
      </aside>
    </div>
  `,
  styles: [`
    .migas .ipress { color: var(--gray-600); font-weight: 600; }
    .accesos { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 14px; }
    .acceso {
      display: inline-flex; align-items: center; gap: 8px; padding: 8px 14px; border-radius: var(--radius-md);
      background: #fff; color: var(--sam-azul); font-weight: 600; font-size: 13.5px; box-shadow: var(--shadow-xs);
      border: 1px solid var(--gray-200);
    }
    .acceso:hover { background: var(--sam-azul-suave); }
    .interruptor { margin-left: auto; cursor: pointer; font: inherit; font-weight: 600; font-size: 13.5px; }
    .interruptor.cerrado { color: #9a3412; border-color: #fdba74; background: #fff7ed; }
    .pista { width: 38px; height: 20px; border-radius: 999px; background: #22a55b; position: relative; transition: background .2s; }
    .perilla { position: absolute; top: 2px; left: 20px; width: 16px; height: 16px; border-radius: 50%; background: #fff; transition: left .2s; }
    .cerrado .pista { background: var(--gray-300); }
    .cerrado .perilla { left: 2px; }
    .contador {
      min-width: 20px; height: 20px; padding: 0 6px; border-radius: 999px; background: var(--sam-naranja); color: #fff;
      font-size: 11px; font-weight: 700; display: inline-flex; align-items: center; justify-content: center;
    }
    .grilla { display: grid; grid-template-columns: minmax(0, 1.7fr) minmax(300px, 1fr); gap: 18px; align-items: start; }
    .tabla-macros :is(th, td) { padding-left: 8px; padding-right: 8px; }
    .tabla-macros th:first-child, .tabla-macros td:first-child { min-width: 210px; }
    .tabla-macros { overflow-x: auto; }
    .nombre { font-weight: 600; color: var(--gray-800); }
    .nombre small { display: block; font-weight: 700; color: var(--sam-azul); font-size: 11px; }
    .por-revisar, .observados { display: block; font-size: 11px; font-weight: 700; }
    .por-revisar { color: var(--sam-naranja); }
    .observados { color: var(--danger-text); }
    .total {
      margin-top: 14px; padding: 14px 18px; border: 1px solid var(--gray-200); border-radius: var(--radius-md);
      display: flex; flex-direction: column; gap: 8px; align-items: flex-start;
    }
    .total-linea { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
    .total-texto { font-size: 19px; font-weight: 900; text-transform: uppercase; color: var(--gray-900); letter-spacing: .01em; }
    .total-valor { font-size: 24px; font-weight: 900; }
    .total-valor.rojo { color: #dc2626; }
    .total-valor.amarillo { color: #d97706; }
    .total-valor.verde { color: #16a34a; }
    .pill { padding: 6px 16px; border-radius: 999px; font-size: 16px; font-weight: 800; }
    .pill.rojo { background: #fca5a5; color: #111827; }
    .pill.amarillo { background: #fde68a; color: #111827; }
    .pill.verde { background: #86efac; color: #111827; }
    .aviso { margin: 0; font-size: 12.5px; color: var(--warning-text); }
    .estructura {
      margin-top: 10px; display: grid; grid-template-columns: 1fr 1fr; gap: 2px 18px; font-size: 11.5px; color: var(--gray-600);
    }
    .estructura span:first-child { grid-column: 1 / -1; text-decoration: underline; }
    .lateral { display: flex; flex-direction: column; gap: 18px; }
    .titulo-centrado { margin: 0 0 12px; text-align: center; color: var(--sam-azul); font-size: 17px; font-weight: 700; text-transform: uppercase; }
    .leyenda { font-size: 11.5px; color: var(--gray-600); margin-bottom: 10px; display: flex; align-items: center; gap: 6px; }
    .punto { width: 9px; height: 9px; border-radius: 50%; background: #2b8af7; display: inline-block; }
    .barras { display: flex; flex-direction: column; gap: 8px; }
    .barra-fila { display: grid; grid-template-columns: minmax(110px, 38%) 1fr 54px; gap: 10px; align-items: center; font-size: 11.5px; }
    .barra-nombre { color: var(--gray-600); text-align: right; line-height: 1.2; }
    .barra { height: 22px; background: transparent; }
    .barra span { display: block; height: 100%; background: #2b8af7; border-radius: 2px; }
    .barra-valor { color: var(--gray-600); }
    .pie-grafico { margin: 10px 0 0; text-align: center; font-size: 11.5px; color: var(--gray-500); }
    .sin-datos { margin: 14px 0; text-align: center; color: var(--gray-400); font-size: 13px; }
    .reportes { display: flex; justify-content: space-around; gap: 8px; flex-wrap: wrap; }
    .text-excel { color: #1d6f42; }
    .text-word { color: #2b579a; }
    @media (max-width: 1279px) { .grilla { grid-template-columns: 1fr; } }
    @media (max-width: 767px) { .estructura { grid-template-columns: 1fr; } .total-texto { font-size: 16px; } }
  `],
})
export class MacroprocesosComponent {
  readonly store = inject(AutoevaluacionStore);
  private readonly api = inject(AutoevaluacionService);
  private readonly modal = inject(ModalService);
  private readonly toast = inject(ToastService);

  /** Porcentaje de los criterios en 0 que corresponde a cada macroproceso (como el gráfico del prototipo). */
  readonly recuentoCero = computed(() => {
    const total = this.store.avanceTotal().criteriosEnCero;
    if (!total) return [];
    return this.store.macros()
      .map((m) => {
        const cantidad = this.store.avancePorMacro().get(m.id)?.criteriosEnCero ?? 0;
        return { codigo: m.code, nombre: m.name, cantidad, porcentaje: (cantidad * 100) / total };
      })
      .filter((b) => b.cantidad > 0)
      .sort((x, y) => y.cantidad - x.cantidad);
  });
  readonly maximoCero = computed(() => Math.max(...this.recuentoCero().map((b) => b.porcentaje), 1));

  avance = (id: number) => this.store.avancePorMacro().get(id)!;
  pct = porcentaje;
  color = (p: number) => semaforo(p);
  categoria = (c: keyof typeof ETIQUETA_CATEGORIA) => ETIQUETA_CATEGORIA[c];
  estado = (s: keyof typeof ETIQUETA_ESTADO_AUTOEVALUACION) => ETIQUETA_ESTADO_AUTOEVALUACION[s];

  textoClasificacion(c: string): string {
    return c === 'APPROVED' ? 'Aprobado' : 'No Aprobado';
  }

  /** Candado de la carga documental del evaluado (como el "Activar/Desactivar Reporte" de samec-old). */
  async alternarCarga(): Promise<void> {
    if (this.store.cargaCerrada()) {
      const ok = await this.modal.confirm('El evaluado podrá volver a registrar y corregir sustentos. Las calificaciones '
        + 'automáticas que se pusieron al cerrar la carga se quitarán. ¿Reabrir?', 'Reabrir carga documental del evaluado', 'Reabrir');
      if (ok) await this.store.ejecutar('candado', this.api.abrirCarga(this.store.id()), 'Carga documental del evaluado reabierta.');
      return;
    }
    const ok = await this.modal.confirm('El evaluado ya no podrá registrar, corregir ni retirar sustentos; usted podrá seguir '
      + 'revisando y calificando. Los criterios documentales sin ningún sustento se calificarán automáticamente. '
      + 'Puede reabrirla después. ¿Cerrar la carga documental del evaluado?', 'Cerrar carga documental del evaluado', 'Cerrar');
    if (!ok) return;
    this.store.ocupado.set('candado');
    this.api.cerrarCarga(this.store.id()).subscribe({
      next: (r) => {
        this.store.ocupado.set(null);
        this.toast.success(r.autoScored
          ? `Carga documental del evaluado cerrada. ${r.autoScored} criterio(s) sin sustentos se calificaron automáticamente.`
          : 'Carga documental del evaluado cerrada.');
        this.store.recargar();
      },
      error: (err) => {
        this.store.ocupado.set(null);
        this.toast.error(mensajeDeError(err));
      },
    });
  }

  async cerrar(): Promise<void> {
    const r = this.store.resultado();
    if (!r) return;
    const ok = await this.modal.confirm(
      `Se cerrará la autoevaluación ${r.assessment.periodYear} con un puntaje final de ${r.finalScore.toFixed(2)}% ` +
      `(${r.classificationLabel}). Ya no se podrá modificar. ¿Continuar?`, 'Cerrar autoevaluación', 'Cerrar');
    if (!ok) return;
    if (await this.store.ejecutar('cerrar', this.api.cerrar(this.store.id()), 'Autoevaluación cerrada.')) {
      await this.modal.success(`Puntaje final: ${r.finalScore.toFixed(2)}%`, 'Autoevaluación cerrada');
    }
  }
}
