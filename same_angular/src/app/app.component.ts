import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SessionTimerService } from './core/services/session-timer.service';
import { ToastComponent } from './shared/components/toast/toast.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastComponent],
  template: `
    <router-outlet />
    <app-toast />
  `,
})
export class AppComponent {
  // Inyectarlo activa la renovación automática del access token (ver SessionTimerService).
  private readonly sessionTimer = inject(SessionTimerService);
}
