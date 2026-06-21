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
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), AuthService],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('starts unauthenticated when no session is stored', () => {
    expect(service.isAuthenticated()).toBe(false);
    expect(service.currentUser()).toBeNull();
  });

  it('persists the session and flips isAuthenticated() to true on successful login', () => {
    const mockEnvelope: ApiEnvelope<LoginResponseData> = {
      status: 200,
      message: 'Login successful.',
      data: {
        accessToken: 'fake-access-token',
        refreshToken: 'fake-refresh-token',
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
    expect(service.getRefreshToken()).toBe('fake-refresh-token');
  });

  it('clears the session on logout', () => {
    const mockEnvelope: ApiEnvelope<LoginResponseData> = {
      status: 200,
      message: 'Login successful.',
      data: {
        accessToken: 'fake-access-token',
        refreshToken: 'fake-refresh-token',
        userId: 'u1',
        email: 'dev@astyann.com',
      },
    };

    service.login({ email: 'dev@astyann.com', password: 'Sup3rSecret!' }).subscribe();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`).flush(mockEnvelope);
    expect(service.isAuthenticated()).toBe(true);

    service.logout().subscribe();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/auth/logout`)
      .flush({ status: 200, message: 'Logged out successfully.', data: null });

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

  it('exchanges a refresh token for a new access token', () => {
    localStorage.setItem('astyann_refresh_token', 'fake-refresh-token');

    service.refreshAccessToken().subscribe((data) => {
      expect(data.accessToken).toBe('new-access-token');
    });

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/refresh-token`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ refreshToken: 'fake-refresh-token' });
    req.flush({
      status: 200,
      message: 'Token renewed.',
      data: { accessToken: 'new-access-token', expiresIn: 3600 },
    });

    expect(service.getAccessToken()).toBe('new-access-token');
  });
});
