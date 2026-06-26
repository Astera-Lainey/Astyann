import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

const TOKEN_KEY = 'ast_at';

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  // inject() must be called in a synchronous injection context — capture here,
  // not inside the catchError callback where the context is no longer active.
  const router = inject(Router);
  const token = sessionStorage.getItem(TOKEN_KEY);

  if (token) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        sessionStorage.removeItem(TOKEN_KEY);
        router.navigate(['/login']);
      }
      return throwError(() => error);
    }),
  );
};
