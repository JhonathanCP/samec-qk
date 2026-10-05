import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';
import {
  Checklist, NodoCriterio, NodoMacroproceso, Resultado, TipoObservacion,
} from '../../core/models/autoevaluacion.model';
import { AutoevaluacionService } from '../../core/services/autoevaluacion.service';
import { ToastService } from '../../core/services/toast.service';
import { AuthStore } from '../../core/stores/auth.store';
import { mensajeDeError } from '../../core/utils/http-error';

/** Avance de un macroproceso o de un criterio, calculado del checklist. */
export interface Avance {
  estandares: number;
  criterios: number;
  criteriosCalificados: number;
  /** Suma de los puntajes otorgados (como "Puntaje alcanzado" del prototipo). */
  puntaje: number;
  /** Fuentes auditables que la IPRESS debe sustentar. */
  sustentosRequeridos: number;
  /** Con documento cargado (por revisar o aprobado). */
  sustentosCargados: number;
  sustentosAprobados: number;
  sustentosObservados: number;
  porRevisar: number;
  verificaciones: number;
  verificacionesHechas: number;
  /** Criterios con puntaje 0 (oportunidad de mejora). */
  criteriosEnCero: number;
}

const VACIO: Avance = {
  estandares: 0, criterios: 0, criteriosCalificados: 0, puntaje: 0, sustentosRequeridos: 0, sustentosCargados: 0,
  sustentosAprobados: 0, sustentosObservados: 0, porRevisar: 0, verificaciones: 0, verificacionesHechas: 0,
  criteriosEnCero: 0,
};

/**
 * Estado de una autoevaluación abierta en pantalla (lo provee AutoevaluacionShellComponent y lo
 * comparten macroprocesos, criterios, bandeja y cálculo del puntaje). Cada acción recarga el
 * checklist y el puntaje: el core recalcula estados y puntajes sugeridos.
 */
@Injectable()
export class AutoevaluacionStore {
  private readonly api = inject(AutoevaluacionService);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthStore);

  readonly id = signal<number>(0);
  readonly checklist = signal<Checklist | null>(null);
  readonly resultado = signal<Resultado | null>(null);
  readonly cargando = signal(true);
  readonly error = signal('');
  readonly tiposObservacion = signal<TipoObservacion[]>([]);
  /** Acción en curso (clave del elemento) para deshabilitar sus botones. */
  readonly ocupado = signal<string | null>(null);

  readonly autoevaluacion = computed(() => this.checklist()?.assessment ?? null);
  readonly abierta = computed(() => this.autoevaluacion()?.status === 'OPEN');
  /** Candado del evaluador sobre la carga documental del evaluado. */
  readonly cargaCerrada = computed(() => !!this.autoevaluacion()?.evidenceLocked);
  readonly puedeRegistrar = computed(() => this.abierta() && !this.cargaCerrada() && this.auth.can('evidencias.registrar'));
  readonly puedeEvaluar = computed(() => this.abierta() && this.auth.can('evidencias.evaluar'));
  readonly puedeGestionar = computed(() => this.abierta() && this.auth.can('autoevaluacion.gestionar'));
  /** El evaluador valida; el evaluado sustenta (carga documental). */
  readonly esEvaluador = computed(() => this.auth.can('evidencias.evaluar'));

  readonly macros = computed(() => this.checklist()?.macroProcesses ?? []);

  readonly avancePorMacro = computed(() => {
    const mapa = new Map<number, Avance>();
    for (const m of this.macros()) mapa.set(m.id, avanceDeMacro(m));
    return mapa;
  });

  readonly avanceTotal = computed(() => {
    const total = { ...VACIO };
    for (const a of this.avancePorMacro().values()) sumar(total, a);
    return total;
  });

  iniciar(id: number): void {
    this.id.set(id);
    this.checklist.set(null);
    this.resultado.set(null);
    this.recargar(true);
    this.api.tiposObservacion().subscribe({ next: (t) => this.tiposObservacion.set(t), error: () => {} });
  }

  recargar(conIndicador = false): void {
    if (conIndicador) this.cargando.set(true);
    this.api.checklist(this.id()).subscribe({
      next: (c) => {
        this.checklist.set(c);
        this.error.set('');
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(mensajeDeError(err, 'No se pudo cargar la autoevaluación.'));
        this.cargando.set(false);
      },
    });
    this.api.resultado(this.id()).subscribe({ next: (r) => this.resultado.set(r), error: () => {} });
  }

  macro(id: number): NodoMacroproceso | null {
    return this.macros().find((m) => m.id === id) ?? null;
  }

  /** Macroproceso y criterio de una fuente auditable (para ir al criterio desde la bandeja). */
  ubicarFuente(fuenteId: number): { macroId: number; criterioId: number } | null {
    for (const m of this.macros()) {
      for (const e of m.standards) {
        for (const c of e.criteria) {
          if (c.conditions.some((co) => co.sources.some((f) => f.id === fuenteId))) return { macroId: m.id, criterioId: c.id };
        }
      }
    }
    return null;
  }

  /**
   * Ejecuta una acción sobre la API: marca el elemento como ocupado, muestra el resultado y
   * recarga. Devuelve true si salió bien.
   */
  async ejecutar<T>(clave: string, accion: Observable<T>, exito: string): Promise<boolean> {
    this.ocupado.set(clave);
    try {
      await firstValueFrom(accion);
      this.toast.success(exito);
      this.recargar();
      return true;
    } catch (err) {
      this.toast.error(mensajeDeError(err));
      return false;
    } finally {
      this.ocupado.set(null);
    }
  }

  /** Abre un archivo (PDF/imagen se ven en el navegador; el resto se descarga). */
  abrirArchivo(archivoId: number, nombre: string): void {
    this.api.descargarArchivo(archivoId).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        if (blob.type === 'application/pdf' || blob.type.startsWith('image/')) {
          window.open(url, '_blank', 'noopener');
        } else {
          const a = document.createElement('a');
          a.href = url;
          a.download = nombre;
          a.click();
        }
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: (err) => this.toast.error(mensajeDeError(err, 'No se pudo abrir el archivo.')),
    });
  }
}

