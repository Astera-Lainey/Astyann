import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Observable, catchError, map, of, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiEnvelope,
  AuthenticatedUser,
  ConfirmPasswordResetRequest,
  LoginRequest,
  LoginResponseData,
  RegisterRequest,
  RegisterResponseData,
  RequestPasswordResetRequest,
  ResendVerificationRequest,
  ResendVerificationResponseData,
  VerifyEmailRequest,
  VerifyEmailResponseData,
} from '../models/auth.models';

const TOKEN_KEY = 'ast_at';
const USER_KEY = 'ast_user';

function readUserFromSession(): AuthenticatedUser | null {
  const raw = sessionStorage.getItem(USER_KEY);
  if (!raw) return null;
  try { return JSON.parse(raw) as AuthenticatedUser; } catch { return null; }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly baseUrl = `${environment.apiBaseUrl}/auth`;

  private readonly _accessToken = signal<string | null>(
    sessionStorage.getItem(TOKEN_KEY),
  );
  private readonly currentUserSignal = signal<AuthenticatedUser | null>(
    readUserFromSession(),
  );

  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly isAuthenticated = computed(() => this._accessToken() !== null);

  constructor(private readonly http: HttpClient) {}

  register(request: RegisterRequest): Observable<RegisterResponseData> {
    return this.http
      .post<ApiEnvelope<RegisterResponseData>>(`${this.baseUrl}/register`, request)
      .pipe(map((res) => res.data));
  }

  verifyEmail(request: VerifyEmailRequest): Observable<VerifyEmailResponseData> {
    return this.http
      .post<ApiEnvelope<VerifyEmailResponseData>>(`${this.baseUrl}/verify-email`, request)
      .pipe(map((res) => res.data));
  }

  resendVerificationCode(
    request: ResendVerificationRequest,
  ): Observable<ResendVerificationResponseData> {
    return this.http
      .post<ApiEnvelope<ResendVerificationResponseData>>(`${this.baseUrl}/verify/resend`, request)
      .pipe(map((res) => res.data));
  }

  login(request: LoginRequest): Observable<LoginResponseData> {
    return this.http.post<ApiEnvelope<LoginResponseData>>(`${this.baseUrl}/login`, request).pipe(
      map((res) => res.data),
      tap((data) => {
        const user: AuthenticatedUser = { id: data.userId, email: data.email };
        sessionStorage.setItem(TOKEN_KEY, data.accessToken);
        sessionStorage.setItem(USER_KEY, JSON.stringify(user));
        this._accessToken.set(data.accessToken);
        this.currentUserSignal.set(user);
      }),
    );
  }

  logout(): Observable<void> {
    return this.http.post<ApiEnvelope<null>>(`${this.baseUrl}/logout`, {}).pipe(
      map(() => undefined),
      catchError(() => of(undefined)),
      tap(() => {
        sessionStorage.removeItem(TOKEN_KEY);
        sessionStorage.removeItem(USER_KEY);
        this._accessToken.set(null);
        this.currentUserSignal.set(null);
      }),
    );
  }

  requestPasswordReset(request: RequestPasswordResetRequest): Observable<void> {
    return this.http
      .post<ApiEnvelope<null>>(`${this.baseUrl}/reset-password`, request)
      .pipe(map(() => undefined));
  }

  confirmPasswordReset(request: ConfirmPasswordResetRequest): Observable<void> {
    return this.http
      .post<ApiEnvelope<null>>(`${this.baseUrl}/reset-password/confirm`, request)
      .pipe(map(() => undefined));
  }

  getAccessToken(): string | null {
    return this._accessToken();
  }
}
