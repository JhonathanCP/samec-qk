import { Injectable, signal } from '@angular/core';

export interface PromptField {
  key: string;
  label: string;
  defaultValue?: string;
  placeholder?: string;
  type?: 'text' | 'number' | 'select';
  /** Solo para type 'select'. Use un value vacío para representar "sin valor". */
  options?: { value: string; label: string }[];
  required?: boolean;
}

interface ModalState {
  visible: boolean;
  type: 'confirm' | 'success' | 'error' | 'form';
  title: string;
  message?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  fields?: PromptField[];
  id?: number;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  resolve?: (v: any) => void;
}

@Injectable({ providedIn: 'root' })
export class ModalService {
  readonly state = signal<ModalState>({ visible: false, type: 'confirm', title: '' });
  private seq = 0;

  confirm(message: string, title = 'Confirmar', confirmLabel = 'Confirmar', cancelLabel = 'Cancelar'): Promise<boolean> {
    return new Promise(resolve => {
      this.state.set({ visible: true, type: 'confirm', title, message, confirmLabel, cancelLabel, id: ++this.seq, resolve });
    });
  }

  form(title: string, fields: PromptField[], confirmLabel = 'Guardar'): Promise<Record<string, string> | null> {
    return new Promise(resolve => {
      this.state.set({ visible: true, type: 'form', title, fields, confirmLabel, cancelLabel: 'Cancelar', id: ++this.seq, resolve });
    });
  }

  /** Muestra el éxito con animación y se cierra sola; se puede esperar (await) antes de navegar. */
  success(message: string, title = 'Éxito', autoCloseMs = 1800): Promise<void> {
    const id = ++this.seq;
    return new Promise(resolve => {
      this.state.set({ visible: true, type: 'success', title, message, id, resolve: () => resolve() });
      if (autoCloseMs > 0) {
        setTimeout(() => {
          if (this.state().id === id && this.state().visible) this.respond(true);
        }, autoCloseMs);
      }
    });
  }

  error(message: string, title = 'Error'): void {
    this.state.set({ visible: true, type: 'error', title, message, id: ++this.seq });
  }

  respond(value: boolean | Record<string, string> | null = false): void {
    this.state().resolve?.(value);
    this.state.set({ visible: false, type: 'confirm', title: '' });
  }
}
