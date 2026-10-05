import { Injectable, signal } from '@angular/core';

export type ToastTipo = 'info' | 'success' | 'warning' | 'error';

export interface ToastItem {
  id: number;
  mensaje: string;
  tipo: ToastTipo;
  duracionMs: number;
}

/**
 * Notificaciones no bloqueantes ("toaster") en la esquina de la pantalla — distinto de
 * ModalService, que siempre muestra un overlay bloqueante sobre toda la pantalla.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 1;
  readonly toasts = signal<ToastItem[]>([]);

  show(mensaje: string, tipo: ToastTipo = 'info', duracionMs = 5000): void {
    const id = this.nextId++;
    this.toasts.update(list => [...list, { id, mensaje, tipo, duracionMs }]);
    if (duracionMs > 0) {
      setTimeout(() => this.dismiss(id), duracionMs);
    }
  }

  info(mensaje: string, duracionMs = 5000): void { this.show(mensaje, 'info', duracionMs); }
  success(mensaje: string, duracionMs = 4000): void { this.show(mensaje, 'success', duracionMs); }
  warning(mensaje: string, duracionMs = 6000): void { this.show(mensaje, 'warning', duracionMs); }
  error(mensaje: string, duracionMs = 7000): void { this.show(mensaje, 'error', duracionMs); }

  dismiss(id: number): void {
    this.toasts.update(list => list.filter(t => t.id !== id));
  }
}
