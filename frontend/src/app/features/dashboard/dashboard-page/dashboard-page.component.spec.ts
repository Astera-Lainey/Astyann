import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { DashboardPageComponent } from './dashboard-page.component';
import { environment } from '../../../../environments/environment';

describe('DashboardPageComponent', () => {
  async function setup() {
    await TestBed.configureTestingModule({
      imports: [DashboardPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    const fixture = TestBed.createComponent(DashboardPageComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    return { component, fixture };
  }

  const searchUrl = `${environment.apiBaseUrl}/projects/search`;

  it('requests GET /projects/search on init', async () => {
    await setup();
    const httpMock = TestBed.inject(HttpTestingController);

    const req = httpMock.expectOne((r) => r.url === searchUrl && r.method === 'GET');
    req.flush({ status: 200, message: 'OK', data: [] });
  });

  it('derives totalProjects and analyzingCount from the loaded list', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);

    httpMock.expectOne(searchUrl).flush({
      status: 200,
      message: 'OK',
      data: [
        {
          projectId: 'p1',
          userId: 'u1',
          title: 'Treasury Ledger',
          description: 'Core banking treasury system.',
          status: 'ANALYZING',
          creationDate: '2026-06-01T00:00:00',
          updatedDate: '2026-06-20T00:00:00',
        },
        {
          projectId: 'p2',
          userId: 'u1',
          title: 'Ledger Flow',
          description: 'General ledger engine.',
          status: 'ANALYZING',
          creationDate: '2026-06-02T00:00:00',
          updatedDate: '2026-06-19T00:00:00',
        },
      ],
    });

    expect(component.totalProjects()).toBe(2);
    expect(component.analyzingCount()).toBe(2);
    expect(component.isLoading()).toBe(false);
  });

  it('sets a load error message when the request fails', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);

    httpMock
      .expectOne(searchUrl)
      .flush({ status: 500, message: 'Internal error.', data: null }, { status: 500, statusText: 'Server Error' });

    expect(component.loadError()).toBe('Could not load your projects. Please try again later.');
    expect(component.isLoading()).toBe(false);
  });

  it('derives a stable uppercase initial for a project avatar', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(searchUrl).flush({ status: 200, message: 'OK', data: [] });

    expect(component.initialFor('treasury ledger')).toBe('T');
  });
});
