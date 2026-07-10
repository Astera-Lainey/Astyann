import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ProjectService } from '../../../core/services/project.service';
import { ToastService } from '../../../core/services/toast.service';
import { FormFieldComponent } from '../../../shared/components/form-field/form-field.component';
import { SparkleIconComponent } from '../../../shared/components/sparkle-icon/sparkle-icon.component';

const MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024;
const ACCEPTED_FILE_TYPES = [
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
];

@Component({
  selector: 'app-new-project-page',
  standalone: true,
  imports: [ReactiveFormsModule, FormFieldComponent, SparkleIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './new-project.component.html',
  styleUrl: './new-project.component.scss',
})
export class NewProjectComponent {
  private readonly fb = inject(FormBuilder);
  private readonly projectService = inject(ProjectService);
  private readonly router = inject(Router);
  private readonly toastService = inject(ToastService);

  readonly isSubmitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly selectedFile = signal<File | null>(null);
  readonly fileError = signal<string | null>(null);
  readonly projectId = signal<string | null>(null);

  readonly briefForm = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.minLength(2)]],
    description: ['', [Validators.required, Validators.minLength(10)]],
  });

  // ── Template download ──
  downloadTemplate(): void {
    const a = document.createElement('a');
    a.href = '/assets/AstyannTemplate.docx';
    a.download = 'AstyannTemplate.docx';
    a.click();
  }

  // ── Getters ──
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
      next: (project) => {
        this.isSubmitting.set(false);
        this.toastService.show('Your document is being analysed. This may take a few minutes.');
        this.router.navigate(['/app/projects', project.projectId, 'requirements']);
      },
      error: (error: HttpErrorResponse) => {
        this.isSubmitting.set(false);
        if (error.status === 400) {
          this.errorMessage.set('We could not read that document. Please check the file and try again.');
        } else {
          this.errorMessage.set('Something went wrong. Please try again later.');
        }
      },
    });
  }
}
