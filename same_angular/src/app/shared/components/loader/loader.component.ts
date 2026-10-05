import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-loader',
  imports: [CommonModule],
  template: `
    <div class="loader-wrap" [class.overlay]="overlay" [class.inline]="!overlay && !compact" [class.compact]="compact">
      <div class="loader-ring" [class.sm]="size === 'sm'" [class.lg]="size === 'lg'" [class.on-dark]="onDark">
        <div></div><div></div><div></div>
      </div>
      <p class="loader-label" *ngIf="label">{{ label }}</p>
    </div>
  `,
  styles: [`
    .loader-wrap {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 14px;
    }
    .loader-wrap.overlay {
      position: absolute;
      inset: 0;
      background: rgba(255, 255, 255, 0.82);
      backdrop-filter: blur(1.5px);
      z-index: 30;
      border-radius: inherit;
    }
    .loader-wrap.inline {
      padding: 40px 16px;
    }
    .loader-wrap.compact {
      flex-direction: row;
      gap: 7px;
      display: inline-flex;
    }
    .loader-wrap.compact .loader-label {
      color: inherit;
      font-size: inherit;
      font-weight: inherit;
    }

    /* Triple-ring spinner: essalud-main + accent, staggered for a fuller motion */
    .loader-ring {
      position: relative;
      width: 44px;
      height: 44px;
    }
    .loader-ring.sm { width: 24px; height: 24px; }
    .loader-ring.lg { width: 60px; height: 60px; }
    .loader-ring div {
      position: absolute;
      inset: 0;
      border-radius: 50%;
      border: 3px solid transparent;
      border-top-color: var(--essalud-main, #0056a0);
      animation: loader-spin 1.1s cubic-bezier(0.5, 0.05, 0.5, 0.95) infinite;
    }
    .loader-ring.sm div { border-width: 2px; }
    .loader-ring.lg div { border-width: 4px; }
    .loader-ring div:nth-child(1) { animation-delay: 0s; opacity: 1; }
    .loader-ring div:nth-child(2) {
      inset: 5px;
      border-top-color: var(--essalud-accent, #20c2d7);
      animation-delay: -0.35s;
      opacity: 0.75;
    }
    .loader-ring div:nth-child(3) {
      inset: 10px;
      border-top-color: var(--essalud-main, #0056a0);
      animation-delay: -0.7s;
      opacity: 0.5;
    }
    .loader-ring.sm div:nth-child(2) { inset: 3px; }
    .loader-ring.sm div:nth-child(3) { inset: 6px; }
    .loader-ring.lg div:nth-child(2) { inset: 7px; }
    .loader-ring.lg div:nth-child(3) { inset: 14px; }

    /* Variante para fondos oscuros/de color (ej. botones primarios azules) */
    .loader-ring.on-dark div {
      border-top-color: rgba(255, 255, 255, 0.95);
    }
    .loader-ring.on-dark div:nth-child(2) { border-top-color: rgba(255, 255, 255, 0.7); }
    .loader-ring.on-dark div:nth-child(3) { border-top-color: rgba(255, 255, 255, 0.45); }

    @keyframes loader-spin { to { transform: rotate(360deg); } }

    .loader-label {
      margin: 0;
      font-size: 13px;
      font-weight: 600;
      color: var(--gray-500, #64748b);
      letter-spacing: -0.1px;
    }
  `],
})
export class LoaderComponent {
  /** Tamaño del spinner. */
  @Input() size: 'sm' | 'md' | 'lg' = 'md';
  /** Texto opcional debajo del spinner. */
  @Input() label?: string;
  /** Si es true, cubre absolutamente al contenedor padre (que debe tener position: relative). */
  @Input() overlay = false;
  /** Uso compacto en línea: junto a texto, dentro de un botón o un hint pequeño. */
  @Input() compact = false;
  /** Variante en blanco, para usar sobre fondos oscuros o de color (ej. botones primarios). */
  @Input() onDark = false;
}
