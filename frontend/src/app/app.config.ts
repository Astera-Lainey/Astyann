import { ApplicationConfig, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { jwtInterceptor } from './core/interceptors/jwt.interceptor';

/**
 * Application-wide providers.
 *
 * - `provideZonelessChangeDetection()`: Angular 20's zoneless mode, the
 *   natural pairing with the Signal-based state used throughout AuthService
 *   and the form components.
 * - `provideHttpClient(withInterceptors([jwtInterceptor]))`: registers the
 *   functional JWT interceptor for every HttpClient call app-wide.
 * - `withComponentInputBinding()`: lets route params/query params bind
 *   directly to component @Input()s where useful.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([jwtInterceptor])),
  ],
};
