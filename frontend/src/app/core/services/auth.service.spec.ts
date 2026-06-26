import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';
import { ApiEnvelope, LoginResponseData } from '../models/auth.models';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), AuthService],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    sessionStorage.clear();
  });

  it('starts unauthenticated before login', () => {
    expect(service.isAuthenticated()).toBe(false);
    expect(service.currentUser()).toBeNull();
    expect(service.getAccessToken()).toBeNull();
  });

  it('sets auth state and exposes the token after a successful login', () => {
    const mockEnvelope: ApiEnvelope<LoginResponseData> = {
      status: 200,
      message: 'Login successful.',
      data: {
        accessToken: 'fake-access-token',
        userId: 'u1',
        email: 'dev@astyann.com',
      },
    };

    service.login({ email: 'dev@astyann.com', password: 'Sup3rSecret!' }).subscribe();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`);
    expect(req.request.method).toBe('POST');
    req.flush(mockEnvelope);

    expect(service.isAuthenticated()).toBe(true);
    expect(service.currentUser()?.email).toBe('dev@astyann.com');
    expect(service.getAccessToken()).toBe('fake-access-token');
  });

  it('clears auth state on logout', () => {
    const mockEnvelope: ApiEnvelope<LoginResponseData> = {
      status: 200,
      message: 'Login successful.',
      data: { accessToken: 'fake-access-token', userId: 'u1', email: 'dev@astyann.com' },
    };

    service.login({ email: 'dev@astyann.com', password: 'Sup3rSecret!' }).subscribe();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`).flush(mockEnvelope);
    expect(service.isAuthenticated()).toBe(true);

    service.logout().subscribe();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/auth/logout`)
      .flush({ status: 200, message: 'Logged out.', data: null });

    expect(service.isAuthenticated()).toBe(false);
    expect(service.getAccessToken()).toBeNull();
  });

  it('clears auth state even when the logout call fails', () => {
    const mockEnvelope: ApiEnvelope<LoginResponseData> = {
      status: 200,
      message: 'Login successful.',
      data: { accessToken: 'fake-access-token', userId: 'u1', email: 'dev@astyann.com' },
    };

    service.login({ email: 'dev@astyann.com', password: 'Sup3rSecret!' }).subscribe();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`).flush(mockEnvelope);

    service.logout().subscribe();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/auth/logout`)
      .flush('Server error', { status: 500, statusText: 'Internal Server Error' });

    expect(service.isAuthenticated()).toBe(false);
    expect(service.getAccessToken()).toBeNull();
  });

  it('registers a new account against /auth/register', () => {
    service.register({ email: 'new@astyann.com', password: 'Str0ngP@ss!' }).subscribe((data) => {
      expect(data.userId).toBe('u2');
      expect(data.isVerified).toBe(false);
    });

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/register`);
    expect(req.request.method).toBe('POST');
    req.flush({
      status: 201,
      message: 'Account created. Verification email sent.',
      data: { userId: 'u2', email: 'new@astyann.com', isVerified: false },
    });
  });

  it('requests a password reset email against /auth/reset-password', () => {
    service.requestPasswordReset({ email: 'dev@astyann.com' }).subscribe();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/reset-password`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'dev@astyann.com' });
    req.flush({ status: 200, message: 'If that email exists a reset link was sent.', data: null });
  });

  it('confirms a password reset against /auth/reset-password/confirm', () => {
    service
      .confirmPasswordReset({ token: 'tok-abc', newPassword: 'NewP@ss1!' })
      .subscribe();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/reset-password/confirm`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ token: 'tok-abc', newPassword: 'NewP@ss1!' });
    req.flush({ status: 200, message: 'Password reset successfully.', data: null });
  });
});
