import { HttpClient } from '@angular/common/http'; //gives us access to all backend requests
import { Injectable, computed, signal } from '@angular/core';
import { Observable, catchError, map, of, tap } from 'rxjs';
import { environment } from '../../../environments/environment'; //bakcend link
import {
  ApiEnvelope,
  AuthenticatedUser,
  ConfirmPasswordResetRequest,
  LoginRequest,
  LoginResponseData,
  RefreshTokenRequest,
  RefreshTokenResponseData,
  RegisterRequest,
  RegisterResponseData,
  RequestPasswordResetRequest,
  ResendVerificationRequest,
  ResendVerificationResponseData,
  VerifyEmailRequest,
  VerifyEmailResponseData,
} from '../models/auth.models';

const ACCESS_TOKEN_KEY = 'astyann_access_token';
const REFRESH_TOKEN_KEY = 'astyann_refresh_token';
const USER_KEY = 'astyann_user';

/**
 * Single source of truth for authentication state and all auth-related
 * HTTP interactions with the backend, per Section IV ("Authentication and
 * Account Management", API-AUTH-01 through API-AUTH-08) of the API Contract.
 *
 * Every endpoint in this module returns the platform-wide envelope
 * `{ status, message, data }` (Section 2.4) — this service unwraps `.data`
 * so callers (components) work with plain typed payloads.
 *
 * Responsibilities (SRP):
 *  - Persisting / restoring the session from storage
 *  - Exposing reactive auth state via Signals
 *  - Talking to the auth endpoints
 *
 * Token attachment to outgoing requests, and silent refresh-on-401, are
 * handled separately by `JwtInterceptor` — this service only stores and
 * exposes the tokens it needs for that.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly baseUrl = `${environment.apiBaseUrl}/auth`;

  /** Internal writable signal holding the current user, or null if logged out. so that page refreshes dont log the user out */
  private readonly currentUserSignal = signal<AuthenticatedUser | null>(this.readUserFromStorage());

  /** Public read-only view of the current authenticated user. */
  readonly currentUser = this.currentUserSignal.asReadonly();

  /** Derived signal: true when a user is logged in. */
  readonly isAuthenticated = computed(() => this.currentUserSignal() !== null);

  constructor(private readonly http: HttpClient) {}

  // ---------------------------------------------------------------------
  // API-AUTH-01 — POST /auth/register
  // ---------------------------------------------------------------------

  /** Creates an unverified account and triggers a verification email. */
  register(request: RegisterRequest): Observable<RegisterResponseData> {
    return this.http
      .post<ApiEnvelope<RegisterResponseData>>(`${this.baseUrl}/register`, request)
      .pipe(map((res) => res.data));
  }

  // ---------------------------------------------------------------------
  // API-AUTH-02 — POST /auth/verify-email
  // ---------------------------------------------------------------------

  /** Activates the account using the 6-digit code emailed to the user. */
  verifyEmail(request: VerifyEmailRequest): Observable<VerifyEmailResponseData> {
    return this.http
      .post<ApiEnvelope<VerifyEmailResponseData>>(`${this.baseUrl}/verify-email`, request)
      .pipe(map((res) => res.data));
  }

  // ---------------------------------------------------------------------
  // API-AUTH-03 — POST /auth/verify/resend
  // ---------------------------------------------------------------------

  /** Requests a new verification code, invalidating any previously issued one. */
  resendVerificationCode(
    request: ResendVerificationRequest,
  ): Observable<ResendVerificationResponseData> {
    return this.http
      .post<ApiEnvelope<ResendVerificationResponseData>>(`${this.baseUrl}/verify/resend`, request)
      .pipe(map((res) => res.data));
  }

  // ---------------------------------------------------------------------
  // API-AUTH-04 — POST /auth/login
  // ---------------------------------------------------------------------

  login(request: LoginRequest): Observable<LoginResponseData> {
    return this.http.post<ApiEnvelope<LoginResponseData>>(`${this.baseUrl}/login`, request).pipe(
      map((res) => res.data),
      tap((data) => this.persistSession(data)),
    );
  }

  // ---------------------------------------------------------------------
  // API-AUTH-05 — POST /auth/logout (JWT required)
  // ---------------------------------------------------------------------

  /**
   * Invalidates the current access token server-side, then always clears
   * the local session regardless of outcome (a failed logout call — e.g.
   * the token was already expired — shouldn't leave the user stuck
   * "logged in" on a session the server has already abandoned).
   */
  logout(): Observable<void> {
    return this.http.post<ApiEnvelope<null>>(`${this.baseUrl}/logout`, {}).pipe(
      map(() => undefined),
      catchError(() => of(undefined)),
      tap(() => this.clearSession()),
    );
  }

  // ---------------------------------------------------------------------
  // API-AUTH-06 — POST /auth/refresh-token
  // ---------------------------------------------------------------------

  /**
   * Exchanges the stored refresh token for a new access token. Used by
   * `JwtInterceptor` to transparently recover from a 401 caused by access
   * token expiry (1 hour validity — Section 2.1) without forcing a full
   * re-login while the refresh token (7 day validity) is still good.
   */
  refreshAccessToken(): Observable<RefreshTokenResponseData> {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      throw new Error('No refresh token available.');
    }
    const request: RefreshTokenRequest = { refreshToken };
    return this.http
      .post<ApiEnvelope<RefreshTokenResponseData>>(`${this.baseUrl}/refresh-token`, request)
      .pipe(
        map((res) => res.data),
        tap((data) => localStorage.setItem(ACCESS_TOKEN_KEY, data.accessToken)),
      );
  }

  // ---------------------------------------------------------------------
  // API-AUTH-07 — POST /auth/reset-password (request a reset email)
  // ---------------------------------------------------------------------

  /**
   * Requests a password reset email. Always resolves with 200 OK — the
   * backend deliberately never reveals whether the address is registered,
   * to prevent account enumeration (contract, API-AUTH-07).
   */
  requestPasswordReset(request: RequestPasswordResetRequest): Observable<void> {
    return this.http
      .post<ApiEnvelope<null>>(`${this.baseUrl}/reset-password`, request)
      .pipe(map(() => undefined));
  }

  // ---------------------------------------------------------------------
  // API-AUTH-08 — POST /auth/password-reset/confirm
  // ---------------------------------------------------------------------

  confirmPasswordReset(request: ConfirmPasswordResetRequest): Observable<void> {
    return this.http
      .post<ApiEnvelope<null>>(`${this.baseUrl}/password-reset/confirm`, request)
      .pipe(map(() => undefined));
  }

  // ---------------------------------------------------------------------
  // Token / session accessors
  // ---------------------------------------------------------------------

  getAccessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  /**
   * Clears the local session only, without calling the backend. Used by
   * `JwtInterceptor` when a 401 survives the refresh attempt (i.e. the
   * refresh token itself is invalid/expired) — at that point the server
   * already considers the session dead, so there's nothing to invalidate.
   */
  clearSession(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.currentUserSignal.set(null);
  }

  // ---------------------------------------------------------------------
  // Internals
  // ---------------------------------------------------------------------

  private persistSession(data: LoginResponseData): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, data.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, data.refreshToken);
    const user: AuthenticatedUser = { id: data.userId, email: data.email };
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    this.currentUserSignal.set(user);
  }

  private readUserFromStorage(): AuthenticatedUser | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as AuthenticatedUser;
    } catch {
      return null;
    }
  }
}
