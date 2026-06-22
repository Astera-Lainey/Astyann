/**
 * Strongly typed contracts for the Astyann authentication module.
 *
 * These mirror the Spring Boot 3.x backend DTOs exactly as specified in the
 * API Contract (Section IV — "Authentication and Account Management",
 * API-AUTH-01 through API-AUTH-08). Every endpoint in that module wraps its
 * payload in the platform-wide envelope described in Section 2.4:
 *
 *   { "status": <http_code>, "message": "<short_label>", "data": <T | null> }
 *
 * `ApiEnvelope<T>` models that wrapper; `AuthService` unwraps `.data` before
 * returning values to callers so components never deal with the envelope
 * directly.
 */

// ---------------------------------------------------------------------
// Envelope (Section 2.4 — applies to every endpoint in the API)
// ---------------------------------------------------------------------

/** The homogeneous success/response envelope returned by all endpoints. */
export interface ApiEnvelope<T> {
  status: number;
  message: string;
  data: T;
}

// ---------------------------------------------------------------------
// API-AUTH-01 — POST /auth/register
// ---------------------------------------------------------------------

export interface RegisterRequest {
  email: string;
  password: string;
}

/** `data` payload of the 201 response from /auth/register. */
export interface RegisterResponseData {
  userId: string;
  email: string;
  isVerified: boolean;
}

// ---------------------------------------------------------------------
// API-AUTH-02 — POST /auth/verify-email
// ---------------------------------------------------------------------

export interface VerifyEmailRequest {
  userId: string;
  /** 6-digit numeric code sent by email. */
  code: string;
}

export interface VerifyEmailResponseData {
  accessToken: string;
  refreshToken: string;
  userId: string;
  email: string;
}

// ---------------------------------------------------------------------
// API-AUTH-03 — POST /auth/verify/resend
// ---------------------------------------------------------------------

export interface ResendVerificationRequest {
  email: string;
}

export interface ResendVerificationResponseData {
  verificationExpiryDate: string;
  userId?: string;
}

// ---------------------------------------------------------------------
// API-AUTH-04 — POST /auth/login
// ---------------------------------------------------------------------

export interface LoginRequest {
  email: string;
  password: string;
}

/**
 * `data` payload of the 200 response from /auth/login.
 *
 * NOTE: per the contract's example schema, login returns only these four
 * fields — no `tokenType`, `expiresIn`, or `roles`. Access tokens are valid
 * for 1 hour and refresh tokens for 7 days (Section 2.1), but those
 * durations are not echoed back in the payload itself.
 */
export interface LoginResponseData {
  accessToken: string;
  refreshToken: string;
  userId: string;
  email: string;
}

// ---------------------------------------------------------------------
// API-AUTH-05 — POST /auth/logout (JWT required)
// ---------------------------------------------------------------------

// No request body (empty `{}`); response `data` is `null`.

// ---------------------------------------------------------------------
// API-AUTH-06 — POST /auth/refresh-token
// ---------------------------------------------------------------------

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface RefreshTokenResponseData {
  accessToken: string;
  expiresIn: number;
}

// ---------------------------------------------------------------------
// API-AUTH-07 — POST /auth/reset-password (request a reset email)
// ---------------------------------------------------------------------

export interface RequestPasswordResetRequest {
  email: string;
}

// Always 200 OK, `data` is `null` — deliberately doesn't reveal whether the
// email exists, to prevent account enumeration (contract, API-AUTH-07).

// ---------------------------------------------------------------------
// API-AUTH-08 — POST /auth/password-reset/confirm
// ---------------------------------------------------------------------

export interface ConfirmPasswordResetRequest {
  token: string;
  newPassword: string;
}

// `data` is `null` on success.

// ---------------------------------------------------------------------
// Session / local auth state
// ---------------------------------------------------------------------

/**
 * What the frontend actually persists locally after a successful login.
 * Built from `LoginResponseData` — see `AuthService.persistSession`.
 */
export interface AuthenticatedUser {
  id: string;
  email: string;
}

// ---------------------------------------------------------------------
// JWT
// ---------------------------------------------------------------------

export interface DecodedJwt {
  sub: string;
  email: string;
  type: 'access' | 'refresh';
  iat: number;
  exp: number;
}

// ---------------------------------------------------------------------
// API error shape (Section 2.4 — RFC 7807-flavoured Problem Details)
// ---------------------------------------------------------------------

export interface ApiFieldError {
  field: string;
  code: string;
  detail: string;
}

/**
 * Standard error envelope returned by every endpoint on failure. Shares the
 * same top-level shape as `ApiEnvelope` (`status`/`message`/`data`) plus an
 * `errors` array for field-level validation failures (e.g. 400/422
 * responses to /auth/register or /auth/password-reset/confirm).
 */
export interface ApiErrorResponse {
  status: number;
  message: string;
  data: null;
  errors?: ApiFieldError[];
}
