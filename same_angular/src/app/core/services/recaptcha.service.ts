import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';

declare const grecaptcha: {
  ready(cb: () => void): void;
  execute(siteKey: string, options: { action: string }): Promise<string>;
};

/**
 * reCAPTCHA v3 opcional: solo se carga el script de Google si hay clave pública configurada
 * (environment.recaptchaSiteKey). Debe coincidir con SAMEC_RECAPTCHA_ENABLED en el core.
 */
@Injectable({ providedIn: 'root' })
export class RecaptchaService {
  private carga?: Promise<void>;

  get habilitado(): boolean {
    return !!environment.recaptchaSiteKey;
  }

  /** Token para la acción, o undefined si reCAPTCHA está desactivado o no se pudo obtener. */
  async token(accion: string): Promise<string | undefined> {
    if (!this.habilitado) return undefined;
    try {
      await this.cargar();
      return await new Promise<string>((resolve, reject) =>
        grecaptcha.ready(() => grecaptcha.execute(environment.recaptchaSiteKey, { action: accion }).then(resolve, reject)),
      );
    } catch {
      return undefined;
    }
  }

  /** Muestra el sello de reCAPTCHA solo en las pantallas que lo usan. */
  mostrarSello(visible: boolean): void {
    document.body.classList.toggle('recaptcha-visible', visible);
  }

  private cargar(): Promise<void> {
    return (this.carga ??= new Promise<void>((resolve, reject) => {
      const script = document.createElement('script');
      script.src = `https://www.google.com/recaptcha/api.js?render=${environment.recaptchaSiteKey}`;
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => {
        this.carga = undefined;
        reject(new Error('No se pudo cargar reCAPTCHA'));
      };
      document.head.appendChild(script);
    }));
  }
}