export function avanceDeCriterio(criterio: NodoCriterio): Avance {
  const a = { ...VACIO, criterios: 1 };
  if (criterio.score) {
    a.criteriosCalificados = 1;
    a.puntaje = criterio.score.score;
    if (criterio.score.score === 0) a.criteriosEnCero = 1;
  }
  for (const condicion of criterio.conditions) {
    if (condicion.technique !== 'DOCUMENT_REVIEW') {
      a.verificaciones++;
      if (condicion.review) a.verificacionesHechas++;
    }
    for (const fuente of condicion.sources) {
      if (fuente.availability !== 'PROVIDED_BY_FACILITY') continue;
      a.sustentosRequeridos++;
      const estado = fuente.evidence?.status;
      if (estado === 'PENDING_REVIEW' || estado === 'APPROVED') a.sustentosCargados++;
      if (estado === 'APPROVED') a.sustentosAprobados++;
      if (estado === 'OBSERVED') a.sustentosObservados++;
      if (estado === 'PENDING_REVIEW') a.porRevisar++;
    }
  }
  return a;
}

function avanceDeMacro(macro: NodoMacroproceso): Avance {
  const a = { ...VACIO, estandares: macro.standards.length };
  for (const e of macro.standards) for (const c of e.criteria) sumar(a, avanceDeCriterio(c));
  a.estandares = macro.standards.length;
  return a;
}

function sumar(destino: Avance, origen: Avance): void {
  for (const k of Object.keys(destino) as (keyof Avance)[]) destino[k] += origen[k];
}

/** ¿El criterio tiene algo pendiente para quien mira? (filtro "Solo pendientes"). */
export function criterioPendiente(criterio: NodoCriterio, evaluador: boolean): boolean {
  const a = avanceDeCriterio(criterio);
  return evaluador
    ? a.porRevisar > 0 || a.verificacionesHechas < a.verificaciones || !criterio.score
    : a.sustentosCargados < a.sustentosRequeridos;
}

/** Semáforo de la carga documental (prototipo: verde completo, ámbar parcial, rojo sin carga). */
export function semaforoCarga(a: Avance): 'verde' | 'ambar' | 'rojo' | 'gris' {
  if (!a.sustentosRequeridos) return 'gris';
  if (a.sustentosCargados >= a.sustentosRequeridos) return 'verde';
  return a.sustentosCargados > 0 ? 'ambar' : 'rojo';
}

export function porcentaje(parte: number, total: number): number {
  return total ? Math.round((parte * 100) / total) : 0;
}
