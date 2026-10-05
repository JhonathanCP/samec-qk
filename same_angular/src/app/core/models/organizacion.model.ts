export type NivelComplejidad = 'I' | 'II' | 'III';

export interface MacroRegion {
  id: number;
  codigo: string;
  nombre: string;
  activo: boolean;
}

export interface Red {
  id: number;
  codigo: string;
  nombre: string;
  descripcion: string | null;
  idMacroRegion: number | null;
  macroRegionNombre: string | null;
  activo: boolean;
}

export interface Ipress {
  id: number;
  codigo: string;
  nombre: string;
  idRed: number;
  redNombre: string;
  nivelComplejidad: NivelComplejidad | null;
  activo: boolean;
}
