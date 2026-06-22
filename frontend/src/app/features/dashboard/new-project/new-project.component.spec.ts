import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { NewProjectComponent } from './new-project.component';
import { environment } from '../../../../environments/environment';

describe('NewProjectComponent', () => {
  async function setup() {
    await TestBed.configureTestingModule({
      imports: [NewProjectComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    const fixture = TestBed.createComponent(NewProjectComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    return { component, fixture };
  }

  function pdfFile(name = 'spec.pdf', sizeBytes = 1024): File {
    const file = new File(['%PDF-1.4'], name, { type: 'application/pdf' });
    Object.defineProperty(file, 'size', { value: sizeBytes });
    return file;
  }

  it('rejects an unsupported file type', async () => {
    const { component } = await setup();
    const file = new File(['hello'], 'notes.txt', { type: 'text/plain' });
    const event = { target: { files: [file], value: '' } } as unknown as Event;

    component.onFileSelected(event);

    expect(component.fileError()).toBe('Only PDF or Word (.docx) documents are supported.');
    expect(component.selectedFile()).toBeNull();
  });

  it('rejects a file over 20MB', async () => {
    const { component } = await setup();
    const file = pdfFile('big.pdf', 21 * 1024 * 1024);
    const event = { target: { files: [file], value: '' } } as unknown as Event;

    component.onFileSelected(event);

    expect(component.fileError()).toBe('File is too large — the maximum size is 20 MB.');
    expect(component.selectedFile()).toBeNull();
  });

  it('accepts a valid PDF under the size limit', async () => {
    const { component } = await setup();
    const file = pdfFile();
    const event = { target: { files: [file], value: '' } } as unknown as Event;

    component.onFileSelected(event);

    expect(component.fileError()).toBeNull();
    expect(component.selectedFile()).toBe(file);
  });

  it('does not submit the brief without a selected file', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);
    component.briefForm.setValue({ title: 'Orbit CRM', description: 'A CRM for boutique agencies.' });

    component.onSubmitBrief();

    httpMock.expectNone(`${environment.apiBaseUrl}/projects`);
    expect(component.fileError()).toBe('Please attach a specification document to continue.');
  });

  it('submits the brief, then renders guided questions on success', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);

    component.briefForm.setValue({ title: 'Orbit CRM', description: 'A CRM for boutique agencies.' });
    component.onFileSelected({ target: { files: [pdfFile()], value: '' } } as unknown as Event);
    component.onSubmitBrief();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/projects`);
    expect(req.request.method).toBe('POST');
    req.flush({
      status: 201,
      message: 'Project created.',
      data: {
        projectId: 'p1',
        title: 'Orbit CRM',
        status: 'ANALYZING',
        createdAt: '2026-06-12T08:00:00Z',
        guidedQuestions: [{ gqId: 'gq-001', question: 'Which database engine should be used?' }],
      },
    });

    expect(component.step()).toBe('questions');
    expect(component.guidedQuestions().length).toBe(1);
    expect(component.questionControls['gq-001']).toBeTruthy();
  });

  it('submits guided question answers and navigates to the project workspace', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigate');

    component.briefForm.setValue({ title: 'Orbit CRM', description: 'A CRM for boutique agencies.' });
    component.onFileSelected({ target: { files: [pdfFile()], value: '' } } as unknown as Event);
    component.onSubmitBrief();
    httpMock.expectOne(`${environment.apiBaseUrl}/projects`).flush({
      status: 201,
      message: 'Project created.',
      data: {
        projectId: 'p1',
        title: 'Orbit CRM',
        status: 'ANALYZING',
        createdAt: '2026-06-12T08:00:00Z',
        guidedQuestions: [{ gqId: 'gq-001', question: 'Which database engine should be used?' }],
      },
    });

    component.questionControls['gq-001'].setValue('PostgreSQL');
    component.onSubmitQuestions();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/projects/p1/guided-questions`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ answers: [{ gqId: 'gq-001', answer: 'PostgreSQL' }] });
    req.flush({ status: 200, message: 'Answers saved.', data: { projectId: 'p1', status: 'ANALYZING' } });

    expect(navigateSpy).toHaveBeenCalledWith(['/app/projects', 'p1', 'requirements']);
  });

  it('does not submit guided questions if any answer is empty', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);

    component.briefForm.setValue({ title: 'Orbit CRM', description: 'A CRM for boutique agencies.' });
    component.onFileSelected({ target: { files: [pdfFile()], value: '' } } as unknown as Event);
    component.onSubmitBrief();
    httpMock.expectOne(`${environment.apiBaseUrl}/projects`).flush({
      status: 201,
      message: 'Project created.',
      data: {
        projectId: 'p1',
        title: 'Orbit CRM',
        status: 'ANALYZING',
        createdAt: '2026-06-12T08:00:00Z',
        guidedQuestions: [{ gqId: 'gq-001', question: 'Which database engine should be used?' }],
      },
    });

    component.onSubmitQuestions();

    httpMock.expectNone(`${environment.apiBaseUrl}/projects/p1/guided-questions`);
    expect(component.questionControls['gq-001'].touched).toBe(true);
  });
});
