import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { SparkleIconComponent } from '../../../shared/components/sparkle-icon/sparkle-icon.component';
import { DecorativeCirclesComponent } from '../../../shared/components/decorative-circles/decorative-circles.component';
import { FormFieldComponent } from '../../../shared/components/form-field/form-field.component';
import { passwordsMatchValidator } from '../../../shared/validators/passwords-match.validator';

/**
 * Reset Password page.
 *
 * The reset token arrives as a query parameter (?token=...) from the link
 * sent by ForgotPasswordComponent's email. If it's missing, we show an
 * inline error rather than letting the user submit a request doomed to
 * fail server-side. Submits to POST /api/v1/auth/password-reset/confirm
 * (API-AUTH-08).
 */
@Component({
  selector: 'app-reset-password-page',
  standalone: true,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    SparkleIconComponent,
    DecorativeCirclesComponent,
    FormFieldComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './reset-password.component.html',
  styleUrl: './reset-password.component.scss',
})
export class ResetPasswordComponent implements OnInit {
  private readonly fb = inject(FormBuilder);

  readonly isSubmitting = signal(false);
  readonly isSubmitted = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly token = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group(
    {
      password: [
        '',
        [
          Validators.required,
          Validators.minLength(8),
          Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/),
        ],
      ],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: passwordsMatchValidator('password', 'confirmPassword') },
  );

  constructor(
    private readonly authService: AuthService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    const tokenParam = this.route.snapshot.queryParamMap.get('token');
    this.token.set(tokenParam);
    if (!tokenParam) {
      this.errorMessage.set(
        'This password reset link is invalid or has expired. Please request a new one.',
      );
    }
  }

  get passwordError(): string | null {
    const control = this.form.controls.password;
    if (!control.touched || control.valid) return null;
    if (control.hasError('required')) return 'Password is required.';
    if (control.hasError('minlength')) return 'Password must be at least 8 characters.';
    if (control.hasError('pattern')) {
      return 'Password must include an uppercase letter, a lowercase letter, and a number.';
    }
    return null;
  }

  get confirmPasswordError(): string | null {
    const control = this.form.controls.confirmPassword;
    if (!control.touched) return null;
    if (control.hasError('required')) return 'Please confirm your password.';
    if (this.form.hasError('passwordMismatch')) return 'Passwords do not match.';
    return null;
  }

  onSubmit(): void {
    this.errorMessage.set(null);
    const currentToken = this.token();

    if (!currentToken) {
      this.errorMessage.set(
        'This password reset link is invalid or has expired. Please request a new one.',
      );
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);
    const { password } = this.form.getRawValue();

    this.authService.confirmPasswordReset({ token: currentToken, newPassword: password }).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.isSubmitted.set(true);
      },
      error: (error: HttpErrorResponse) => {
        this.isSubmitting.set(false);
        if (error.status === 400) {
          this.errorMessage.set(
            'This password reset link is invalid or has expired. Please request a new one.',
          );
        } else if (error.status === 422) {
          this.errorMessage.set(
            'That password is too weak. Use at least 8 characters with an uppercase letter, a lowercase letter, and a number.',
          );
        } else {
          this.errorMessage.set('Something went wrong. Please try again later.');
        }
      },
    });
  }

  goToLogin(): void {
    this.router.navigate(['/login']);
  }
}
