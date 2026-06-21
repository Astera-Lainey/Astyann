import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Attaches the JWT bearer token (if present) to every outgoing request that
 * targets our own API.
 *
 * On a 401 (Section VI — "Expired tokens must be renewed via
 * /auth/refresh-token"), attempts exactly one silent refresh using the
 * stored refresh token and retries the original request with the new
 * access token. If the refresh itself fails (refresh token also expired/
 * invalid — its own 401), the local session is cleared and the user is
 * redirected to /login.
 *
 * The refresh-token endpoint and the auth endpoints that don't require a
 * token (register/login/etc.) are excluded from the retry loop to avoid
 * recursion and to avoid bouncing a bad-credentials 401 on /auth/login into
 * an unnecessary refresh attempt.
 *
 * Registered as a functional interceptor (Angular 15+/20 style) in app.config.ts.
 */

// These endpoints should NEVER trigger token refresh.
const NO_RETRY_PATHS = ['/auth/login', '/auth/register', '/auth/refresh-token']; 

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const token = authService.getAccessToken();
  const isApiRequest = req.url.startsWith('http'); //jwt tokens are only attatched to api requests

  const authorizedReq =
    token && isApiRequest 
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  const isRetryExempt = NO_RETRY_PATHS.some((path) => req.url.includes(path));

  return next(authorizedReq).pipe(
    catchError((error: HttpErrorResponse) => {
      const canAttemptRefresh =
        error.status === 401 && isApiRequest && !isRetryExempt && !!authService.getRefreshToken();

      if (!canAttemptRefresh) {
        if (error.status === 401) {
          authService.clearSession();
          router.navigate(['/login'], { queryParams: { sessionExpired: true } });
        }
        return throwError(() => error);
      }

      return authService.refreshAccessToken().pipe(
        switchMap((refreshed) => {
          const retriedReq = req.clone({
            setHeaders: { Authorization: `Bearer ${refreshed.accessToken}` },
          });
          return next(retriedReq);
        }),
        catchError((refreshError) => {
          authService.clearSession();
          router.navigate(['/login'], { queryParams: { sessionExpired: true } });
          return throwError(() => refreshError);
        }),
      );
    }),
  );
};
