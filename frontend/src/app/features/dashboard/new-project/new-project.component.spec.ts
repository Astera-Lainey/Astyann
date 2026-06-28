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

  const baseUrl = environment.apiBaseUrl;

  // Minimal ProjectDTO response from POST /projects
  const createdProjectFlush = {
    status: 201,
    message: 'Project created successfully.',
    data: {
      projectId: 'p1',
      userId: 'u1',
      title: 'Orbit CRM',
      description: 'A CRM for boutique agencies.',
      status: 'ANALYZING',
      creationDate: '2026-06-12T08:00:00',
      updatedDate: null,
    },
  };

  // PCSF status: no pending questions → fall through to guided-questions
  const pcsfStatusNoQuestionsFlush = {
    status: 200,
    message: 'Status retrieved.',
    data: { pcsfStatus: 'DRAFT', completenessScore: 0, pendingQuestionsCount: 0 },
  };

  // PCSF status: has pending clarification questions → open PCSF modal
  const pcsfStatusWithQuestionsFlush = {
    status: 200,
    message: 'Status retrieved.',
    data: { pcsfStatus: 'DRAFT', completenessScore: 0, pendingQuestionsCount: 2 },
  };

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

    httpMock.expectNone(`${baseUrl}/projects`);
    expect(component.fileError()).toBe('Please attach a specification document to continue.');
  });

  it('sends document as "document" form field (not "specificationFile")', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);

    component.briefForm.setValue({ title: 'Orbit CRM', description: 'A CRM for boutique agencies.' });
    component.onFileSelected({ target: { files: [pdfFile()], value: '' } } as unknown as Event);
    component.onSubmitBrief();

    const req = httpMock.expectOne(`${baseUrl}/projects`);
    expect(req.request.method).toBe('POST');
    const formData: FormData = req.request.body;
    expect(formData.get('document')).toBeTruthy();
    expect(formData.get('specificationFile')).toBeNull();

    req.flush(createdProjectFlush);
    httpMock.expectOne(`${baseUrl}/projects/p1/pcsf/status`).flush(pcsfStatusNoQuestionsFlush);
    httpMock.expectOne(`${baseUrl}/projects/p1/guided-questions`).flush({ status: 200, message: 'OK', data: [] });
  });

  it('shows guided questions (step 2) when PCSF has no pending questions and legacy questions exist', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);

    component.briefForm.setValue({ title: 'Orbit CRM', description: 'A CRM for boutique agencies.' });
    component.onFileSelected({ target: { files: [pdfFile()], value: '' } } as unknown as Event);
    component.onSubmitBrief();

    httpMock.expectOne(`${baseUrl}/projects`).flush(createdProjectFlush);
    httpMock.expectOne(`${baseUrl}/projects/p1/pcsf/status`).flush(pcsfStatusNoQuestionsFlush);
    httpMock.expectOne(`${baseUrl}/projects/p1/guided-questions`).flush({
      status: 200,
      message: 'OK',
      data: [{ gqId: 'gq-001', question: 'Which database engine should be used?', answer: null }],
    });

    expect(component.step()).toBe('questions');
    expect(component.guidedQuestions().length).toBe(1);
    expect(component.questionControls['gq-001']).toBeTruthy();
  });

  it('opens the PCSF modal when pcsfStatus is DRAFT with pending questions', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);

    component.briefForm.setValue({ title: 'Orbit CRM', description: 'A CRM for boutique agencies.' });
    component.onFileSelected({ target: { files: [pdfFile()], value: '' } } as unknown as Event);
    component.onSubmitBrief();

    httpMock.expectOne(`${baseUrl}/projects`).flush(createdProjectFlush);
    httpMock.expectOne(`${baseUrl}/projects/p1/pcsf/status`).flush(pcsfStatusWithQuestionsFlush);
    httpMock.expectOne(`${baseUrl}/projects/p1/questions`).flush({ status: 200, message: 'OK', data: [] });

    expect(component.showQuestionsModal()).toBe(true);
  });

  it('navigates to requirements when no guided questions exist after creation', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigate');

    component.briefForm.setValue({ title: 'Orbit CRM', description: 'A CRM for boutique agencies.' });
    component.onFileSelected({ target: { files: [pdfFile()], value: '' } } as unknown as Event);
    component.onSubmitBrief();

    httpMock.expectOne(`${baseUrl}/projects`).flush(createdProjectFlush);
    httpMock.expectOne(`${baseUrl}/projects/p1/pcsf/status`).flush(pcsfStatusNoQuestionsFlush);
    httpMock.expectOne(`${baseUrl}/projects/p1/guided-questions`).flush({ status: 200, message: 'OK', data: [] });

    expect(navigateSpy).toHaveBeenCalledWith(['/app/projects', 'p1', 'requirements']);
  });

  it('submits guided question answers via POST to /guided-questions/answers and navigates', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigate');

    component.briefForm.setValue({ title: 'Orbit CRM', description: 'A CRM for boutique agencies.' });
    component.onFileSelected({ target: { files: [pdfFile()], value: '' } } as unknown as Event);
    component.onSubmitBrief();

    httpMock.expectOne(`${baseUrl}/projects`).flush(createdProjectFlush);
    httpMock.expectOne(`${baseUrl}/projects/p1/pcsf/status`).flush(pcsfStatusNoQuestionsFlush);
    httpMock.expectOne(`${baseUrl}/projects/p1/guided-questions`).flush({
      status: 200,
      message: 'OK',
      data: [{ gqId: 'gq-001', question: 'Which database engine should be used?', answer: null }],
    });

    component.questionControls['gq-001'].setValue('PostgreSQL');
    component.onSubmitQuestions();

    const req = httpMock.expectOne(`${baseUrl}/projects/p1/guided-questions/answers`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ answers: [{ gqId: 'gq-001', answer: 'PostgreSQL' }] });
    req.flush({ status: 200, message: 'Answers submitted.', data: { projectId: 'p1', status: 'ANALYZING' } });

    expect(navigateSpy).toHaveBeenCalledWith(['/app/projects', 'p1', 'requirements']);
  });

  it('does not submit guided questions if any answer is empty', async () => {
    const { component } = await setup();
    const httpMock = TestBed.inject(HttpTestingController);

    component.briefForm.setValue({ title: 'Orbit CRM', description: 'A CRM for boutique agencies.' });
    component.onFileSelected({ target: { files: [pdfFile()], value: '' } } as unknown as Event);
    component.onSubmitBrief();

    httpMock.expectOne(`${baseUrl}/projects`).flush(createdProjectFlush);
    httpMock.expectOne(`${baseUrl}/projects/p1/pcsf/status`).flush(pcsfStatusNoQuestionsFlush);
    httpMock.expectOne(`${baseUrl}/projects/p1/guided-questions`).flush({
      status: 200,
      message: 'OK',
      data: [{ gqId: 'gq-001', question: 'Which database engine should be used?', answer: null }],
    });

    component.onSubmitQuestions();

    httpMock.expectNone(`${baseUrl}/projects/p1/guided-questions/answers`);
    expect(component.questionControls['gq-001'].touched).toBe(true);
  });
});
