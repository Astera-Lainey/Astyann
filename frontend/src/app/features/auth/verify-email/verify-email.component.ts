import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  QueryList,
  signal,
  ViewChildren,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { SparkleIconComponent } from '../../../shared/components/sparkle-icon/sparkle-icon.component';
import { DecorativeCirclesComponent } from '../../../shared/components/decorative-circles/decorative-circles.component';

const DIGIT_COUNT = 6;

/**
 * Verify Email page.
 *
 * Lands here right after signup (POST /auth/register — API-AUTH-01 — always
 * creates the account as unverified and sends a code) and is also where
 * LoginComponent redirects a user who tries to log in before verifying
 * (login returns 403 Forbidden for unverified accounts — API-AUTH-04).
 *
 * Submits the 6-digit code to POST /api/v1/auth/verify-email (API-AUTH-02).
 * The `userId` and `email` arrive as query params — `userId` from the
 * register response, `email` as a fallback display value when only the
 * email is known (e.g. coming from the login redirect, which doesn't have
 * a userId on hand). If `userId` is missing, the code can't be verified
 * directly, so the user is prompted to request a fresh code by email
 * instead (POST /auth/verify/resend — API-AUTH-03), which re-issues a
 * `userId` indirectly by sending a new code to that inbox.
 */
@Component({
  selector: 'app-verify-email-page',
  standalone: true,
  imports: [
    RouterLink,
    SparkleIconComponent,
    DecorativeCirclesComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './verify-email.component.html',
  styleUrl: './verify-email.component.scss',
})
export class VerifyEmailComponent implements OnInit, OnDestroy, AfterViewInit {
  @ViewChildren('digitInput') digitInputs!: QueryList<ElementRef<HTMLInputElement>>;

  readonly isSubmitting = signal(false);
  readonly isVerified = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly isResending = signal(false);
  readonly resendMessage = signal<string | null>(null);
  readonly resendCooldown = signal(0);

  readonly userId = signal<string | null>(null);
  readonly email = signal<string | null>(null);

  readonly digits = signal<string[]>(Array(DIGIT_COUNT).fill(''));

  readonly indices = Array.from({ length: DIGIT_COUNT }, (_, i) => i);

  private cooldownTimer: ReturnType<typeof setInterval> | null = null;

  constructor(
    private readonly authService: AuthService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    this.userId.set(this.route.snapshot.queryParamMap.get('userId'));
    this.email.set(this.route.snapshot.queryParamMap.get('email'));

    if (!this.userId()) {
      this.errorMessage.set(
        'We could not find your pending verification. Request a new code below to continue.',
      );
    }
  }

  ngAfterViewInit(): void {
    const first = this.digitInputs.get(0);
    if (first) {
      first.nativeElement.focus();
    }
  }

  ngOnDestroy(): void {
    if (this.cooldownTimer) {
      clearInterval(this.cooldownTimer);
    }
  }

  get code(): string {
    return this.digits().join('');
  }

  get codeComplete(): boolean {
    return this.code.length === DIGIT_COUNT && /^\d+$/.test(this.code);
  }

  onDigitInput(index: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = input.value;

    if (!/^\d$/.test(value) && value !== '') {
      input.value = this.digits()[index];
      return;
    }

    if (value === '') {
      this.digits.update(d => {
        const next = [...d];
        next[index] = '';
        return next;
      });
      return;
    }

    this.digits.update(d => {
      const next = [...d];
      next[index] = value;
      return next;
    });

    input.value = value;

    if (index < DIGIT_COUNT - 1) {
      const next = this.digitInputs.get(index + 1);
      if (next) {
        next.nativeElement.focus();
      }
    }
  }

  onDigitKeydown(index: number, event: KeyboardEvent): void {
    if (event.key === 'Backspace') {
      const current = this.digits()[index];
      if (current === '' && index > 0) {
        this.digits.update(d => {
          const next = [...d];
          next[index - 1] = '';
          return next;
        });
        const prev = this.digitInputs.get(index - 1);
        if (prev) {
          prev.nativeElement.focus();
        }
      } else {
        this.digits.update(d => {
          const next = [...d];
          next[index] = '';
          return next;
        });
      }
    }

    if (event.key === 'ArrowLeft' && index > 0) {
      const prev = this.digitInputs.get(index - 1);
      if (prev) {
        prev.nativeElement.focus();
      }
    }

    if (event.key === 'ArrowRight' && index < DIGIT_COUNT - 1) {
      const next = this.digitInputs.get(index + 1);
      if (next) {
        next.nativeElement.focus();
      }
    }
  }

  onDigitPaste(event: ClipboardEvent): void {
    event.preventDefault();
    const data = event.clipboardData?.getData('text') ?? '';
    const clean = data.replace(/\D/g, '').slice(0, DIGIT_COUNT);

    if (clean.length === 0) return;

    this.digits.update(d => {
      const next = [...d];
      for (let i = 0; i < clean.length; i++) {
        next[i] = clean[i];
      }
      return next;
    });

    const focusIndex = Math.min(clean.length, DIGIT_COUNT - 1);
    const input = this.digitInputs.get(focusIndex);
    if (input) {
      input.nativeElement.focus();
    }
  }

  onSubmit(): void {
    this.errorMessage.set(null);
    const currentUserId = this.userId();

    if (!currentUserId) {
      this.errorMessage.set(
        'We could not find your pending verification. Request a new code below to continue.',
      );
      return;
    }

    if (!this.codeComplete) {
      this.errorMessage.set('Please enter all 6 digits of the verification code.');
      return;
    }

    this.isSubmitting.set(true);

    this.authService.verifyEmail({ userId: currentUserId, code: this.code }).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.isVerified.set(true);
      },
      error: (error: HttpErrorResponse) => {
        this.isSubmitting.set(false);
        if (error.status === 400) {
          this.errorMessage.set('That code is invalid. Please check it and try again.');
        } else if (error.status === 409) {
          this.errorMessage.set('This code has expired. Request a new one below.');
        } else {
          this.errorMessage.set('Something went wrong. Please try again later.');
        }
      },
    });
  }

  resendCode(): void {
    const currentEmail = this.email();
    if (!currentEmail || this.resendCooldown() > 0) {
      return;
    }

    this.resendMessage.set(null);
    this.errorMessage.set(null);
    this.digits.set(Array(DIGIT_COUNT).fill(''));
    this.isResending.set(true);

    this.authService.resendVerificationCode({ email: currentEmail }).subscribe({
      next: () => {
        this.isResending.set(false);
        this.resendMessage.set('A new verification code has been sent to your email.');
        this.startResendCooldown();
        const first = this.digitInputs.get(0);
        if (first) {
          first.nativeElement.focus();
        }
      },
      error: (error: HttpErrorResponse) => {
        this.isResending.set(false);
        if (error.status === 404) {
          this.errorMessage.set('No account was found for that email address.');
        } else if (error.status === 409) {
          this.resendMessage.set('This account is already verified — you can sign in now.');
        } else if (error.status === 429) {
          this.errorMessage.set('Too many requests. Please wait a moment and try again.');
        } else {
          this.errorMessage.set('Something went wrong. Please try again later.');
        }
      },
    });
  }

  goToLogin(): void {
    this.router.navigate(['/login']);
  }

  private startResendCooldown(): void {
    this.resendCooldown.set(30);
    this.cooldownTimer = setInterval(() => {
      const next = this.resendCooldown() - 1;
      this.resendCooldown.set(Math.max(next, 0));
      if (next <= 0 && this.cooldownTimer) {
        clearInterval(this.cooldownTimer);
        this.cooldownTimer = null;
      }
    }, 1000);
  }
}
