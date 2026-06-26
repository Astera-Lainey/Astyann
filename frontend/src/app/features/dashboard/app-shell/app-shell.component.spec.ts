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

  const searchUrl = `${environment.apiBaseUrl}/projects/search`;
  const emptyFlush = { status: 200, message: 'OK', data: [] };

  it('loads the project list for the sidebar via GET /projects/search', async () => {
    await setup();
    const httpMock = TestBed.inject(HttpTestingController);

    const req = httpMock.expectOne((r) => r.url === searchUrl && r.method === 'GET');
    req.flush(emptyFlush);
  });

  it('calls AuthService.logout() and navigates to /login', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigate');

    httpMock.expectOne(searchUrl).flush(emptyFlush);

    component.logout();

    const logoutReq = httpMock.expectOne(`${environment.apiBaseUrl}/auth/logout`);
    logoutReq.flush({ status: 200, message: 'Logged out successfully.', data: null });

    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });

  it('does not show the workspace section when not viewing a project', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(searchUrl).flush(emptyFlush);

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
    httpMock.expectOne(searchUrl).flush(emptyFlush);

    const router = TestBed.inject(Router);
    await router.navigateByUrl('/app/projects/p1/requirements');

    expect(component.isInProjectWorkspace()).toBe(true);
    expect(component.activeProjectIdValue()).toBe('p1');
  });
});
