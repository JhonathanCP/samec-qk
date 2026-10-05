import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-sin-acceso',
  imports: [RouterLink],
  template: `
    <div class="wrapper">
      <div class="card-sin-acceso">
        <div class="icon-wrap"><i class="bi bi-shield-x"></i></div>
        <h1>Acceso denegado</h1>
        <p>No tiene permisos para acceder a esta sección.<br>Si cree que es un error, contacte al administrador.</p>
        <a routerLink="/inicio" class="btn btn-primary"><i class="bi bi-house"></i> Volver al inicio</a>
      </div>
    </div>
  `,
  styles: [`
    .wrapper { display: flex; align-items: center; justify-content: center; min-height: 400px; padding: 24px; }
    .card-sin-acceso {
      text-align: center; background: #fff; padding: 48px 40px; border-radius: var(--radius-xl);
      box-shadow: var(--shadow-lg); border: 1px solid var(--gray-200); max-width: 420px;
    }
    .icon-wrap {
      width: 72px; height: 72px; border-radius: 50%; background: var(--danger-bg); border: 2px solid var(--danger-border);
      display: flex; align-items: center; justify-content: center; margin: 0 auto 20px; font-size: 32px; color: #ef4444;
    }
    h1 { font-size: 20px; font-weight: 800; color: var(--gray-900); margin: 0 0 8px; }
    p { color: var(--gray-500); font-size: 14px; margin-bottom: 28px; line-height: 1.6; }
  `],
})
export class SinAccesoComponent {}
