/**
 * Desarrollo: `npm start` sirve en http://localhost:4200 y proxy.conf.json reenvía /samec-api al
 * core (localhost:8753), igual que Nginx en producción. Así el front usa la misma URL en ambos
 * entornos y la cookie del refresh token es del mismo origen.
 */
export const environment = {
  production: false,
  apiUrl: '/samec-api',
  /** Clave pública de reCAPTCHA v3; vacía = desactivado (el core también debe tenerlo apagado). */
  recaptchaSiteKey: '',
  /** Dominio obligatorio del correo en el autorregistro (samec.registro.dominio-correo). */
  dominioCorreo: 'essalud.gob.pe',
};
