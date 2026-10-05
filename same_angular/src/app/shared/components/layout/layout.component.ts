import { Component, HostListener, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { TopbarComponent } from '../topbar/topbar.component';
import { ModalComponent } from '../modal/modal.component';

/** Por debajo de este ancho el menú lateral pasa a ser un panel deslizable sobre el contenido. */
const ANCHO_MOVIL = 768;

@Component({
  selector: 'app-layout',
  imports: [RouterOutlet, CommonModule, SidebarComponent, TopbarComponent, ModalComponent],
  template: `
    <div class="layout-wrapper"
         [class.sidebar-collapsed]="collapsed()"
         [class.movil]="esMovil()"
         [class.menu-abierto]="esMovil() && menuAbierto()">
      <app-sidebar
        [collapsed]="!esMovil() && collapsed()"
        (click)="alPulsarMenu($event)"
      />
      <div class="sidebar-backdrop" *ngIf="esMovil() && menuAbierto()" (click)="menuAbierto.set(false)"></div>
      <div class="main-content">
        <app-topbar
          [collapsed]="collapsed()"
          (toggleSidebar)="alternarMenu()"
        />
        <div class="page-container">
          <router-outlet />
        </div>
      </div>
    </div>
    <app-modal />
  `,
  styles: [`
    .layout-wrapper {
      display: flex;
      height: 100vh;
      height: 100dvh;
      overflow: hidden;
      background: var(--sam-fondo);
    }
    .main-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      overflow: hidden;
      min-width: 0;
    }
    .page-container {
      flex: 1;
      overflow-y: auto;
      padding: 8px 28px 28px;
      background: var(--sam-fondo);
    }
    @media (max-width: 1023px) {
      .page-container { padding: 20px 18px; }
    }
    @media (max-width: 767px) {
      .page-container { padding: 16px 12px; }
    }

    /* En teléfonos el menú no ocupa ancho: se superpone al contenido y se abre con el botón ☰. */
    .layout-wrapper.movil app-sidebar {
      position: fixed;
      top: 0;
      left: 0;
      bottom: 0;
      z-index: 1100;
      display: block;
      transform: translateX(-100%);
      transition: transform .25s ease, box-shadow .25s ease;
    }
    .layout-wrapper.menu-abierto app-sidebar {
      transform: none;
      box-shadow: 0 0 32px rgba(15, 23, 42, .35);
    }
    .sidebar-backdrop {
      position: fixed;
      inset: 0;
      z-index: 1050;
      background: rgba(15, 23, 42, .45);
    }
  `],
})
export class LayoutComponent {
  esMovil = signal(window.innerWidth < ANCHO_MOVIL);
  /** Barra angosta (solo íconos) en pantallas medianas y grandes; en tablets arranca así. */
  collapsed = signal(window.innerWidth < 1024);
  /** Panel deslizable abierto, solo en teléfonos. */
  menuAbierto = signal(false);

  @HostListener('window:resize')
  alCambiarTamano(): void {
    const movil = window.innerWidth < ANCHO_MOVIL;
    if (movil !== this.esMovil()) {
      this.esMovil.set(movil);
      this.menuAbierto.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  alPresionarEscape(): void {
    this.menuAbierto.set(false);
  }

  alternarMenu(): void {
    if (this.esMovil()) this.menuAbierto.set(!this.menuAbierto());
    else this.collapsed.set(!this.collapsed());
  }

  /** En teléfonos, elegir una opción del menú lo cierra (también si ya estaba en esa pantalla). */
  alPulsarMenu(event: MouseEvent): void {
    if (this.esMovil() && (event.target as HTMLElement).closest('a')) this.menuAbierto.set(false);
  }
}
