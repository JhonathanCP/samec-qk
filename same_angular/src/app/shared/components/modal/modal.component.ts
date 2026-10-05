import { Component, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ModalService } from '../../../core/services/modal.service';

@Component({
  selector: 'app-modal',
    imports: [CommonModule],
  template: `
    @if (modal.state().visible) {
      <div class="overlay" (click)="onOverlay($event)">
        <div class="modal-box" role="dialog">

          <div class="modal-icon" [ngClass]="modal.state().type">
            @if (modal.state().type === 'confirm') { <i class="bi bi-exclamation-triangle-fill"></i> }
            @if (modal.state().type === 'success') { <i class="bi bi-check-circle-fill"></i> }
            @if (modal.state().type === 'error')   { <i class="bi bi-x-circle-fill"></i> }
            @if (modal.state().type === 'form')    { <i class="bi bi-pencil-square"></i> }
          </div>

          <h3 class="modal-title">{{ modal.state().title }}</h3>

          @if (modal.state().message) {
            <p class="modal-message">{{ modal.state().message }}</p>
          }

          @if (modal.state().type === 'form') {
            <div class="form-fields">
              @for (field of modal.state().fields; track field.key) {
                <div class="field-group">
                  <label class="field-label">
                    {{ field.label }}
                    @if (field.required) { <span class="required">*</span> }
                  </label>
                  @if (field.type === 'select') {
                    <select
                      class="field-input"
                      [value]="formValues[field.key] ?? ''"
                      (change)="formValues[field.key] = $any($event.target).value"
                    >
                      @for (opt of field.options ?? []; track opt.value) {
                        <option [value]="opt.value" [selected]="(formValues[field.key] ?? '') === opt.value">{{ opt.label }}</option>
                      }
                    </select>
                  } @else {
                    <input
                      class="field-input"
                      [type]="field.type || 'text'"
                      [placeholder]="field.placeholder ?? ''"
                      [value]="formValues[field.key] ?? ''"
                      (input)="formValues[field.key] = $any($event.target).value"
                    />
                  }
                </div>
              }
            </div>
          }

          <div class="modal-actions">
            @if (modal.state().type === 'confirm') {
              <button class="btn-cancel" (click)="modal.respond(false)">
                {{ modal.state().cancelLabel ?? 'Cancelar' }}
              </button>
              <button class="btn-confirm" (click)="modal.respond(true)">
                {{ modal.state().confirmLabel ?? 'Confirmar' }}
              </button>
            } @else if (modal.state().type === 'form') {
              <button class="btn-cancel" (click)="modal.respond(null)">Cancelar</button>
              <button class="btn-confirm" [disabled]="!formValid()" (click)="submitForm()">
                {{ modal.state().confirmLabel ?? 'Guardar' }}
              </button>
            } @else {
              <button class="btn-ok" (click)="modal.respond(true)">Aceptar</button>
            }
          </div>

        </div>
      </div>
    }
  `,
  styles: [`
    .overlay {
      position: fixed; inset: 0; background: rgba(0,0,0,.45);
      display: flex; align-items: center; justify-content: center;
      z-index: 9999; animation: fadeIn .15s ease;
    }
    @keyframes fadeIn { from { opacity: 0 } to { opacity: 1 } }

    .modal-box {
      background: #fff; border-radius: 16px; padding: 36px 32px 28px;
      max-width: 440px; width: 90%; box-shadow: 0 20px 60px rgba(0,0,0,.2);
      text-align: center; animation: slideUp .18s ease;
    }
    @keyframes slideUp { from { transform: translateY(12px); opacity: 0 } to { transform: translateY(0); opacity: 1 } }

    .modal-icon { font-size: 48px; margin-bottom: 16px; display: flex; align-items: center; justify-content: center; animation: pop .3s ease; }
    @keyframes pop { from { transform: scale(.6); opacity: 0 } to { transform: scale(1); opacity: 1 } }
    .modal-icon.confirm { color: #f59e0b; }
    .modal-icon.success { color: #22c55e; }
    .modal-icon.error   { color: #ef4444; }
    .modal-icon.form    { color: #00579e; }

    .modal-title   { font-size: 18px; font-weight: 700; color: #111; margin: 0 0 10px; }
    .modal-message { font-size: 14px; color: #555; margin: 0 0 20px; line-height: 1.5; }

    .form-fields { text-align: left; margin-bottom: 24px; display: flex; flex-direction: column; gap: 14px; }
    .field-group { display: flex; flex-direction: column; gap: 5px; }
    .field-label { font-size: 13px; font-weight: 600; color: #374151; }
    .required { color: #ef4444; margin-left: 2px; }
    .field-input {
      padding: 8px 12px; border: 1.5px solid #d1d5db; border-radius: 8px;
      font-size: 14px; color: #111; outline: none; transition: border-color .15s;
    }
    .field-input:focus { border-color: #00579e; }

    .modal-actions { display: flex; gap: 10px; justify-content: center; }

    button { padding: 9px 24px; border-radius: 8px; font-weight: 600; font-size: 14px; cursor: pointer; border: none; transition: all .15s; }
    button:disabled { opacity: .45; cursor: not-allowed; }
    .btn-cancel  { background: #f1f5f9; color: #475569; }
    .btn-cancel:hover  { background: #e2e8f0; }
    .btn-confirm { background: #00579e; color: #fff; }
    .btn-confirm:hover:not(:disabled) { background: #004080; }
    .btn-ok      { background: #00579e; color: #fff; min-width: 100px; }
    .btn-ok:hover { background: #004080; }
  `],
})
export class ModalComponent {
  readonly modal = inject(ModalService);
  formValues: Record<string, string | undefined> = {};

  constructor() {
    effect(() => {
      const state = this.modal.state();
      if (state.visible && state.type === 'form') {
        this.formValues = {};
        for (const f of state.fields ?? []) {
          this.formValues[f.key] = f.defaultValue ?? '';
        }
      }
    });
  }

  formValid(): boolean {
    return (this.modal.state().fields ?? [])
      .filter(f => f.required)
      .every(f => (this.formValues[f.key] ?? '').trim().length > 0);
  }

  submitForm(): void {
    if (!this.formValid()) return;
    this.modal.respond({ ...this.formValues } as Record<string, string>);
  }

  onOverlay(e: MouseEvent): void {
    if ((e.target as HTMLElement).classList.contains('overlay')) {
      if (this.modal.state().type === 'success' || this.modal.state().type === 'error') {
        this.modal.respond(false);
      }
    }
  }
}
