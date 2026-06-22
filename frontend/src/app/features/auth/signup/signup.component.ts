import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { SparkleIconComponent } from '../../../shared/components/sparkle-icon/sparkle-icon.component';
import { DecorativeCirclesComponent } from '../../../shared/components/decorative-circles/decorative-circles.component';
import { FormFieldComponent } from '../../../shared/components/form-field/form-field.component';

/**
 * Signup page.
 *
 * Wires real Reactive Forms validation and a real POST to
 * /api/v1/auth/register (API-AUTH-01). Per the API Contract, the account is
 * created with `isVerified: false` and a verification email is sent
 * immediately. On success we navigate to /verify-email, carrying the
 * returned `userId` as a query param, so the user can enter the 6-digit
 * code before they're able to log in (login returns 403 Forbidden for
 * unverified accounts — API-AUTH-04).
 */
@Component({
  selector: 'app-signup-page',
  standalone: true,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    SparkleIconComponent,
    DecorativeCirclesComponent,
    FormFieldComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './signup.component.html',
  styleUrl: './signup.component.scss',
})
export class SignupComponent {
  private readonly fb = inject(FormBuilder);

  readonly isSubmitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: [
      '',
      [
        Validators.required,
        Validators.minLength(8),
        Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/),
      ],
    ],
  });

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly toastService: ToastService,
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
    if (control.hasError('pattern')) {
      return 'Password must include an uppercase letter, a lowercase letter, and a number.';
    }
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

    this.authService.register({ email, password }).subscribe({
      next: (data) => {
        this.isSubmitting.set(false);
        this.toastService.show('Account created successfully. Check your email for the verification code.');
        this.router.navigate(['/verify-email'], {
          queryParams: {
            userId: data?.userId ?? '',
            email: data?.email ?? email,
          },
        });
      },
      error: (error: HttpErrorResponse) => {
        this.isSubmitting.set(false);
        if (error.status === 409) {
          this.errorMessage.set('An account with this email already exists.');
        } else if (error.status === 400) {
          this.errorMessage.set('Please check your details and try again.');
        } else {
          this.errorMessage.set('Something went wrong. Please try again later.');
        }
      },
    });
  }
}
