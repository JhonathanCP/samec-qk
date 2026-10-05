import { Routes } from '@angular/router';

/**
 * Recorrido del prototipo: autoevaluaciones → macroprocesos → criterios del macroproceso (con el
 * modal del criterio: Anexo 2) ; además bandeja de revisión y cálculo del puntaje.
 */
export const autoevaluacionRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('./lista/autoevaluacion-lista.component').then((m) => m.AutoevaluacionListaComponent),
  },
  {
    path: ':id',
    loadComponent: () => import('./shell/autoevaluacion-shell.component').then((m) => m.AutoevaluacionShellComponent),
    children: [
      {
        path: '',
        loadComponent: () => import('./macroprocesos/macroprocesos.component').then((m) => m.MacroprocesosComponent),
      },
      {
        path: 'macroprocesos/:macroId',
        loadComponent: () => import('./criterios/criterios.component').then((m) => m.CriteriosComponent),
      },
      {
        path: 'bandeja',
        loadComponent: () => import('./bandeja/bandeja-evidencias.component').then((m) => m.BandejaEvidenciasComponent),
      },
      {
        path: 'puntaje',
        loadComponent: () => import('./resultado/resultado.component').then((m) => m.ResultadoComponent),
      },
    ],
  },
];
