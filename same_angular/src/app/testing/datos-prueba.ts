import { Permission } from '../core/constants/permissions';
import {
  Autoevaluacion, Checklist, Evidencia, NodoCondicion, NodoCriterio, NodoFuente, Resultado, SubmissionStatus,
} from '../core/models/autoevaluacion.model';
import { RolCodigo, UsuarioSesion } from '../core/models/sesion.model';

/** Datos de prueba de los tests del front (mismos permisos que asigna Permisos.java en el core). */

const PERMISOS: Record<'admin' | 'evaluador' | 'evaluado', { rol: RolCodigo; permisos: Permission[] }> = {
  admin: {
    rol: 'ADMIN_SISTEMA',
    permisos: ['usuarios.crud', 'catalogos.crud', 'accesos.ver', 'autoevaluacion.ver', 'autoevaluacion.gestionar'],
  },
  evaluador: { rol: 'EVALUADOR', permisos: ['autoevaluacion.ver', 'autoevaluacion.gestionar', 'evidencias.evaluar'] },
  evaluado: { rol: 'EVALUADO', permisos: ['autoevaluacion.ver', 'evidencias.registrar'] },
};

export function usuario(tipo: keyof typeof PERMISOS): UsuarioSesion {
  const { rol, permisos } = PERMISOS[tipo];
  const nacional = tipo === 'admin';
  return {
    id: 1, username: tipo, nombre: 'Juan', apellido: 'Pérez', email: `${tipo}@essalud.gob.pe`, roles: [rol], permisos,
    alcance: nacional ? 'NACIONAL' : 'IPRESS', idRed: nacional ? null : 7, redNombre: nacional ? null : 'RED REBAGLIATI',
    idIpress: nacional ? null : 1, ipressNombre: nacional ? null : 'H.N. EDGARDO REBAGLIATI MARTINS',
  };
}

export function autoevaluacion(cambios: Partial<Autoevaluacion> = {}): Autoevaluacion {
  return {
    id: 5, facilityId: 1, facilityCode: '001', facilityName: 'H.N. EDGARDO REBAGLIATI MARTINS', periodYear: 2026,
    facilityCategory: 'II_1', status: 'OPEN', finalScore: null, classification: null, classificationLabel: null,
    closedAt: null, closedByName: null, evidenceLocked: false, evidenceLockedAt: null, evidenceLockedByName: null,
    ...cambios,
  };
}

export function evidencia(id: number, status: SubmissionStatus): Evidencia {
  return {
    id, selfAssessmentId: 5, auditSourceId: id, auditSourceName: 'Fuente', criterionCode: 'DIR1-1', externalUrl: null,
    notes: null, status, submittedByName: 'Roberto Huanca', submittedAt: '2026-10-05T08:00:00', files: [], reviews: [],
  };
}

/** Fuente que sube la IPRESS (con su evidencia, si se indica el estado). */
export function fuente(id: number, estado?: SubmissionStatus): NodoFuente {
  return {
    id, name: `Fuente ${id}`, referenceUrl: null, availability: 'PROVIDED_BY_FACILITY',
    status: estado === 'APPROVED' ? 'MET' : estado === 'OBSERVED' ? 'NOT_MET' : 'PENDING',
    evidence: estado ? evidencia(id, estado) : null,
  };
}

export function condicion(id: number, cambios: Partial<NodoCondicion> = {}): NodoCondicion {
  return {
    id, description: `Condición ${id}`, scoreWeight: 1, technique: 'DOCUMENT_REVIEW', sourceLogic: null,
    facilityFunction: null, responsibleRole: null, verificationGuide: null, status: 'PENDING', normativeReferences: [],
    sources: [], review: null, ...cambios,
  };
}

export function criterio(id: number, code: string, condiciones: NodoCondicion[], puntaje?: number): NodoCriterio {
  return {
    id, code, statement: `Criterio ${code}`, type: 'STRUCTURE', scoreLevels: [], suggestedScore: null,
    score: puntaje === undefined ? null : {
      id: id * 10, selfAssessmentId: 5, criterionId: id, score: puntaje, origin: 'MANUAL', justification: null,
      scoredByName: 'Juan Pérez', scoredAt: '2026-10-05T09:00:00',
    },
    conditions: condiciones,
  };
}

/**
 * Checklist con dos macroprocesos:
 * DIR (DIR1-1: una fuente aprobada y otra por revisar, puntaje 2; DIR1-2: una entrevista sin verificar)
 * y GRH (GRH1-1: una fuente observada, puntaje 0).
 */
export function checklist(a: Autoevaluacion = autoevaluacion()): Checklist {
  return {
    assessment: a,
    progress: {
      criteria: 3, criteriaScored: 2, sourcesRequiringEvidence: 3, sourcesSubmitted: 3, sourcesPendingReview: 1,
      sourcesApproved: 1, sourcesObserved: 1, onSiteConditions: 1, onSiteConditionsReviewed: 0,
    },
    macroProcesses: [
      {
        id: 1, code: 'DIR', name: 'Direccionamiento', category: 'MANAGEMENT',
        standards: [{
          id: 11, code: 'DIR-1', statement: 'Planeamiento',
          criteria: [
            criterio(101, 'DIR1-1', [condicion(1001, { sources: [fuente(1, 'APPROVED'), fuente(2, 'PENDING_REVIEW')] })], 2),
            criterio(102, 'DIR1-2', [condicion(1002, { technique: 'INTERVIEW', scoreWeight: 2 })]),
          ],
        }],
      },
      {
        id: 2, code: 'GRH', name: 'Gestión de recursos humanos', category: 'MANAGEMENT',
        standards: [{
          id: 21, code: 'GRH-1', statement: 'Personal',
          criteria: [criterio(201, 'GRH1-1', [condicion(2001, { scoreWeight: 2, sources: [fuente(3, 'OBSERVED')] })], 0)],
        }],
      },
    ],
  };
}

export function resultado(a: Autoevaluacion = autoevaluacion()): Resultado {
  return {
    assessment: a, complete: false, criteriaCount: 3, scoredCount: 2, finalScore: 40, classification: 'NOT_APPROVED_RESTART',
    classificationLabel: 'No aprobado: reiniciar', categories: [], macroProcesses: [],
  };
}
