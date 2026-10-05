import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Debe coincidir con samec.auth.password-longitud-minima y PoliticaPassword en el core. */
export const PASSWORD_LONGITUD_MINIMA = 8;
export const PASSWORD_LONGITUD_MAXIMA = 64;
export const PASSWORD_AYUDA = `Mínimo ${PASSWORD_LONGITUD_MINIMA} caracteres, con letras y números.`;

/** Política de contraseña nueva (el core la vuelve a validar). */
export const passwordSegura: ValidatorFn = (c: AbstractControl): ValidationErrors | null => {
  const v: string = c.value ?? '';
  if (!v) return null;
  if (v.length < PASSWORD_LONGITUD_MINIMA || v.length > PASSWORD_LONGITUD_MAXIMA) return { passwordLongitud: true };
  if (!/[A-Za-zÁÉÍÓÚáéíóúÑñ]/.test(v) || !/\d/.test(v)) return { passwordDebil: true };
  return null;
};

/** Para el grupo: `password` y `passwordConfirmar` deben ser iguales. */
export const passwordsIguales: ValidatorFn = (g: AbstractControl): ValidationErrors | null => {
  const p = g.get('password')?.value;
  const c = g.get('passwordConfirmar')?.value;
  return p && c && p !== c ? { passwordsDistintas: true } : null;
};
