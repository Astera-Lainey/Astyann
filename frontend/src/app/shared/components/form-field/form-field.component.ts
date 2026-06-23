import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';

@Component({
  selector: 'app-form-field',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <label class="field">
      <span class="field__label">{{ label }}</span>
      <div class="field__input-wrapper">
        <ng-content></ng-content>
        @if (showPasswordToggle) {
          <button
            type="button"
            class="field__password-toggle"
            [attr.aria-label]="passwordVisible ? 'Hide password' : 'Show password'"
            (click)="togglePassword()"
            tabindex="-1"
          >
            @if (passwordVisible) {
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" />
                <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" />
                <line x1="1" y1="1" x2="23" y2="23" />
              </svg>
            } @else {
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                <circle cx="12" cy="12" r="3" />
              </svg>
            }
          </button>
        }
      </div>
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
  @Input() showPasswordToggle = false;
  @Input() passwordVisible = false;
  @Output() passwordVisibleChange = new EventEmitter<boolean>();

  togglePassword(): void {
    this.passwordVisibleChange.emit(!this.passwordVisible);
  }
}
