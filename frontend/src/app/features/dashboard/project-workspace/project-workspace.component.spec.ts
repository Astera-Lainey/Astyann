import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { ProjectWorkspaceComponent } from './project-workspace.component';
import { environment } from '../../../../environments/environment';

describe('ProjectWorkspaceComponent', () => {
  async function setup(id = 'p1', section = 'requirements') {
    await TestBed.configureTestingModule({
      imports: [ProjectWorkspaceComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({ id, section })),
          },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ProjectWorkspaceComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    return { component, fixture };
  }

  const mockProject = {
    projectId: 'p1',
    title: 'Treasury Ledger',
    description: 'Core banking treasury system.',
    status: 'ANALYZING',
    createdAt: '2026-06-01T00:00:00Z',
    updatedAt: '2026-06-20T00:00:00Z',
    docPath: '/storage/p1/spec.pdf',
    docUrl: 'https://cdn.astyann.com/p1/spec.pdf',
  };

  it('requests GET /projects/{id} using the id route param', async () => {
    await setup('p1', 'requirements');
    const httpMock = TestBed.inject(HttpTestingController);

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/projects/p1`);
    expect(req.request.method).toBe('GET');
    req.flush({ status: 200, message: 'OK', data: mockProject });
  });

  it('sets section from the route param and resolves a known label', async () => {
    const { component } = await setup('p1', 'design');
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/p1`).flush({
      status: 200,
      message: 'OK',
      data: mockProject,
    });

    expect(component.section()).toBe('design');
    expect(component.currentSectionLabel).toBe('System Design');
    expect(component.isKnownSection).toBe(true);
  });

  it('flags an unrecognized section as not known', async () => {
    const { component } = await setup('p1', 'not-a-real-section');
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/p1`).flush({
      status: 200,
      message: 'OK',
      data: mockProject,
    });

    expect(component.isKnownSection).toBe(false);
  });

  it('sets a not-found error message on a 404', async () => {
    const { component } = await setup('missing-id', 'requirements');
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/projects/missing-id`)
      .flush({ status: 404, message: 'Project not found.', data: null }, { status: 404, statusText: 'Not Found' });

    expect(component.loadError()).toBe('This project could not be found.');
    expect(component.isLoading()).toBe(false);
  });
});
