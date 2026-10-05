import {
  AccreditationClassification, AssessmentStatus, ConditionStatus, CriterionType, EvaluationTechnique,
  FacilityCategory, MacroProcessCategory, SourceAvailability, SubmissionStatus,
} from '../models/autoevaluacion.model';

/** Textos en español de los códigos que publica la API del core. */

export const CATEGORIAS_IPRESS: { value: FacilityCategory; label: string }[] = [
  { value: 'I_1', label: 'I-1' }, { value: 'I_2', label: 'I-2' }, { value: 'I_3', label: 'I-3' },
  { value: 'I_4', label: 'I-4' }, { value: 'II_1', label: 'II-1' }, { value: 'II_2', label: 'II-2' },
  { value: 'III_1', label: 'III-1' },
];

export const ETIQUETA_CATEGORIA: Record<FacilityCategory, string> = Object.fromEntries(
  CATEGORIAS_IPRESS.map((c) => [c.value, c.label]),
) as Record<FacilityCategory, string>;

export const ETIQUETA_ESTADO_AUTOEVALUACION: Record<AssessmentStatus, string> = {
  OPEN: 'En curso',
  CLOSED: 'Cerrada',
};

export const ETIQUETA_TIPO_CRITERIO: Record<CriterionType, string> = {
  STRUCTURE: 'Estructura',
  PROCESS: 'Proceso',
  RESULT: 'Resultado',
};

export const ETIQUETA_TECNICA: Record<EvaluationTechnique, string> = {
  DOCUMENT_REVIEW: 'Revisión de documento',
  OBSERVATION: 'Observación',
  SAMPLING_MEDICAL_RECORD_AUDIT: 'Muestreo / auditoría de HC',
  INTERVIEW: 'Entrevista',
};

export const ICONO_TECNICA: Record<EvaluationTechnique, string> = {
  DOCUMENT_REVIEW: 'bi bi-file-earmark-text',
  OBSERVATION: 'bi bi-eye',
  SAMPLING_MEDICAL_RECORD_AUDIT: 'bi bi-journal-medical',
  INTERVIEW: 'bi bi-chat-square-text',
};

export const ETIQUETA_CATEGORIA_MACRO: Record<MacroProcessCategory, string> = {
  MANAGEMENT: 'Gerenciales',
  CARE_PROVISION: 'Prestacionales',
  SUPPORT: 'De apoyo',
};

/** Estado de la evidencia de una fuente: texto y clase de badge. */
export const ESTADO_EVIDENCIA: Record<SubmissionStatus, { label: string; badge: string }> = {
  PENDING_REVIEW: { label: 'Por revisar', badge: 'badge-blue' },
  APPROVED: { label: 'Aprobada', badge: 'badge-green' },
  OBSERVED: { label: 'Observada', badge: 'badge-red' },
};

export const ESTADO_CONDICION: Record<ConditionStatus, { label: string; badge: string; icon: string }> = {
  MET: { label: 'Cumple', badge: 'badge-green', icon: 'bi bi-check-circle-fill' },
  NOT_MET: { label: 'No cumple', badge: 'badge-red', icon: 'bi bi-x-circle-fill' },
  PENDING: { label: 'Pendiente', badge: 'badge-gray', icon: 'bi bi-hourglass-split' },
};

export const DISPONIBILIDAD_FUENTE: Record<SourceAvailability, { label: string; badge: string; ayuda: string }> = {
  PROVIDED_BY_FACILITY: {
    label: 'La sube la IPRESS', badge: 'badge-blue',
    ayuda: 'Documento o registro que la IPRESS debe cargar como evidencia.',
  },
  PREDEFINED: {
    label: 'Institucional', badge: 'badge-teal',
    ayuda: 'Documento institucional de EsSalud: ya está disponible y no se sube.',
  },
  NOT_IMPLEMENTED: {
    label: 'No implementada', badge: 'badge-gray',
    ayuda: 'EsSalud aún no ha emitido este documento: la fuente no se puede cumplir por ahora.',
  },
};

/** Semáforo de la clasificación (verde ≥ 85, amarillo 50–84, rojo < 50). */
export const COLOR_CLASIFICACION: Record<AccreditationClassification, string> = {
  APPROVED: 'verde',
  REMEDIATE_IN_6_MONTHS: 'amarillo',
  REMEDIATE_IN_9_MONTHS: 'amarillo',
  NOT_APPROVED_RESTART: 'rojo',
};

export function semaforo(porcentaje: number | null | undefined): 'verde' | 'amarillo' | 'rojo' {
  const p = porcentaje ?? 0;
  if (p >= 85) return 'verde';
  if (p >= 50) return 'amarillo';
  return 'rojo';
}

/** Extensiones que acepta el core (FileTypeDetector); el tipo real se verifica allá con el contenido. */
export const ARCHIVOS_ACEPTADOS = '.pdf,.jpg,.jpeg,.png,.doc,.docx,.xls,.xlsx,.ppt,.pptx';
export const IMAGENES_ACEPTADAS = '.jpg,.jpeg,.png';
export const TAMANIO_MAXIMO_MB = 20;
export const ARCHIVOS_POR_ENVIO = 10;
