// Genera src/environments/environment.prod.ts a partir de variables de entorno (lo usa el Dockerfile).
const fs = require('fs');
const path = require('path');

const apiUrl = process.env.API_URL || '/samec-api';
const recaptchaSiteKey = process.env.RECAPTCHA_SITE_KEY || '';
const dominioCorreo = process.env.DOMINIO_CORREO || 'essalud.gob.pe';

const content = `// Archivo generado por set-env.js durante \`npm run build:prod\` (no editar a mano).
export const environment = {
  production: true,
  apiUrl: '${apiUrl}',
  recaptchaSiteKey: '${recaptchaSiteKey}',
  dominioCorreo: '${dominioCorreo}',
};
`;

fs.writeFileSync(path.join(__dirname, 'src', 'environments', 'environment.prod.ts'), content, 'utf8');
console.log(`environment.prod.ts generado (apiUrl="${apiUrl}", recaptcha ${recaptchaSiteKey ? 'activo' : 'desactivado'})`);
