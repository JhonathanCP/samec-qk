import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { Ipress, MacroRegion, Red } from '../models/organizacion.model';
import { ApiService } from './api.service';

/** Catálogos de MacroRegiones, Redes e IPRESS (cambian poco: se cachean en memoria). */
@Injectable({ providedIn: 'root' })
export class OrganizacionService {
  private readonly api = inject(ApiService);
  private redes$?: Observable<Red[]>;
  private ipress$?: Observable<Ipress[]>;

  macroRegiones(): Observable<MacroRegion[]> {
    return this.api.get<MacroRegion[]>('/macroregiones');
  }

  /** Redes activas. */
  redes(): Observable<Red[]> {
    return (this.redes$ ??= this.api.get<Red[]>('/redes').pipe(shareReplay(1)));
  }

  /** Todas las IPRESS activas; se filtran por idRed en el componente. */
  ipress(): Observable<Ipress[]> {
    return (this.ipress$ ??= this.api.get<Ipress[]>('/ipress').pipe(shareReplay(1)));
  }
}
