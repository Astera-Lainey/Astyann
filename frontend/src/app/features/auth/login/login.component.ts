import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { SparkleIconComponent } from '../../../shared/components/sparkle-icon/sparkle-icon.component';
import { DecorativeCirclesComponent } from '../../../shared/components/decorative-circles/decorative-circles.component';
import { FormFieldComponent } from '../../../shared/components/form-field/form-field.component';

/**
 * Login page.
 *
 * Reactive Forms validation, a real POST to /api/v1/auth/login
 * (API-AUTH-04) via AuthService, a generic "invalid credentials" message on
 * 401 (never revealing whether the email exists), a dedicated message and
 * redirect to /verify-email on 403 (account exists but isn't verified yet —
 * contract: "403 Forbidden (account not verified)"), a pending/loading
 * state that disables the submit button, and a redirect to the dashboard on
 * success.
 */
@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    SparkleIconComponent,
    DecorativeCirclesComponent,
    FormFieldComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);

  readonly isSubmitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router,
  ) {}

  get emailError(): string | null {
    const control = this.form.controls.email;
    if (!control.touched || control.valid) return null;
    if (control.hasError('required')) return 'Email is required.';
    if (control.hasError('email')) return 'Enter a valid email address.';
    return null;
  }

  get passwordError(): string | null {
    const control = this.form.controls.password;
    if (!control.touched || control.valid) return null;
    if (control.hasError('required')) return 'Password is required.';
    if (control.hasError('minlength')) return 'Password must be at least 8 characters.';
    return null;
  }

  onSubmit(): void {
    this.errorMessage.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);
    const { email, password } = this.form.getRawValue();

    this.authService.login({ email, password }).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.router.navigate(['/app/dashboard']);
      },
      error: (error: HttpErrorResponse) => {
        this.isSubmitting.set(false);
        if (error.status === 403) {
          // Account exists but hasn't been verified yet (API-AUTH-04: "403
          // Forbidden — account not verified"). Send them to finish
          // verification instead of leaving them stuck on a login error.
          const userId = (error.error as { userId?: string })?.userId ?? '';
          this.router.navigate(['/verify-email'], {
            queryParams: { email, ...(userId && { userId }) },
          });
        } else if (error.status === 401 || error.status === 400) {
          // Generic message regardless of the underlying reason (wrong email
          // vs wrong password) so we never leak which part was incorrect.
          this.errorMessage.set('Invalid email or password. Please try again.');
        } else {
          this.errorMessage.set('Something went wrong. Please try again later.');
        }
      },
    });
  }
}
