import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { VerifyEmailComponent } from './verify-email.component';
import { environment } from '../../../../environments/environment';

describe('VerifyEmailComponent', () => {
  async function setup(queryParams: Record<string, string> = { userId: 'u1', email: 'dev@astyann.com' }) {
    await TestBed.configureTestingModule({
      imports: [VerifyEmailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: convertToParamMap(queryParams) },
          },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(VerifyEmailComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    return { component, fixture };
  }

  it('reads userId and email from query params on init', async () => {
    const { component } = await setup();
    expect(component.userId()).toBe('u1');
    expect(component.email()).toBe('dev@astyann.com');
  });

  it('shows a recovery message when userId is missing from the URL', async () => {
    const { component } = await setup({ email: 'dev@astyann.com' });
    expect(component.errorMessage()).toContain('could not find your pending verification');
  });

  it('rejects a code that is not exactly 6 digits', async () => {
    const { component } = await setup();
    component.form.controls.code.setValue('123');
    component.form.controls.code.markAsTouched();
    expect(component.codeError).toBe('The code must be exactly 6 digits.');
  });

  it('submits the code to /auth/verify-email and shows the success state', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);

    component.form.controls.code.setValue('847291');
    component.onSubmit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/verify-email`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ userId: 'u1', code: '847291' });
    req.flush({ status: 200, message: 'Account verified and activated.', data: { isVerified: true } });

    expect(component.isVerified()).toBe(true);
  });

  it('calls /auth/verify/resend when requesting a new code', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);

    component.resendCode();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/verify/resend`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'dev@astyann.com' });
    req.flush({
      status: 200,
      message: 'A new verification code has been sent.',
      data: { verificationExpiryDate: '2026-06-12T09:45:00Z' },
    });

    expect(component.resendMessage()).toContain('A new verification code has been sent');
    expect(component.resendCooldown()).toBeGreaterThan(0);
  });
});
