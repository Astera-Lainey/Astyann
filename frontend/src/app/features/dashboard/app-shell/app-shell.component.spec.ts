import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AppShellComponent } from './app-shell.component';
import { environment } from '../../../../environments/environment';

describe('AppShellComponent', () => {
  afterEach(() => {
    localStorage.clear();
  });

  async function setup() {
    await TestBed.configureTestingModule({
      imports: [AppShellComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    const fixture = TestBed.createComponent(AppShellComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    return { component, fixture };
  }

  it('loads a short, recently-updated project list for the sidebar shortcuts', async () => {
    await setup();
    const httpMock = TestBed.inject(HttpTestingController);

    const req = httpMock.expectOne(
      (r) =>
        r.url === `${environment.apiBaseUrl}/projects` &&
        r.params.get('size') === '6' &&
        r.params.get('sort') === 'updatedAt,desc',
    );
    req.flush({
      status: 200,
      message: 'OK',
      data: { content: [], totalElements: 0, totalPages: 0, currentPage: 0, pageSize: 6 },
    });
  });

  it('calls AuthService.logout() and navigates to /login', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigate');

    httpMock.expectOne(`${environment.apiBaseUrl}/projects`).flush({
      status: 200,
      message: 'OK',
      data: { content: [], totalElements: 0, totalPages: 0, currentPage: 0, pageSize: 6 },
    });

    component.logout();

    const logoutReq = httpMock.expectOne(`${environment.apiBaseUrl}/auth/logout`);
    logoutReq.flush({ status: 200, message: 'Logged out successfully.', data: null });

    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });

  it('does not show the workspace section when not viewing a project', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(`${environment.apiBaseUrl}/projects`).flush({
      status: 200,
      message: 'OK',
      data: { content: [], totalElements: 0, totalPages: 0, currentPage: 0, pageSize: 6 },
    });

    expect(component.isInProjectWorkspace()).toBe(false);
  });

  it('shows the workspace section with the active projectId once inside a project route', async () => {
    await TestBed.resetTestingModule().configureTestingModule({
      imports: [AppShellComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'app/projects/:id/:section', children: [] }]),
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(AppShellComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(`${environment.apiBaseUrl}/projects`).flush({
      status: 200,
      message: 'OK',
      data: { content: [], totalElements: 0, totalPages: 0, currentPage: 0, pageSize: 6 },
    });

    const router = TestBed.inject(Router);
    await router.navigateByUrl('/app/projects/p1/requirements');

    expect(component.isInProjectWorkspace()).toBe(true);
    expect(component.activeProjectIdValue()).toBe('p1');
  });
});
