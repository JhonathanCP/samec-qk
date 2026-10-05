import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-toast',
  imports: [CommonModule],
  template: `
    <div class="toast-stack">
      <div
        class="toast-item"
        *ngFor="let t of toastSvc.toasts()"
        [class]="'toast-' + t.tipo"
      >
        <i class="bi" [ngClass]="{
          'bi-info-circle-fill': t.tipo === 'info',
          'bi-check-circle-fill': t.tipo === 'success',
          'bi-exclamation-triangle-fill': t.tipo === 'warning',
          'bi-x-circle-fill': t.tipo === 'error'
        }"></i>
        <span class="toast-msg">{{ t.mensaje }}</span>
        <button type="button" class="toast-close" (click)="toastSvc.dismiss(t.id)" aria-label="Cerrar">
          <i class="bi bi-x"></i>
        </button>
      </div>
    </div>
  `,
  styles: [`
    .toast-stack {
      position: fixed; top: 18px; right: 18px; z-index: 10000;
      display: flex; flex-direction: column; gap: 10px; max-width: 360px;
    }
    .toast-item {
      display: flex; align-items: flex-start; gap: 10px;
      background: #fff; border-radius: 10px; padding: 12px 14px;
      box-shadow: 0 8px 24px rgba(15,23,42,.15); border-left: 4px solid var(--gray-300);
      font-size: 13px; font-weight: 500; color: var(--gray-800); line-height: 1.5;
      animation: toast-in .18s ease-out;
    }
    @keyframes toast-in {
      from { opacity: 0; transform: translateX(24px); }
      to { opacity: 1; transform: translateX(0); }
    }
    .toast-item i.bi-info-circle-fill,
    .toast-item i.bi-check-circle-fill,
    .toast-item i.bi-exclamation-triangle-fill,
    .toast-item i.bi-x-circle-fill { font-size: 17px; margin-top: 1px; flex-shrink: 0; }
    .toast-info { border-left-color: #3b82f6; }
    .toast-info i { color: #3b82f6; }
    .toast-success { border-left-color: #22c55e; }
    .toast-success i { color: #22c55e; }
    .toast-warning { border-left-color: #f59e0b; }
    .toast-warning i { color: #f59e0b; }
    .toast-error { border-left-color: #ef4444; }
    .toast-error i { color: #ef4444; }
    .toast-msg { flex: 1; }
    .toast-close {
      border: none; background: none; cursor: pointer; color: var(--gray-400);
      padding: 0; font-size: 16px; line-height: 1; flex-shrink: 0;
    }
    .toast-close:hover { color: var(--gray-600); }
  `],
})
export class ToastComponent {
  toastSvc = inject(ToastService);
}
