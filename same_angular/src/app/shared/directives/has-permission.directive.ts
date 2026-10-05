import { Directive, TemplateRef, ViewContainerRef, effect, inject, input } from '@angular/core';
import { Permission } from '../../core/constants/permissions';
import { AuthStore } from '../../core/stores/auth.store';

/**
 * Muestra el contenido solo si el usuario tiene el permiso (o alguno de la lista).
 * Uso: `<button *appHasPermission="'usuarios.crud'">` o `*appHasPermission="['a', 'b']"`.
 * Es solo presentación: el core vuelve a validar el permiso en cada endpoint.
 */
@Directive({ selector: '[appHasPermission]' })
export class HasPermissionDirective {
  readonly appHasPermission = input.required<Permission | Permission[]>();

  private readonly tpl = inject(TemplateRef<unknown>);
  private readonly vcr = inject(ViewContainerRef);
  private readonly auth = inject(AuthStore);
  private visible = false;

  constructor() {
    effect(() => {
      const requerido = this.appHasPermission();
      const puede = Array.isArray(requerido) ? this.auth.canAny(requerido) : this.auth.can(requerido);
      if (puede && !this.visible) this.vcr.createEmbeddedView(this.tpl);
      if (!puede && this.visible) this.vcr.clear();
      this.visible = puede;
    });
  }
}
