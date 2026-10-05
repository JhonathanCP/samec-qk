import { RolCodigo } from '../models/sesion.model';

/** Nombre corto de cada rol para mostrar en pantalla (el nombre oficial viene del core). */
export const ETIQUETA_ROL: Record<RolCodigo, string> = {
  ADMIN_SISTEMA: 'Administrador',
  GESTOR_CALIDAD_SEDE: 'Gestor de Calidad · Sede Central',
  GESTOR_CALIDAD_RED: 'Gestor de Calidad · Red',
  GESTOR_CALIDAD_IPRES: 'Gestor de Calidad · IPRESS',
  EVALUADOR: 'Evaluador',
  EVALUADO: 'Evaluado',
};
