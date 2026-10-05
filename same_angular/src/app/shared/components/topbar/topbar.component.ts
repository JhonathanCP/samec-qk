import { Component, EventEmitter, Input, Output, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ETIQUETA_ROL } from '../../../core/constants/roles';
import { AuthStore } from '../../../core/stores/auth.store';

/** Barra superior (prototipo): usuario | IPRESS - rol a la izquierda; configuración y avatar a la derecha. */
@Component({
  selector: 'app-topbar',
  imports: [RouterLink],
  template: `
    <header class="topbar">
      <div class="izq">
        <button class="menu-btn" (click)="toggleSidebar.emit()" title="Menú" aria-label="Mostrar u ocultar el menú">
          <i class="bi bi-list"></i>
        </button>
        <i class="bi bi-person-fill persona" aria-hidden="true"></i>
        <span class="usuario">
          <span class="nombre">{{ auth.user()?.nombre }} {{ auth.user()?.apellido }}</span>
          <span class="contexto">&nbsp;|&nbsp; {{ contexto() }}</span>
        </span>
      </div>
      <div class="der">
        <a routerLink="/mi-cuenta/cambiar-contrasena" class="icono" title="Cambiar contraseña" aria-label="Cambiar contraseña">
          <i class="bi bi-gear"></i>
        </a>
        <span class="avatar" [title]="(auth.user()?.nombre ?? '') + ' ' + (auth.user()?.apellido ?? '')">{{ iniciales() }}</span>
      </div>
    </header>
  `,
  styles: [`
    .topbar {
      height: 64px; display: flex; align-items: center; justify-content: space-between; gap: 12px;
      padding: 0 28px; background: var(--sam-fondo); flex-shrink: 0;
    }
    .izq { display: flex; align-items: center; gap: 12px; min-width: 0; }
    .menu-btn {
      background: #fff; border: 1px solid var(--gray-200); border-radius: var(--radius-md); width: 36px; height: 36px;
      font-size: 17px; cursor: pointer; color: var(--sam-azul); display: flex; align-items: center; justify-content: center;
      flex-shrink: 0;
    }
    .menu-btn:hover { background: var(--sam-azul-suave); }
    .persona { font-size: 24px; color: var(--sam-azul); flex-shrink: 0; }
    .usuario { display: flex; min-width: 0; font-size: 17px; color: var(--sam-azul); white-space: nowrap; }
    .contexto { overflow: hidden; text-overflow: ellipsis; }
    .der { display: flex; align-items: center; gap: 14px; flex-shrink: 0; }
    .icono { font-size: 22px; color: var(--gray-800); display: flex; }
    .icono:hover { color: var(--sam-azul); }
    .avatar {
      width: 38px; height: 38px; border-radius: 50%; background: var(--sam-azul); color: #fff; font-weight: 700; font-size: 13px;
      display: flex; align-items: center; justify-content: center; box-shadow: 0 0 0 3px #fff, 0 0 0 4px var(--gray-200);
    }
    @media (max-width: 1023px) { .contexto { display: none; } }
    @media (max-width: 767px) {
      .topbar { padding: 0 12px; height: 56px; }
      .usuario { font-size: 15px; }
      .persona { display: none; }
    }
  `],
})
export class TopbarComponent {
  @Input() collapsed = false;
  @Output() toggleSidebar = new EventEmitter<void>();
  readonly auth = inject(AuthStore);

  readonly iniciales = computed(() => {
    const u = this.auth.user();
    return u ? `${u.nombre?.[0] ?? ''}${u.apellido?.[0] ?? ''}`.toUpperCase() : '?';
  });

  /** "H.N. Rebagliati - Evaluado", como en el prototipo ("P.M. Chilca - Rol IPRESS"). */
  readonly contexto = computed(() => {
    const u = this.auth.user();
    const lugar = u?.ipressNombre ?? u?.redNombre ?? 'Nivel nacional';
    const rol = this.auth.roles()[0];
    return rol ? `${lugar} - ${ETIQUETA_ROL[rol]}` : lugar;
  });
}
