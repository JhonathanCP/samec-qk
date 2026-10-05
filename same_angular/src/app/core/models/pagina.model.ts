/** Página de resultados (shared/paginacion/Pagina.java en el core). */
export interface Pagina<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}
