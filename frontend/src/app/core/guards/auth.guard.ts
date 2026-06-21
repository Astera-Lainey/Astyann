import { inject } from '@angular/core'; //used to create an instance
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Protects routes that require an authenticated session (e.g. /app/dashboard).
 * Redirects anonymous users to /login, preserving the attempted URL so we can
 * optionally return them there after a successful login.
 */

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true; //allow access if user has logged in
  }

  return router.createUrlTree(['/login'], { queryParams: { redirectTo: state.url } });
};
