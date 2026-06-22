import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Input,
  OnDestroy,
  OnInit,
  QueryList,
  signal,
  ViewChildren,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { SparkleIconComponent } from '../../../shared/components/sparkle-icon/sparkle-icon.component';
import { DecorativeCirclesComponent } from '../../../shared/components/decorative-circles/decorative-circles.component';

const DIGIT_COUNT = 6;

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

  @Input() set userId(value: string | null) {
    this._userId.set(value ?? null);
  }
  @Input() set email(value: string | null) {
    this._email.set(value ?? null);
  }

  private readonly _userId = signal<string | null>(null);
  private readonly _email = signal<string | null>(null);

  readonly isSubmitting = signal(false);
  readonly isVerified = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly isResending = signal(false);
  readonly resendMessage = signal<string | null>(null);
  readonly resendCooldown = signal(0);

  readonly digits = signal<string[]>(Array(DIGIT_COUNT).fill(''));
  readonly indices = Array.from({ length: DIGIT_COUNT }, (_, i) => i);

  private cooldownTimer: ReturnType<typeof setInterval> | null = null;

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly toastService: ToastService,
  ) {}   

  ngOnInit(): void {
    if (!this._userId()) {
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

  // exposed for template
  userId_() { return this._userId(); }
  email_()  { return this._email();  }

  get code(): string {
    return this.digits().join('');
  }

  get codeComplete(): boolean {
    return this.code.length === DIGIT_COUNT && /^\d+$/.test(this.code);
  }

  onDigitInput(index: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    const raw = input.value;
    const last = raw.slice(-1);

    if (!/^\d$/.test(last) && raw !== '') {
      input.value = this.digits()[index];
      return;
    }

    if (raw === '') {
      this.digits.update(d => {
        const next = [...d];
        next[index] = '';
        return next;
      });
      return;
    }

    this.digits.update(d => {
      const next = [...d];
      next[index] = last;
      return next;
    });

    input.value = last;

    if (index < DIGIT_COUNT - 1) {
      const nextInput = this.digitInputs.get(index + 1);
      if (nextInput) {
        nextInput.nativeElement.focus();
        nextInput.nativeElement.value = '';
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
          prev.nativeElement.value = '';
        }
      } else {
        this.digits.update(d => {
          const next = [...d];
          next[index] = '';
          return next;
        });
        const current = this.digitInputs.get(index);
        if (current) current.nativeElement.value = '';
      }
    }

    if (event.key === 'ArrowLeft' && index > 0) {
      this.digitInputs.get(index - 1)?.nativeElement.focus();
    }

    if (event.key === 'ArrowRight' && index < DIGIT_COUNT - 1) {
      this.digitInputs.get(index + 1)?.nativeElement.focus();
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

    // sync DOM values
    this.digitInputs.forEach((el, i) => {
      el.nativeElement.value = clean[i] ?? '';
    });

    const focusIndex = Math.min(clean.length, DIGIT_COUNT - 1);
    this.digitInputs.get(focusIndex)?.nativeElement.focus();
  }

  onSubmit(): void {
    this.errorMessage.set(null);
    const currentUserId = this._userId();

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
        this.toastService.show('Email verified successfully.');
        this.router.navigate(['/login'], { queryParams: { verified: 'true' } });
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
    const currentEmail = this._email();
    if (!currentEmail || this.resendCooldown() > 0) {
      return;
    }

    this.resendMessage.set(null);
    this.errorMessage.set(null);
    this.digits.set(Array(DIGIT_COUNT).fill(''));
    this.digitInputs?.forEach(el => el.nativeElement.value = '');
    this.isResending.set(true);

    this.authService.resendVerificationCode({ email: currentEmail }).subscribe({
      next: (data) => {
        this.isResending.set(false);
        this.resendMessage.set('A new verification code has been sent to your email.');
        if (data?.userId) {
          this._userId.set(data.userId);
          this.errorMessage.set(null);
        }
        this.startResendCooldown();
        this.digitInputs.get(0)?.nativeElement.focus();
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