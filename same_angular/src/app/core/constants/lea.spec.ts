import { HttpErrorResponse } from '@angular/common/http';
import { mensajeDeError } from '../utils/http-error';
import { COLOR_CLASIFICACION, ETIQUETA_CATEGORIA, semaforo } from './lea';

describe('textos y colores de la acreditación', () => {
  it('semáforo con los cortes de la estructura de puntaje (50 y 85)', () => {
    expect(semaforo(null)).toBe('rojo');
    expect(semaforo(49.99)).toBe('rojo');
    expect(semaforo(50)).toBe('amarillo');
    expect(semaforo(84.99)).toBe('amarillo');
    expect(semaforo(85)).toBe('verde');
    expect(COLOR_CLASIFICACION.APPROVED).toBe('verde');
    expect(COLOR_CLASIFICACION.NOT_APPROVED_RESTART).toBe('rojo');
  });

  it('categorías de IPRESS como las escribe el manual', () => {
    expect(ETIQUETA_CATEGORIA.II_1).toBe('II-1');
    expect(ETIQUETA_CATEGORIA.III_1).toBe('III-1');
  });
});

describe('mensajeDeError', () => {
  const error = (status: number, cuerpo: unknown = null) => new HttpErrorResponse({ status, error: cuerpo });

  it('usa el mensaje del core cuando lo hay', () => {
    expect(mensajeDeError(error(400, { message: 'La carga documental del evaluado está cerrada por el evaluador' })))
      .toBe('La carga documental del evaluado está cerrada por el evaluador');
  });

  it('sin mensaje, uno según el estado', () => {
    expect(mensajeDeError(error(0))).toContain('No se pudo conectar');
    expect(mensajeDeError(error(403))).toContain('No tiene permisos');
    expect(mensajeDeError(error(404))).toContain('no existe');
    expect(mensajeDeError(error(500), 'Falló')).toBe('Falló');
    expect(mensajeDeError(new Error('x'))).toBe('No se pudo completar la operación.');
  });
});
