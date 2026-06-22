import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR, ReactiveFormsModule } from '@angular/forms';

/**
 * Labeled text input matching the local `Field({ label, ...props })` helper
 * duplicated in login.tsx and signup.tsx. Designed to be used as a plain
 * presentational wrapper around a reactive-forms-bound <input>; the parent
 * component owns the FormControl and passes errors/touched state in.
 */
@Component({
  selector: 'app-form-field',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <label class="field">
      <span class="field__label">{{ label }}</span>
      <ng-content></ng-content>
      @if (errorMessage) {
        <span class="field__error">{{ errorMessage }}</span>
      }
    </label>
  `,
  styleUrl: './form-field.component.scss',
})
export class FormFieldComponent {
  @Input({ required: true }) label!: string;
  @Input() errorMessage: string | null = null;
}
