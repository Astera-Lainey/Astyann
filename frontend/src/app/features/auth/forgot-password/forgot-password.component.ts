import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { SparkleIconComponent } from '../../../shared/components/sparkle-icon/sparkle-icon.component';
import { DecorativeCirclesComponent } from '../../../shared/components/decorative-circles/decorative-circles.component';
import { FormFieldComponent } from '../../../shared/components/form-field/form-field.component';

/**
 * Forgot Password page.
 *
 * Submits the email to POST /api/v1/auth/reset-password (API-AUTH-07 — yes,
 * "reset-password" is the *request* endpoint per the contract; confirmation
 * happens separately at /auth/password-reset/confirm, wired in
 * ResetPasswordComponent). Per the contract this endpoint always returns
 * 200 OK, even for an unknown email, specifically to prevent account
 * enumeration — so we show the same "check your email" confirmation
 * regardless of whether the address exists in the system.
 */
@Component({
  selector: 'app-forgot-password-page',
  standalone: true,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    SparkleIconComponent,
    DecorativeCirclesComponent,
    FormFieldComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.scss',
})
export class ForgotPasswordComponent {
  private readonly fb = inject(FormBuilder);

  readonly isSubmitting = signal(false);
  readonly isSubmitted = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  constructor(
    private readonly authService: AuthService,
  ) {}

  get emailError(): string | null {
    const control = this.form.controls.email;
    if (!control.touched || control.valid) return null;
    if (control.hasError('required')) return 'Email is required.';
    if (control.hasError('email')) return 'Enter a valid email address.';
    return null;
  }

  onSubmit(): void {
    this.errorMessage.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);
    const { email } = this.form.getRawValue();

    this.authService.requestPasswordReset({ email }).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.isSubmitted.set(true);
      },
      error: (error: HttpErrorResponse) => {
        this.isSubmitting.set(false);
        if (error.status === 429) {
          this.errorMessage.set('Too many requests. Please wait a moment and try again.');
        } else {
          // Deliberately do not reveal whether the email exists.
          this.isSubmitted.set(true);
        }
      },
    });
  }
}
