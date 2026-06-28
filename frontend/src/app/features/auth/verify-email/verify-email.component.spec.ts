import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { VerifyEmailComponent } from './verify-email.component';
import { environment } from '../../../../environments/environment';

describe('VerifyEmailComponent', () => {
  // userId and email are @Input() setters — set them directly before detectChanges()
  // so ngOnInit() sees the values (no withComponentInputBinding in tests).
  async function setup(
    params: { userId?: string; email?: string } = {
      userId: 'u1',
      email: 'dev@astyann.com',
    },
  ) {
    await TestBed.configureTestingModule({
      imports: [VerifyEmailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(VerifyEmailComponent);
    const component = fixture.componentInstance;
    if (params.userId !== undefined) component.userId = params.userId;
    if (params.email !== undefined) component.email = params.email;
    fixture.detectChanges();
    return { component, fixture };
  }

  it('reads userId and email from @Input() on init', async () => {
    const { component } = await setup();
    // userId_() and email_() are the public signal accessors
    expect(component.userId_()).toBe('u1');
    expect(component.email_()).toBe('dev@astyann.com');
  });

  it('shows a recovery message when userId is not provided', async () => {
    const { component } = await setup({ email: 'dev@astyann.com' });
    expect(component.errorMessage()).toContain('could not find your pending verification');
  });

  it('shows an error when submitting with fewer than 6 digits', async () => {
    const { component } = await setup();
    // Set only 3 digits — code will be '123' (length 3), codeComplete = false
    component.digits.set(['1', '2', '3', '', '', '']);
    component.onSubmit();
    expect(component.errorMessage()).toBe('Please enter all 6 digits of the verification code.');
  });

  it('submits the code to /auth/verify-email and shows the success state', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);

    component.digits.set(['8', '4', '7', '2', '9', '1']);
    component.onSubmit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/verify-email`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ userId: 'u1', code: '847291' });
    req.flush({ status: 200, message: 'Account verified and activated.', data: { accessToken: 'tok', userId: 'u1', email: 'dev@astyann.com' } });

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
