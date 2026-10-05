/**
 * Catálogo de acreditación (macroprocesos, estándares, criterios, procesos institucionales,
 * normativa). Reflejan el JSON de /catalog/... en el core.
 */
import {
  CriterionType, EvaluationTechnique, FacilityCategory, MacroProcessCategory, SourceAvailability, SourceLogic,
} from './autoevaluacion.model';

export interface MacroProceso {
  id: number | null;
  code: string;
  name: string;
  category: MacroProcessCategory;
  scoreWeight: number;
  displayOrder: number;
}

export interface Estandar {
  id: number | null;
  macroProcessId: number;
  code: string;
  statement: string;
}

export interface Fuente {
  id: number | null;
  name: string;
  referenceUrl: string | null;
  availability: SourceAvailability;
  appliesSystemWide: boolean;
  displayOrder: number;
}

export interface ReferenciaNorma {
  documentId: number | null;
  applicableExcerpt: string | null;
}

export interface Condicion {
  id: number | null;
  description: string;
  scoreWeight: number;
  technique: EvaluationTechnique;
  institutionalProcessId: number | null;
  facilityFunction: string | null;
  responsibleRole: string | null;
  sourceLogic: SourceLogic | null;
  verificationGuide: string | null;
  normativeReferences: ReferenciaNorma[];
  sources: Fuente[];
  displayOrder: number;
}

export interface Criterio {
  id: number | null;
  standardId: number;
  code: string;
  statement: string;
  type: CriterionType;
  applicableCategories: FacilityCategory[];
  scoreLevels: { score: number; description: string }[];
  conditions: Condicion[];
}

export interface ProcesoInstitucional {
  id: number | null;
  parentId: number | null;
  name: string;
}

export interface DocumentoNormativo {
  id: number | null;
  title: string;
  url: string | null;
}

export interface TipoObservacionCat {
  id: number | null;
  code: string;
  name: string;
  active: boolean;
}

/** Dónde se usa un proceso o una norma. */
export interface Uso {
  criterionId: number;
  criterionCode: string;
  standardId: number;
  conditionId: number;
  conditionDescription: string;
}

// ── Árbol liviano (/catalog/tree) ──

export interface ResumenCriterio {
  id: number;
  code: string;
  statement: string;
  type: CriterionType;
  applicableCategories: FacilityCategory[];
  conditions: number;
  sourcesToUpload: number;
  onSiteConditions: number;
  weightTotal: number;
}

export interface NodoEstandarCat {
  id: number;
  code: string;
  statement: string;
  criteria: ResumenCriterio[];
}

export interface NodoMacroCat {
  id: number;
  code: string;
  name: string;
  category: MacroProcessCategory;
  scoreWeight: number;
  displayOrder: number;
  standards: NodoEstandarCat[];
}
