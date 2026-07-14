import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, signal } from '@angular/core';
import { Router, ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription, timer, switchMap, takeWhile, catchError, of } from 'rxjs';
import { ProjectService } from '../../../core/services/project.service';
import { Project, ClarificationQuestion, SubmitAnswersResponseData } from '../../../core/models/project.models';
import { ToastService } from '../../../core/services/toast.service';
import { RequirementsViewComponent } from './requirements-view/requirements-view.component';
import { SystemDesignComponent } from './system-design/system-design.component';
import { VersionHistoryComponent } from './version-history/version-history.component';

export type WorkspaceSection =
  | 'requirements'
  | 'design'
  | 'documents'
  | 'code'
  | 'versions'
  | 'deploy'
  | 'review';

const SECTION_LABELS: Record<WorkspaceSection, string> = {
  requirements: 'Requirements',
  design: 'System Design',
  documents: 'Documentation',
  code: 'Code',
  versions: 'Version History',
  deploy: 'Deployment',
  review: 'Review',
};

@Component({
  selector: 'app-project-workspace-page',
  standalone: true,
  imports: [RouterLink, RequirementsViewComponent, SystemDesignComponent, VersionHistoryComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './project-workspace.component.html',
  styleUrl: './project-workspace.component.scss',
})
export class ProjectWorkspaceComponent implements OnInit, OnDestroy {
  readonly project = signal<Project | null>(null);
  readonly section = signal<WorkspaceSection | null>(null);
  readonly isLoading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly pcsfStatus = signal<string>('DRAFT');
  readonly pendingQuestionsCount = signal(0);

  readonly isRetrying = signal(false);
  readonly retryError = signal<string | null>(null);

  private projectId: string | null = null;
  private statusPollSub: Subscription | null = null;

  readonly showQuestionsModal = signal(false);
  readonly submittingAnswers = signal(false);
  readonly submitError = signal<string | null>(null);
  questions: ClarificationQuestion[] = [];
  currentIndex = 0;
  answers = new Map<string, string>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly projectService: ProjectService,
    private readonly toastService: ToastService,
  ) {}

  ngOnInit(): void {
    this.route.paramMap
      .pipe(
        switchMap((params) => {
          this.projectId = params.get('id');
          this.section.set(params.get('section') as WorkspaceSection | null);
          this.isLoading.set(true);
          this.loadError.set(null);
          return this.projectService.getById(params.get('id')!);
        }),
      )
      .subscribe({
        next: (project) => {
          this.project.set(project);
          this.isLoading.set(false);
          this.startStatusPolling();
        },
        error: (error: HttpErrorResponse) => {
          this.isLoading.set(false);
          this.loadError.set(
            error.status === 404
              ? 'This project could not be found.'
              : 'Could not load this project. Please try again later.',
          );
        },
      });
  }

  ngOnDestroy(): void {
    this.statusPollSub?.unsubscribe();
  }

  get currentSectionLabel(): string {
    const section = this.section();
    return section ? SECTION_LABELS[section] : '';
  }

  get showPageHeading(): boolean {
    const section = this.section();
    return this.isKnownSection && section !== 'requirements' && section !== 'review';
  }

  get breadcrumbSectionLabel(): string {
    const section = this.section();
    const status = this.pcsfStatus();
    if (section === 'requirements' && status !== 'VALIDATED' && status !== 'APPROVED') return 'Questions';
    if (section === 'review') return 'Requirements';
    return section ? SECTION_LABELS[section] : '';
  }

  get isKnownSection(): boolean {
    return this.section() !== null && this.section()! in SECTION_LABELS;
  }

  onRestartPolling(): void {
    if (this.projectId) this.startStatusPolling();
  }

  retryInference(): void {
    if (!this.projectId || this.isRetrying()) return;
    const pid = this.projectId;
    this.isRetrying.set(true);
    this.retryError.set(null);
    this.projectService
      .resetProjectStatus(pid)
      .pipe(switchMap(() => this.projectService.retryInference(pid)))
      .subscribe({
        next: () => {
          this.isRetrying.set(false);
          this.startStatusPolling();
        },
        error: (error: HttpErrorResponse) => {
          this.isRetrying.set(false);
          this.retryError.set(
            error.error?.error ?? 'Could not retry analysis. Please try again.',
          );
        },
      });
  }

  // ── Status polling ──────────────────────────────────────────────────────────

  private startStatusPolling(): void {
    if (!this.projectId) return;
    const pid = this.projectId;
    const terminalStatuses = ['UNDER_REVIEW', 'VALIDATED', 'APPROVED', 'FAILED'];

    this.statusPollSub?.unsubscribe();
    this.statusPollSub = timer(0, 5000)
      .pipe(
        switchMap(() =>
          this.projectService.getPcsfStatus(pid).pipe(
            catchError((err: HttpErrorResponse) => {
              if (err.status === 404) {
                return of({ pcsfStatus: 'DRAFT', pendingQuestionsCount: 0, completenessScore: 0 });
              }
              throw err;
            }),
          ),
        ),
        takeWhile((response) => !terminalStatuses.includes(response.pcsfStatus), true),
      )
      .subscribe({
        next: (response) => {
          this.pcsfStatus.set(response.pcsfStatus);
          this.pendingQuestionsCount.set(response.pendingQuestionsCount);

          // Once requirements are approved (or already validated), clarification
          // questions are moot — never reopen the modal or redirect away, even if
          // pendingQuestionsCount is stale, so approving keeps the user on this page.
          if (response.pcsfStatus === 'APPROVED' || response.pcsfStatus === 'VALIDATED') {
            return;
          }

          if (response.pcsfStatus === 'UNDER_REVIEW' && response.pendingQuestionsCount > 0) {
            if (!this.showQuestionsModal()) this.openQuestionsModal();
          } else if (response.pcsfStatus === 'UNDER_REVIEW' && this.section() !== 'review') {
            this.router.navigate(['/app/projects', pid, 'review']);
          } else if (response.pcsfStatus === 'FAILED') {
            this.toastService.show(
              'Could not analyse your document. Please try again.',
              'error',
            );
          }
        },
        error: (err) => {
          console.error('Status polling failed', err);
        },
      });
  }

  // ── Questions modal ─────────────────────────────────────────────────────────

  openQuestionsModal(): void {
    if (!this.projectId) return;
    this.projectService.getQuestions(this.projectId).subscribe({
      next: (questions) => {
        if (questions.length > 0) {
          this.questions = questions;
          this.currentIndex = 0;
          this.answers = new Map<string, string>();
          this.submitError.set(null);
          this.showQuestionsModal.set(true);
        }
      },
      error: () => {
        this.toastService.show('Failed to load questions. Please try again.', 'error');
      },
    });
  }

  get currentQuestion(): ClarificationQuestion {
    return this.questions[this.currentIndex];
  }

  get isFirst(): boolean { return this.currentIndex === 0; }
  get isLast(): boolean { return this.currentIndex === this.questions.length - 1; }
  get currentAnswer(): string { return this.answers.get(this.currentQuestion.id) ?? ''; }
  get progressPercent(): number {
    return ((this.currentIndex + 1) / this.questions.length) * 100;
  }

  setAnswer(value: string): void { this.answers.set(this.currentQuestion.id, value); }
  onInput(event: Event): void { this.setAnswer((event.target as HTMLInputElement).value); }
  onTextareaInput(event: Event): void { this.setAnswer((event.target as HTMLTextAreaElement).value); }
  goBack(): void { if (!this.isFirst) this.currentIndex--; }
  goNext(): void { if (!this.isLast) this.currentIndex++; }

  toggleMultiSelect(option: string): void {
    const current = this.getMultiSelectOptions();
    const idx = current.indexOf(option);
    if (idx >= 0) current.splice(idx, 1); else current.push(option);
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
    const pid = this.projectId;
    if (!pid) {
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
    this.projectService.submitAnswers(pid, payload).subscribe({
      next: (response: SubmitAnswersResponseData) => {
        this.submittingAnswers.set(false);
        this.pcsfStatus.set(response.pcsfStatus);
        this.pendingQuestionsCount.set(response.pendingQuestionsCount);

        if (response.pendingQuestionsCount > 0) {
          this.openQuestionsModal();
        } else {
          this.showQuestionsModal.set(false);
          this.startStatusPolling();
        }
      },
      error: () => {
        this.submittingAnswers.set(false);
        this.submitError.set('Something went wrong. Please try again.');
      },
    });
  }
}
