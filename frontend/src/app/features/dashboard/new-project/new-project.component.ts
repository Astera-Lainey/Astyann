import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ProjectService } from '../../../core/services/project.service';
import { GuidedQuestion } from '../../../core/models/project.models';
import { FormFieldComponent } from '../../../shared/components/form-field/form-field.component';

const MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024;
const ACCEPTED_FILE_TYPES = [
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
];

@Component({
  selector: 'app-new-project-page',
  standalone: true,
  imports: [ReactiveFormsModule, FormFieldComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './new-project.component.html',
  styleUrl: './new-project.component.scss',
})
export class NewProjectComponent {
  private readonly fb = inject(FormBuilder);
  private readonly projectService = inject(ProjectService);
  private readonly router = inject(Router);

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

  questionControls: Record<string, FormControl<string>> = {};

  get titleError(): string | null {
    const c = this.briefForm.controls.title;
    if (!c.touched || c.valid) return null;
    if (c.hasError('required')) return 'Project name is required.';
    if (c.hasError('minlength')) return 'Project name must be at least 2 characters.';
    return null;
  }

  get descriptionError(): string | null {
    const c = this.briefForm.controls.description;
    if (!c.touched || c.valid) return null;
    if (c.hasError('required')) return 'A short description is required.';
    if (c.hasError('minlength')) return 'Tell us a bit more — at least 10 characters.';
    return null;
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.fileError.set(null);
    if (!file) { this.selectedFile.set(null); return; }
    if (!ACCEPTED_FILE_TYPES.includes(file.type)) {
      this.fileError.set('Only PDF or Word (.docx) documents are supported.');
      this.selectedFile.set(null); input.value = ''; return;
    }
    if (file.size > MAX_FILE_SIZE_BYTES) {
      this.fileError.set('File is too large — the maximum size is 20 MB.');
      this.selectedFile.set(null); input.value = ''; return;
    }
    this.selectedFile.set(file);
  }

  removeFile(): void {
    this.selectedFile.set(null);
    this.fileError.set(null);
  }

  onSubmitBrief(): void {
    this.errorMessage.set(null);
    if (this.briefForm.invalid) { this.briefForm.markAllAsTouched(); return; }
    if (!this.selectedFile()) { this.fileError.set('Please attach a specification document to continue.'); return; }
    this.isSubmitting.set(true);
    const { title, description } = this.briefForm.getRawValue();
    this.projectService.create({ title, description, specificationFile: this.selectedFile()! }).subscribe({
      next: (data) => {
        this.isSubmitting.set(false);
        this.projectId.set(data.projectId);
        this.guidedQuestions.set(data.guidedQuestions);
        this.questionControls = Object.fromEntries(
          data.guidedQuestions.map((q) => [
            q.gqId,
            new FormControl('', { nonNullable: true, validators: [Validators.required] }),
          ]),
        );
        this.step.set('questions');
      },
      error: (error: HttpErrorResponse) => {
        this.isSubmitting.set(false);
        if (error.status === 422) {
          this.errorMessage.set('We could not read that document. Please check the file and try again.');
        } else {
          this.errorMessage.set('Something went wrong. Please try again later.');
        }
      },
    });
  }

  onSubmitQuestions(): void {
    this.errorMessage.set(null);
    const currentProjectId = this.projectId();
    if (!currentProjectId) { this.errorMessage.set('Something went wrong — please start over.'); return; }
    const controls = Object.values(this.questionControls);
    if (!controls.every((c) => c.valid)) { controls.forEach((c) => c.markAsTouched()); return; }
    this.isSubmitting.set(true);
    const answers = Object.entries(this.questionControls).map(([gqId, control]) => ({ gqId, answer: control.value }));
    this.projectService.submitGuidedQuestions(currentProjectId, { answers }).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.router.navigate(['/app/projects', currentProjectId, 'requirements']);
      },
      error: () => {
        this.isSubmitting.set(false);
        this.errorMessage.set('Something went wrong. Please try again later.');
      },
    });
  }
}