import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PERMISOS_USUARIOS } from '../../core/constants/permissions';
import { ETIQUETA_ROL } from '../../core/constants/roles';
import { AuthStore } from '../../core/stores/auth.store';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

/**
 * Pantalla de inicio. Cuando el core tenga sus módulos, aquí van los tableros por alcance
 * (dashboard.global / dashboard.red / dashboard.ipress).
 */
@Component({
  selector: 'app-inicio',
  imports: [RouterLink, HasPermissionDirective],
  template: `
    <div class="page-header">
      <div>
        <h2 class="page-title">Hola, {{ auth.user()?.nombre }}</h2>
        <p class="page-subtitle">Sistema de Acreditación y Mejora Continua</p>
      </div>
    </div>

    <div class="grid">
      <div class="card">
        <h3 class="section-title"><i class="bi bi-person-badge"></i> Su sesión</h3>
        <dl>
          <dt>Usuario</dt><dd>{{ auth.user()?.username }}</dd>
          <dt>Rol</dt><dd>{{ roles() }}</dd>
          <dt>Alcance</dt><dd>{{ alcance() }}</dd>
          @if (auth.user()?.redNombre) { <dt>Red</dt><dd>{{ auth.user()?.redNombre }}</dd> }
          @if (auth.user()?.ipressNombre) { <dt>IPRESS</dt><dd>{{ auth.user()?.ipressNombre }}</dd> }
        </dl>
      </div>

      <div class="card" *appHasPermission="'autoevaluacion.ver'">
        <h3 class="section-title"><i class="bi bi-ui-checks-grid"></i> Autoevaluación</h3>
        <p class="texto">{{ textoAutoevaluacion() }}</p>
        <div class="atajos">
          <a routerLink="/autoevaluaciones" class="btn btn-primary"><i class="bi bi-clipboard2-check"></i> Ir a autoevaluaciones</a>
        </div>
      </div>

      <div class="card" *appHasPermission="permisosUsuarios">
        <h3 class="section-title"><i class="bi bi-gear"></i> Administración</h3>
        <div class="atajos">
          <a routerLink="/usuarios" class="btn btn-secondary"><i class="bi bi-people"></i> Usuarios</a>
          <a *appHasPermission="'usuarios.solicitudes'" routerLink="/usuarios/solicitudes" class="btn btn-secondary">
            <i class="bi bi-person-plus"></i> Solicitudes de registro
          </a>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 16px; }
    dl { display: grid; grid-template-columns: auto 1fr; gap: 8px 16px; font-size: 13.5px; }
    dt { color: var(--gray-500); font-weight: 600; }
    dd { color: var(--gray-800); }
    .texto { color: var(--gray-600); font-size: 13.5px; line-height: 1.6; }
    .atajos { display: flex; flex-wrap: wrap; gap: 8px; }
    .section-title i { color: var(--essalud-main); margin-right: 4px; }
  `],
})
export class InicioComponent {
  readonly auth = inject(AuthStore);
  readonly permisosUsuarios = PERMISOS_USUARIOS;

  readonly textoAutoevaluacion = computed(() => {
    if (this.auth.can('evidencias.evaluar')) {
      return 'Valide los sustentos que carga su IPRESS, verifique en campo las condiciones y califique los criterios de acreditación.';
    }
    if (this.auth.can('evidencias.registrar')) {
      return 'Realice la carga documental de las fuentes auditables de su IPRESS y corrija los sustentos que el evaluador observe.';
    }
    return 'Siga el avance de las autoevaluaciones de las IPRESS.';
  });

  readonly roles = computed(() => this.auth.roles().map((r) => ETIQUETA_ROL[r]).join(', '));
  readonly alcance = computed(() => ({
    NACIONAL: 'Nacional', RED: 'Su Red Asistencial', IPRESS: 'Su IPRESS',
  })[this.auth.alcance() ?? 'NACIONAL']);
}
