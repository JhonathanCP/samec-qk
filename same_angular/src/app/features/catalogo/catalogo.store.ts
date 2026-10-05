import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { DocumentoNormativo, NodoMacroCat, ProcesoInstitucional } from '../../core/models/catalogo.model';
import { CatalogoService } from '../../core/services/catalogo.service';
import { ModalService } from '../../core/services/modal.service';
import { ToastService } from '../../core/services/toast.service';
import { mensajeDeError } from '../../core/utils/http-error';
import { SelectOption } from '../../shared/components/searchable-select/searchable-select.component';

/**
 * Estado de "Macroprocesos, procesos y criterios": el árbol del catálogo, los procesos
 * institucionales y la normativa, compartidos por las pestañas y los editores (lo que se crea en
 * uno aparece de inmediato en los demás).
 */
@Injectable()
export class CatalogoStore {
  private readonly api = inject(CatalogoService);
  private readonly toast = inject(ToastService);
  private readonly modal = inject(ModalService);
  private readonly router = inject(Router);

  readonly arbol = signal<NodoMacroCat[]>([]);
  readonly procesos = signal<ProcesoInstitucional[]>([]);
  readonly normas = signal<DocumentoNormativo[]>([]);
  readonly cargando = signal(true);
  readonly error = signal('');
  /** Hay cambios sin guardar en el editor abierto. */
  readonly sucio = signal(false);
  readonly ocupado = signal(false);

  /** Suma de pesos de los macroprocesos (debe ser 100). */
  readonly sumaPesos = computed(() => Math.round(this.arbol().reduce((s, m) => s + Number(m.scoreWeight), 0) * 100) / 100);

  readonly opcionesProceso = computed<SelectOption[]>(() => {
    const lista = this.procesos();
    const raices = lista.filter((p) => p.parentId === null).sort((a, b) => a.name.localeCompare(b.name));
    const opciones: SelectOption[] = [];
    for (const r of raices) {
      opciones.push({ value: r.id!, label: r.name });
      lista.filter((p) => p.parentId === r.id).sort((a, b) => a.name.localeCompare(b.name))
        .forEach((s) => opciones.push({ value: s.id!, label: `${r.name} › ${s.name}` }));
    }
    return opciones;
  });

  readonly opcionesNorma = computed<SelectOption[]>(() =>
    [...this.normas()].sort((a, b) => a.title.localeCompare(b.title)).map((n) => ({ value: n.id!, label: n.title })));

  /** Estándares para mover un criterio: "DIR-1 · Direccionamiento". */
  readonly opcionesEstandar = computed<SelectOption[]>(() =>
    this.arbol().flatMap((m) => m.standards.map((e) => ({ value: e.id, label: `${e.code} · ${m.name}` }))));

  readonly opcionesMacro = computed<SelectOption[]>(() =>
    this.arbol().map((m) => ({ value: m.id, label: `${m.code} · ${m.name}` })));

  cargarTodo(): void {
    this.cargando.set(true);
    this.recargarArbol();
    this.recargarProcesos();
    this.recargarNormas();
  }

  recargarArbol(): void {
    this.api.arbol().subscribe({
      next: (a) => {
        this.arbol.set(a);
        this.error.set('');
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(mensajeDeError(err, 'No se pudo cargar el catálogo.'));
        this.cargando.set(false);
      },
    });
  }

  recargarProcesos(): void {
    this.api.procesos().subscribe({ next: (p) => this.procesos.set(p), error: () => {} });
  }

  recargarNormas(): void {
    this.api.normas().subscribe({ next: (n) => this.normas.set(n), error: () => {} });
  }

  nombreProceso(id: number | null): string {
    if (id === null) return '';
    return this.opcionesProceso().find((o) => o.value === id)?.label ?? '';
  }

  /** Dónde está un criterio en el árbol (para las migas y para abrir sus ancestros). */
  ubicarCriterio(id: number): { macroId: number; estandarId: number } | null {
    for (const m of this.arbol()) {
      for (const e of m.standards) if (e.criteria.some((c) => c.id === id)) return { macroId: m.id, estandarId: e.id };
    }
    return null;
  }

  ubicarEstandar(id: number): NodoMacroCat | null {
    return this.arbol().find((m) => m.standards.some((e) => e.id === id)) ?? null;
  }

  /**
   * Selecciona un elemento (queda en la URL: ?sel=m-1 macroproceso, e-2 estándar, c-3 criterio,
   * nm nuevo macroproceso, ne-1 nuevo estándar del macroproceso 1, nc-2 nuevo criterio del estándar 2).
   * Si hay cambios sin guardar, pregunta antes.
   */
  async irA(sel: string | null, pestana: string | null = 'estructura', preguntar = true): Promise<void> {
    if (preguntar && !(await this.puedeDescartar())) return;
    this.sucio.set(false);
    await this.router.navigate([], {
      queryParams: { sel, tab: pestana === 'estructura' ? null : pestana }, queryParamsHandling: 'merge',
    });
  }

  /** Confirma descartar los cambios sin guardar (true si se puede continuar). */
  async puedeDescartar(): Promise<boolean> {
    if (!this.sucio()) return true;
    const ok = await this.modal.confirm('Hay cambios sin guardar. ¿Descartarlos?', 'Cambios sin guardar', 'Descartar');
    if (ok) this.sucio.set(false);
    return ok;
  }

  /** Ejecuta una acción, muestra el resultado y devuelve lo que respondió el core (o null si falló). */
  async ejecutar<T>(accion: Observable<T>, exito: string): Promise<T | null> {
    this.ocupado.set(true);
    try {
      const r = await firstValueFrom(accion);
      if (exito) this.toast.success(exito);
      return r ?? (true as T);
    } catch (err) {
      this.toast.error(mensajeDeError(err));
      return null;
    } finally {
      this.ocupado.set(false);
    }
  }
}
