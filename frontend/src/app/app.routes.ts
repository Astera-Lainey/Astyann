import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';

/**
 * Application routes.
 *
 * - `/` is the public landing page (index.tsx).
 * - `/login`, `/signup`, `/verify-email`, `/forgot-password`,
 *   `/reset-password` are guarded by `guestGuard` so an already-authenticated
 *   user is redirected straight to the dashboard instead of seeing the auth
 *   forms again.
 * - `/app/**` is guarded by `authGuard`; only a placeholder dashboard route
 *   is wired here since the dashboard itself is out of scope for this
 *   migration, but the guard and route shape are production-ready.
 *
 * All feature components are lazy-loaded via `loadComponent` for optimal
 * initial bundle size — standard Angular 20 standalone routing.
 */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./features/landing/landing.component').then((m) => m.LandingComponent),
    title: 'Astyann — Accelerating Software Delivery Through Automation',
  },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/login/login.component').then((m) => m.LoginComponent),
    title: 'Log in · Astyann',
  },
  {
    path: 'signup',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/signup/signup.component').then((m) => m.SignupComponent),
    title: 'Get Started · Astyann',
  },
  {
    path: 'verify-email',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/verify-email/verify-email.component').then(
        (m) => m.VerifyEmailComponent,
      ),
    title: 'Verify your email · Astyann',
  },
  {
    path: 'forgot-password',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/forgot-password/forgot-password.component').then(
        (m) => m.ForgotPasswordComponent,
      ),
    title: 'Reset password · Astyann',
  },
  {
    path: 'reset-password',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/reset-password/reset-password.component').then(
        (m) => m.ResetPasswordComponent,
      ),
    title: 'Set new password · Astyann',
  },
  {
    path: 'app/dashboard',
    canActivate: [authGuard],
    // Placeholder: the authenticated app shell / dashboard is out of scope
    // for this auth-module migration. Swap in the real dashboard component
    // once it exists.
    loadComponent: () =>
      import('./features/landing/landing.component').then((m) => m.LandingComponent),
    title: 'Dashboard · Astyann',
  },
  {
    path: '**',
    redirectTo: '',
  },
];
