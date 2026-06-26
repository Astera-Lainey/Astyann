import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs/operators';
import { ProjectService } from '../../../core/services/project.service';
import { ToastService } from '../../../core/services/toast.service';
import {
  ClarificationQuestion,
  GuidedQuestion,
} from '../../../core/models/project.models';
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

  // ── Template download ──
  readonly isDownloading = signal(false);

  downloadTemplate(): void {
    this.isDownloading.set(true);
    this.projectService.downloadTemplate().pipe(
      finalize(() => this.isDownloading.set(false)),
    ).subscribe({
      next: (blob) => {
        if (blob.type !== 'application/vnd.openxmlformats-officedocument.wordprocessingml.document') {
          this.errorMessage.set('The template file is corrupted or has an unexpected format.');
          return;
        }
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'AstyannTemplate.docx';
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => {
        this.errorMessage.set('Failed to download template. Please try again.');
      },
    });
  }

  // ── Q&A Modal (PCSF clarification questions) ──
  readonly showQuestionsModal = signal(false);
  readonly submittingAnswers = signal(false);
  readonly submitError = signal<string | null>(null);

  questions: ClarificationQuestion[] = [];
  currentIndex = 0;
  answers = new Map<string, string>();

  get currentQuestion(): ClarificationQuestion {
    return this.questions[this.currentIndex];
  }

  get isFirst(): boolean {
    return this.currentIndex === 0;
  }

  get isLast(): boolean {
    return this.currentIndex === this.questions.length - 1;
  }

  get currentAnswer(): string {
    return this.answers.get(this.currentQuestion.id) ?? '';
  }

  get progressPercent(): number {
    return ((this.currentIndex + 1) / this.questions.length) * 100;
  }

  setAnswer(value: string): void {
    this.answers.set(this.currentQuestion.id, value);
  }

  onInput(event: Event): void {
    this.setAnswer((event.target as HTMLInputElement).value);
  }

  onTextareaInput(event: Event): void {
    this.setAnswer((event.target as HTMLTextAreaElement).value);
  }

  goBack(): void {
    if (!this.isFirst) this.currentIndex--;
  }

  goNext(): void {
    if (!this.isLast) this.currentIndex++;
  }

  toggleMultiSelect(option: string): void {
    const current = this.getMultiSelectOptions();
    const idx = current.indexOf(option);
    if (idx >= 0) {
      current.splice(idx, 1);
    } else {
      current.push(option);
    }
    this.answers.set(this.currentQuestion.id, current.join(','));
  }

  getMultiSelectOptions(): string[] {
    return (this.answers.get(this.currentQuestion.id) ?? '').split(',').filter(Boolean);
  }

  isMultiSelected(option: string): boolean {
    return this.getMultiSelectOptions().includes(option);
  }

  submitAnswers(): void {
    this.submitError.set(null);
    this.submittingAnswers.set(true);
    const currentProjectId = this.projectId();
    if (!currentProjectId) {
      this.submitError.set('Something went wrong — please start over.');
      this.submittingAnswers.set(false);
      return;
    }
    const payload = {
      answers: Array.from(this.answers.entries()).map(([questionId, answer]) => ({
        questionId,
        answer,
      })),
    };
    this.projectService.submitAnswers(currentProjectId, payload).subscribe({
      next: () => {
        this.submittingAnswers.set(false);
        this.showQuestionsModal.set(false);
        this.router.navigate(['/app/projects', currentProjectId, 'review']);
      },
      error: () => {
        this.submittingAnswers.set(false);
        this.submitError.set('Something went wrong. Please try again.');
      },
    });
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

  private openQuestionsModal(projectId: string): void {
    this.showQuestionsModal.set(true);
    this.projectService.getQuestions(projectId).subscribe({
      next: (questions) => {
        this.questions = questions;
        this.currentIndex = 0;
        this.answers = new Map<string, string>();
        this.submitError.set(null);
      },
      error: () => {
        this.showQuestionsModal.set(false);
        this.errorMessage.set('Failed to load questions. Please try again.');
      },
    });
  }

  private loadGuidedQuestions(projectId: string): void {
    this.projectService.getGuidedQuestions(projectId).subscribe({
      next: (questions) => {
        if (questions.length > 0) {
          this.guidedQuestions.set(questions);
          this.questionControls = Object.fromEntries(
            questions.map((q) => [
              q.gqId,
              new FormControl('', { nonNullable: true, validators: [Validators.required] }),
            ]),
          );
          this.step.set('questions');
        } else {
          this.router.navigate(['/app/projects', projectId, 'requirements']);
        }
      },
      error: () => {
        // No guided questions available — navigate directly to workspace.
        this.router.navigate(['/app/projects', projectId, 'requirements']);
      },
    });
  }
}
