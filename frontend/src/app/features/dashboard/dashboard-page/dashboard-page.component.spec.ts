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

  it('requests GET /projects on init', async () => {
    await setup();
    const httpMock = TestBed.inject(HttpTestingController);

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiBaseUrl}/projects` && r.method === 'GET',
    );
    req.flush({
      status: 200,
      message: 'OK',
      data: { content: [], totalElements: 0, totalPages: 0, currentPage: 0, pageSize: 20 },
    });
  });

  it('derives totalProjects and analyzingCount from the loaded list', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);

    httpMock.expectOne(`${environment.apiBaseUrl}/projects`).flush({
      status: 200,
      message: 'OK',
      data: {
        content: [
          {
            projectId: 'p1',
            title: 'Treasury Ledger',
            description: 'Core banking treasury system.',
            status: 'ANALYZING',
            createdAt: '2026-06-01T00:00:00Z',
            updatedAt: '2026-06-20T00:00:00Z',
          },
          {
            projectId: 'p2',
            title: 'Ledger Flow',
            description: 'General ledger engine.',
            status: 'ANALYZING',
            createdAt: '2026-06-02T00:00:00Z',
            updatedAt: '2026-06-19T00:00:00Z',
          },
        ],
        totalElements: 2,
        totalPages: 1,
        currentPage: 0,
        pageSize: 20,
      },
    });

    expect(component.totalProjects()).toBe(2);
    expect(component.analyzingCount()).toBe(2);
    expect(component.isLoading()).toBe(false);
  });

  it('sets a load error message when the request fails', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);

    httpMock
      .expectOne(`${environment.apiBaseUrl}/projects`)
      .flush({ status: 500, message: 'Internal error.', data: null }, { status: 500, statusText: 'Server Error' });

    expect(component.loadError()).toBe('Could not load your projects. Please try again later.');
    expect(component.isLoading()).toBe(false);
  });

  it('derives a stable uppercase initial for a project avatar', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(`${environment.apiBaseUrl}/projects`).flush({
      status: 200,
      message: 'OK',
      data: { content: [], totalElements: 0, totalPages: 0, currentPage: 0, pageSize: 20 },
    });

    expect(component.initialFor('treasury ledger')).toBe('T');
  });
});
