/**
 * Modelos de la autoevaluación LEA: reflejan las vistas del core
 * (assessment/application/port/in/AssessmentViews.java). Los nombres de campo vienen en inglés
 * porque así los publica la API.
 */

export type FacilityCategory = 'I_1' | 'I_2' | 'I_3' | 'I_4' | 'II_1' | 'II_2' | 'III_1';
export type AssessmentStatus = 'OPEN' | 'CLOSED';
export type SubmissionStatus = 'PENDING_REVIEW' | 'APPROVED' | 'OBSERVED';
export type ReviewVerdict = 'APPROVED' | 'OBSERVED';
export type ConditionStatus = 'MET' | 'NOT_MET' | 'PENDING';
export type CriterionType = 'STRUCTURE' | 'PROCESS' | 'RESULT';
export type EvaluationTechnique = 'DOCUMENT_REVIEW' | 'OBSERVATION' | 'SAMPLING_MEDICAL_RECORD_AUDIT' | 'INTERVIEW';
export type SourceAvailability = 'PROVIDED_BY_FACILITY' | 'PREDEFINED' | 'NOT_IMPLEMENTED';
export type SourceLogic = 'ALL' | 'ANY';
export type MacroProcessCategory = 'MANAGEMENT' | 'CARE_PROVISION' | 'SUPPORT';
export type ScoreOrigin = 'PRE_SCORED' | 'MANUAL';
export type AccreditationClassification =
  | 'NOT_APPROVED_RESTART'
  | 'REMEDIATE_IN_9_MONTHS'
  | 'REMEDIATE_IN_6_MONTHS'
  | 'APPROVED';

export interface ArchivoVista {
  id: number;
  name: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
}

export interface Autoevaluacion {
  id: number;
  facilityId: number;
  facilityCode: string | null;
  facilityName: string | null;
  periodYear: number;
  facilityCategory: FacilityCategory;
  status: AssessmentStatus;
  finalScore: number | null;
  classification: AccreditationClassification | null;
  classificationLabel: string | null;
  closedAt: string | null;
  closedByName: string | null;
  /** Carga documental del evaluado cerrada por el evaluador (candado). */
  evidenceLocked: boolean;
  evidenceLockedAt: string | null;
  evidenceLockedByName: string | null;
}

export interface RevisionEvidencia {
  id: number;
  verdict: ReviewVerdict;
  observationTypeId: number | null;
  observationTypeName: string | null;
  comment: string | null;
  reviewedByName: string | null;
  reviewedAt: string;
}

export interface Evidencia {
  id: number;
  selfAssessmentId: number;
  auditSourceId: number;
  auditSourceName: string | null;
  criterionCode: string | null;
  externalUrl: string | null;
  notes: string | null;
  status: SubmissionStatus;
  submittedByName: string | null;
  submittedAt: string;
  files: ArchivoVista[];
  reviews: RevisionEvidencia[];
}

export interface VerificacionCondicion {
  id: number;
  selfAssessmentId: number;
  conditionId: number;
  met: boolean;
  comment: string | null;
  attachments: ArchivoVista[];
  reviewedByName: string | null;
  reviewedAt: string;
}

export interface PuntajeCriterio {
  id: number;
  selfAssessmentId: number;
  criterionId: number;
  score: number;
  origin: ScoreOrigin;
  justification: string | null;
  scoredByName: string | null;
  scoredAt: string;
}

export interface Progreso {
  criteria: number;
  criteriaScored: number;
  sourcesRequiringEvidence: number;
  sourcesSubmitted: number;
  sourcesPendingReview: number;
  sourcesApproved: number;
  sourcesObserved: number;
  onSiteConditions: number;
  onSiteConditionsReviewed: number;
}

export interface ReferenciaNormativa {
  documentId: number;
  title: string | null;
  url: string | null;
  applicableExcerpt: string | null;
}

export interface NodoFuente {
  id: number;
  name: string;
  referenceUrl: string | null;
  availability: SourceAvailability;
  status: ConditionStatus;
  evidence: Evidencia | null;
}

export interface NodoCondicion {
  id: number;
  description: string;
  scoreWeight: number;
  technique: EvaluationTechnique;
  sourceLogic: SourceLogic | null;
  facilityFunction: string | null;
  responsibleRole: string | null;
  verificationGuide: string | null;
  status: ConditionStatus;
  normativeReferences: ReferenciaNormativa[];
  sources: NodoFuente[];
  review: VerificacionCondicion | null;
}

export interface NivelPuntaje {
  score: number;
  description: string;
}

export interface NodoCriterio {
  id: number;
  code: string;
  statement: string;
  type: CriterionType;
  scoreLevels: NivelPuntaje[];
  suggestedScore: number | null;
  score: PuntajeCriterio | null;
  conditions: NodoCondicion[];
}

export interface NodoEstandar {
  id: number;
  code: string;
  statement: string;
  criteria: NodoCriterio[];
}

export interface NodoMacroproceso {
  id: number;
  code: string;
  name: string;
  category: MacroProcessCategory;
  standards: NodoEstandar[];
}

export interface Checklist {
  assessment: Autoevaluacion;
  progress: Progreso;
  macroProcesses: NodoMacroproceso[];
}

export interface ResultadoMacroproceso {
  macroProcessId: number;
  code: string;
  name: string;
  category: MacroProcessCategory;
  configuredWeight: number;
  effectiveWeight: number;
  criteriaCount: number;
  scoredCount: number;
  weightedPoints: number;
  weightedMaximum: number;
  percentage: number;
}

export interface ResultadoCategoria {
  category: MacroProcessCategory;
  weight: number;
  percentage: number;
}

export interface Resultado {
  assessment: Autoevaluacion;
  complete: boolean;
  criteriaCount: number;
  scoredCount: number;
  finalScore: number;
  classification: AccreditationClassification;
  classificationLabel: string;
  categories: ResultadoCategoria[];
  macroProcesses: ResultadoMacroproceso[];
}

export interface TipoObservacion {
  id: number;
  code: string;
  name: string;
  active: boolean;
}
