import { FormControl, FormGroup } from '@angular/forms';
import { passwordSegura, passwordsIguales } from './password.validators';

describe('validadores de contraseña', () => {
  it('exigen longitud mínima, letras y números (igual que PoliticaPassword en el core)', () => {
    expect(passwordSegura(new FormControl('corta1'))).toEqual({ passwordLongitud: true });
    expect(passwordSegura(new FormControl('soloLetrasLargas'))).toEqual({ passwordDebil: true });
    expect(passwordSegura(new FormControl('12345678'))).toEqual({ passwordDebil: true });
    expect(passwordSegura(new FormControl('Segura2026'))).toBeNull();
  });

  it('detectan confirmaciones distintas', () => {
    const g = new FormGroup({ password: new FormControl('Segura2026'), passwordConfirmar: new FormControl('Otra2026') });
    expect(passwordsIguales(g)).toEqual({ passwordsDistintas: true });
    g.controls.passwordConfirmar.setValue('Segura2026');
    expect(passwordsIguales(g)).toBeNull();
  });
});
