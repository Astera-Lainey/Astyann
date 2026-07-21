import { ChangeDetectionStrategy, Component, Input, forwardRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';

@Component({
  selector: 'ast-input',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ast-input.component.html',
  styleUrls: ['./ast-input.component.scss'],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => AstInputComponent), multi: true }],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AstInputComponent implements ControlValueAccessor {
  @Input() label = '';
  @Input() type: string = 'text';
  @Input() placeholder = '';
  @Input() required = false;
  @Input() disabled = false;
  @Input() errorMessage: string | null = null;

  value: any = null;
  private onChange: (value: any) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(value: any): void { this.value = value; }
  registerOnChange(fn: (value: any) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }
  setDisabledState(isDisabled: boolean): void { this.disabled = isDisabled; }

  handleInput(event: Event): void {
    const target = event.target as HTMLInputElement;
    const raw = this.type === 'checkbox' ? target.checked : target.value;
    this.value = raw;
    this.onChange(raw);
  }

  handleBlur(): void { this.onTouched(); }
}
