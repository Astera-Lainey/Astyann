import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { LoginComponent } from './login.component';
import { environment } from '../../../../environments/environment';

describe('LoginComponent', () => {
  let component: LoginComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    const fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('marks the form invalid when fields are empty', () => {
    expect(component.form.valid).toBe(false);
  });

  it('rejects a malformed email', () => {
    component.form.controls.email.setValue('not-an-email');
    component.form.controls.email.markAsTouched();
    expect(component.emailError).toBe('Enter a valid email address.');
  });

  it('rejects a password under 8 characters', () => {
    component.form.controls.password.setValue('short');
    component.form.controls.password.markAsTouched();
    expect(component.passwordError).toBe('Password must be at least 8 characters.');
  });

  it('does not call the API when the form is invalid on submit', () => {
    const httpMock = TestBed.inject(HttpTestingController);
    component.onSubmit();
    httpMock.expectNone(`${environment.apiBaseUrl}/auth/login`);
    expect(component.form.controls.email.touched).toBe(true);
  });

  it('surfaces a generic error message on 401', () => {
    const httpMock = TestBed.inject(HttpTestingController);
    component.form.setValue({ email: 'dev@astyann.com', password: 'WrongPass1' });

    component.onSubmit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`);
    req.flush(
      { status: 401, message: 'Invalid credentials.', data: null },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(component.errorMessage()).toBe('Invalid email or password. Please try again.');
    expect(component.isSubmitting()).toBe(false);
  });

  it('redirects to /verify-email on a 403 (account not verified)', () => {
    const httpMock = TestBed.inject(HttpTestingController);
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigate');
    component.form.setValue({ email: 'unverified@astyann.com', password: 'Passw0rd!' });

    component.onSubmit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`);
    req.flush(
      { status: 403, message: 'Account not verified.', data: null },
      { status: 403, statusText: 'Forbidden' },
    );

    expect(navigateSpy).toHaveBeenCalledWith(['/verify-email'], {
      queryParams: { email: 'unverified@astyann.com' },
    });
  });
});
