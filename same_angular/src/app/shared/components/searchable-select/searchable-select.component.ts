import {
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  ViewChild,
  forwardRef,
  HostListener,
} from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';
import { CommonModule } from '@angular/common';

export interface SelectOption {
  value: string | number;
  label: string;
}

@Component({
  selector: 'app-select',
  imports: [CommonModule, FormsModule],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => SearchableSelectComponent),
      multi: true,
    },
  ],
  template: `
    <div class="sel-wrap" [class.disabled]="disabled" [class.open]="isOpen">
      <div class="sel-trigger" (click)="handleTriggerClick()">
        <span class="sel-label" *ngIf="!isOpen" [class.placeholder]="!selectedLabel">
          {{ selectedLabel || placeholder }}
        </span>
        <input
          *ngIf="isOpen"
          #searchInput
          class="sel-search"
          [(ngModel)]="query"
          [placeholder]="placeholder"
          (keydown.escape)="close()"
          (keydown.enter)="selectFirst()"
        />
        <span class="sel-chevron">&#8964;</span>
      </div>
      <ul class="sel-dropdown" *ngIf="isOpen">
        <li
          *ngFor="let opt of filtered"
          class="sel-option"
          [class.active]="isActive(opt)"
          (mousedown)="select(opt)"
        >
          {{ opt.label }}
        </li>
        <li *ngIf="filtered.length === 0" class="sel-empty">Sin resultados</li>
      </ul>
    </div>
  `,
  styles: [`
    :host { display: block; position: relative; }
    .sel-wrap { position: relative; font-size: 14px; }
    .sel-wrap.disabled { opacity: 0.6; pointer-events: none; }
    .sel-trigger {
      display: flex; align-items: center; gap: 6px;
      padding: 10px 12px; border: 1px solid #dbe4ee;
      border-radius: 8px; background: #fff; cursor: pointer;
      min-height: 40px; box-sizing: border-box;
    }
    .sel-wrap.open .sel-trigger { border-color: #3b82f6; box-shadow: 0 0 0 2px rgba(59,130,246,.15); }
    .sel-label { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #1e293b; }
    .sel-label.placeholder { color: #94a3b8; }
    .sel-search { flex: 1; width: 0; border: none; outline: none; font-size: 14px; background: transparent; min-width: 0; color: #1e293b; }
    .sel-chevron { font-size: 16px; color: #94a3b8; flex-shrink: 0; line-height: 1; }
    .sel-dropdown {
      position: absolute; top: calc(100% + 4px); left: 0; right: 0;
      background: #fff; border: 1px solid #dbe4ee; border-radius: 8px;
      box-shadow: 0 4px 16px rgba(0,0,0,.1); z-index: 9999;
      max-height: 240px; overflow-y: auto; padding: 4px 0; margin: 0; list-style: none;
    }
    .sel-option { padding: 9px 14px; cursor: pointer; color: #1e293b; }
    .sel-option:hover { background: #f0f9ff; }
    .sel-option.active { background: #eff6ff; color: #1d4ed8; font-weight: 600; }
    .sel-empty { padding: 9px 14px; color: #94a3b8; font-style: italic; }
  `],
})
export class SearchableSelectComponent implements ControlValueAccessor, OnChanges {
  @Input() options: SelectOption[] = [];
  @Input() placeholder = '-- Seleccione --';
  @Output() selChange = new EventEmitter<string | number | null>();

  @ViewChild('searchInput') searchInputRef?: ElementRef<HTMLInputElement>;

  isOpen = false;
  query = '';
  value: string | number | null = null;
  disabled = false;

  private _onChange: (v: any) => void = () => {};
  private _onTouched: () => void = () => {};

  constructor(private elRef: ElementRef) {}

  ngOnChanges(): void {}

  get selectedLabel(): string {
    if (this.value === null || this.value === '' || this.value === undefined) return '';
    const opt = this.options.find((o) => String(o.value) === String(this.value));
    return opt?.label ?? '';
  }

  get filtered(): SelectOption[] {
    const q = this.query.trim().toLowerCase();
    if (!q) return this.options;
    return this.options.filter((o) => o.label.toLowerCase().includes(q));
  }

  isActive(opt: SelectOption): boolean {
    return this.value !== null && String(opt.value) === String(this.value);
  }

  handleTriggerClick(): void {
    if (this.disabled) return;
    if (this.isOpen) {
      this.close();
    } else {
      this.isOpen = true;
      this.query = '';
      setTimeout(() => this.searchInputRef?.nativeElement?.focus(), 0);
    }
  }

  close(): void {
    this.isOpen = false;
    this.query = '';
  }

  select(opt: SelectOption): void {
    this.value = opt.value;
    this.close();
    this._onChange(opt.value);
    this._onTouched();
    this.selChange.emit(opt.value);
  }

  selectFirst(): void {
    if (this.filtered.length > 0) {
      this.select(this.filtered[0]);
    }
  }

  @HostListener('document:click', ['$event.target'])
  onDocumentClick(target: EventTarget | null): void {
    if (!this.elRef.nativeElement.contains(target as Node | null)) {
      this.close();
    }
  }

  writeValue(value: any): void {
    this.value = value ?? null;
  }

  registerOnChange(fn: any): void { this._onChange = fn; }
  registerOnTouched(fn: any): void { this._onTouched = fn; }
  setDisabledState(isDisabled: boolean): void { this.disabled = isDisabled; }
}
