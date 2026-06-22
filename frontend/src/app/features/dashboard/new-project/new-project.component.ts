import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ProjectService } from '../../../core/services/project.service';
import { GuidedQuestion } from '../../../core/models/project.models';
import { FormFieldComponent } from '../../../shared/components/form-field/form-field.component';

const MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024; // 20 MB, per API-PROJ-01
const ACCEPTED_FILE_TYPES = [
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document', // .docx
];

/**
 * New Project page — converted from `app.new.tsx`.
 *
 * Two steps in one component, since the contract's create-project response
 * always returns `guidedQuestions[]` that must be answered before the
 * project can proceed (the React mock never rendered this step — added
 * here per project decision):
 *
 *  Step 1 ("brief"): title + description + a single specification file,
 *  submitted as multipart/form-data to POST /projects (API-PROJ-01).
 *
 *  Step 2 ("questions"): the `guidedQuestions[]` from that response are
 *  rendered as a form; answers are submitted to
 *  PUT /projects/{projectId}/guided-questions (API-PROJ-07). On success,
 *  navigates to the project workspace.
 *
 * Scope note: the React mock's UI showed a *list* of multiple uploaded
 * files (two hardcoded filenames shown simultaneously), but the contract
 * only accepts exactly one `specificationFile` per project — per project
 * decision, this is rebuilt as a single file picker/dropzone rather than
 * a multi-file list, to match the contract exactly.
 */
@Component({
  selector: 'app-new-project-page',
  standalone: true,
  imports: [ReactiveFormsModule, FormFieldComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './new-project.component.html',
  styleUrl: './new-project.component.scss',
})
export class NewProjectComponent {
  readonly step = signal<'brief' | 'questions'>('brief');
  readonly isSubmitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly selectedFile = signal<File | null>(null);
  readonly fileError = signal<string | null>(null);

  readonly projectId = signal<string | null>(null);
  readonly guidedQuestions = signal<GuidedQuestion[]>([]);

  readonly briefForm = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.minLength(2)]],
    description: ['', [Validators.required, Validators.minLength(10)]],
  });

  /** One FormControl per guided question, built dynamically once Step 1 succeeds. */
  questionControls: Record<string, FormControl<string>> = {};

  constructor(
    private readonly fb: FormBuilder,
    private readonly projectService: ProjectService,
    private readonly router: Router,
  ) {}

  get titleError(): string | null {
    const control = this.briefForm.controls.title;
    if (!control.touched || control.valid) return null;
    if (control.hasError('required')) return 'Project name is required.';
    if (control.hasError('minlength')) return 'Project name must be at least 2 characters.';
    return null;
  }

  get descriptionError(): string | null {
    const control = this.briefForm.controls.description;
    if (!control.touched || control.valid) return null;
    if (control.hasError('required')) return 'A short description is required.';
    if (control.hasError('minlength')) return 'Tell us a bit more — at least 10 characters.';
    return null;
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.fileError.set(null);

    if (!file) {
      this.selectedFile.set(null);
      return;
    }

    if (!ACCEPTED_FILE_TYPES.includes(file.type)) {
      this.fileError.set('Only PDF or Word (.docx) documents are supported.');
      this.selectedFile.set(null);
      input.value = '';
      return;
    }

    if (file.size > MAX_FILE_SIZE_BYTES) {
      this.fileError.set('File is too large — the maximum size is 20 MB.');
      this.selectedFile.set(null);
      input.value = '';
      return;
    }

    this.selectedFile.set(file);
  }

  removeFile(): void {
    this.selectedFile.set(null);
    this.fileError.set(null);
  }

  onSubmitBrief(): void {
    this.errorMessage.set(null);

    if (this.briefForm.invalid) {
      this.briefForm.markAllAsTouched();
      return;
    }

    if (!this.selectedFile()) {
      this.fileError.set('Please attach a specification document to continue.');
      return;
    }

    this.isSubmitting.set(true);
    const { title, description } = this.briefForm.getRawValue();
    const specificationFile = this.selectedFile()!;

    this.projectService.create({ title, description, specificationFile }).subscribe({
      next: (data) => {
        this.isSubmitting.set(false);
        this.projectId.set(data.projectId);
        this.guidedQuestions.set(data.guidedQuestions);
        this.questionControls = Object.fromEntries(
          data.guidedQuestions.map((q) => [q.gqId, new FormControl('', { nonNullable: true, validators: [Validators.required] })]),
        );
        this.step.set('questions');
      },
      error: (error: HttpErrorResponse) => {
        this.isSubmitting.set(false);
        if (error.status === 422) {
          this.errorMessage.set(
            'We could not read that document. Please check the file and try again.',
          );
        } else if (error.status === 400) {
          this.errorMessage.set('Please check your project details and try again.');
        } else {
          this.errorMessage.set('Something went wrong. Please try again later.');
        }
      },
    });
  }

  onSubmitQuestions(): void {
    this.errorMessage.set(null);
    const currentProjectId = this.projectId();
    if (!currentProjectId) {
      this.errorMessage.set('Something went wrong — please start over.');
      return;
    }

    const controls = Object.values(this.questionControls);
    const isValid = controls.every((c) => c.valid);
    if (!isValid) {
      controls.forEach((c) => c.markAsTouched());
      return;
    }

    this.isSubmitting.set(true);
    const answers = Object.entries(this.questionControls).map(([gqId, control]) => ({
      gqId,
      answer: control.value,
    }));

    this.projectService.submitGuidedQuestions(currentProjectId, { answers }).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.router.navigate(['/app/projects', currentProjectId, 'requirements']);
      },
      error: (error: HttpErrorResponse) => {
        this.isSubmitting.set(false);
        if (error.status === 400) {
          this.errorMessage.set('One of your answers could not be saved. Please review and try again.');
        } else {
          this.errorMessage.set('Something went wrong. Please try again later.');
        }
      },
    });
  }
}
