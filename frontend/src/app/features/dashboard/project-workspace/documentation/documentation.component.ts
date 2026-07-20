import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  computed,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription, catchError, forkJoin, map, of, timer, switchMap, takeWhile } from 'rxjs';
import { DocumentService } from '../../../../core/services/document.service';
import { ToastService } from '../../../../core/services/toast.service';
import {
  DOCUMENT_TYPE_LABELS,
  DocumentListItem,
  DocumentStatus,
  DocumentSummary,
} from '../../../../core/models/document.models';

const STATUS_LABELS: Record<DocumentStatus, string> = {
  GENERATING: 'Generating',
  PENDING_APPROVAL: 'Pending Approval',
  APPROVED: 'Approved',
  FAILED: 'Failed',
};

@Component({
  selector: 'app-documentation',
  standalone: true,
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './documentation.html',
  styleUrl: './documentation.scss',
})
export class DocumentationComponent implements OnChanges, OnDestroy {
  @Input({ required: true }) projectId!: string;

  readonly documentTypeLabels = DOCUMENT_TYPE_LABELS;
  readonly statusLabels = STATUS_LABELS;

  // ── List ──────────────────────────────────────────────────────────────────
  readonly documents = signal<DocumentListItem[]>([]);
  readonly isLoadingList = signal(true);
  readonly listError = signal<string | null>(null);

  readonly hasAnyDocuments = computed(() => this.documents().length > 0);
  readonly hasPendingApproval = computed(() => this.documents().some((d) => d.status === 'PENDING_APPROVAL'));
  readonly allFailed = computed(
    () => this.hasAnyDocuments() && this.documents().every((d) => d.status === 'FAILED'),
  );

  // ── Generate ──────────────────────────────────────────────────────────────
  readonly isGenerating = signal(false);
  readonly generateError = signal<string | null>(null);
  readonly partialFailureNotice = signal<string | null>(null);

  // ── Approve all ───────────────────────────────────────────────────────────
  readonly isApprovingAll = signal(false);
  readonly approveError = signal<string | null>(null);

  // ── Per-row 3-dot menu ────────────────────────────────────────────────────
  readonly openMenuId = signal<string | null>(null);

  @HostListener('document:click')
  onDocumentClick(): void {
    this.openMenuId.set(null);
  }

  toggleMenu(documentId: string, event: MouseEvent): void {
    event.stopPropagation();
    this.openMenuId.set(this.openMenuId() === documentId ? null : documentId);
  }

  closeMenu(): void {
    this.openMenuId.set(null);
  }

  // ── Per-row approve (PENDING_APPROVAL → APPROVED) ────────────────────────
  readonly approvingId = signal<string | null>(null);

  // ── Per-row retry (FAILED → regenerate) ──────────────────────────────────
  readonly regeneratingId = signal<string | null>(null);

  // ── Per-row download ──────────────────────────────────────────────────────
  readonly downloadingId = signal<string | null>(null);
  readonly downloadError = signal<string | null>(null);

  // ── Per-row "Request changes" panel ──────────────────────────────────────
  readonly openChangeId = signal<string | null>(null);
  readonly changeInstruction = signal('');
  readonly changeError = signal<string | null>(null);
  readonly submittingChangeId = signal<string | null>(null);
  readonly canSubmitChange = computed(() => this.changeInstruction().trim().length > 0);

  private lastLoadedProjectId: string | null = null;
  private pollSub: Subscription | null = null;

  constructor(
    private readonly documentService: DocumentService,
    private readonly toastService: ToastService,
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.projectId) return;
    if (changes['projectId'] && this.projectId !== this.lastLoadedProjectId) {
      this.lastLoadedProjectId = this.projectId;
      this.load();
    }
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }

  // ── Load ──────────────────────────────────────────────────────────────────

  private load(): void {
    this.isLoadingList.set(true);
    this.listError.set(null);
    this.documentService.list(this.projectId).subscribe({
      next: (items) => {
        this.documents.set(items);
        this.isLoadingList.set(false);
        // Generation may already be in progress from an earlier visit (e.g.
        // the user reloaded the page mid-generation) — resume polling.
        if (items.some((d) => d.status === 'GENERATING')) {
          this.pollGenerationUntilSettled();
        }
      },
      error: () => {
        this.isLoadingList.set(false);
        this.listError.set('Could not load your documents. Please try again.');
      },
    });
  }

  retryLoadDocuments(): void {
    this.load();
  }

  private toListVm = (s: DocumentSummary): DocumentListItem => ({
    documentId: s.documentId,
    type: s.type,
    status: s.status,
    version: 1,
    generatedAt: null,
    lastError: s.lastError,
  });

  // ── Generate (empty-state / all-failed flow) ─────────────────────────────

  generateDocuments(): void {
    if (this.isGenerating()) return;
    this.isGenerating.set(true);
    this.generateError.set(null);
    this.partialFailureNotice.set(null);

    this.documentService.generate(this.projectId, {}).subscribe({
      next: (data) => {
        this.isGenerating.set(false);
        this.documents.set(data.documents.map(this.toListVm));
        this.toastService.show('Document generation started.', 'success');
        this.pollGenerationUntilSettled();
      },
      error: (err: HttpErrorResponse) => {
        this.isGenerating.set(false);
        const message = this.describeGenerateError(err);
        this.generateError.set(message);
        this.toastService.show(message, 'error');
      },
    });
  }

  private describeGenerateError(err: HttpErrorResponse): string {
    if (err.status === 422) return 'Requirements must be approved before documents can be generated.';
    if (err.status === 409) return 'All diagrams must be approved before documents can be generated.';
    return 'Could not generate documents. Please try again.';
  }

  /**
   * Generation runs in the background on the server — this polls the list
   * endpoint every 5s until no document is left in GENERATING status,
   * refreshing the list on every tick.
   */
  private pollGenerationUntilSettled(): void {
    this.pollSub?.unsubscribe();
    this.pollSub = timer(0, 5000)
      .pipe(
        switchMap(() => this.documentService.list(this.projectId)),
        takeWhile((items) => items.some((d) => d.status === 'GENERATING'), true),
      )
      .subscribe({
        next: (items) => {
          this.documents.set(items);
          const stillGenerating = items.some((d) => d.status === 'GENERATING');
          if (!stillGenerating) {
            const failed = items.filter((d) => d.status === 'FAILED');
            this.partialFailureNotice.set(
              failed.length > 0
                ? `${failed.length} of ${items.length} document(s) failed to generate — use Retry to try again.`
                : null,
            );
          }
        },
        error: () => {
          this.listError.set('Lost track of document generation progress. Please refresh and try again.');
        },
      });
  }

  // ── Per-row retry (FAILED) ────────────────────────────────────────────────

  retryDocument(documentId: string): void {
    if (this.regeneratingId()) return;
    this.regeneratingId.set(documentId);
    this.documentService.regenerate(this.projectId, documentId).subscribe({
      next: (dto) => this.applyRegenerateResult(documentId, dto),
      error: () => {
        this.regeneratingId.set(null);
        this.toastService.show('Could not regenerate this document. Please try again.', 'error');
      },
    });
  }

  private applyRegenerateResult(documentId: string, dto: DocumentSummary): void {
    this.regeneratingId.set(null);
    this.documents.update((list) =>
      list.map((d) => (d.documentId === documentId ? { ...d, status: dto.status, lastError: dto.lastError } : d)),
    );
    if (dto.status === 'FAILED') {
      this.toastService.show(dto.lastError ?? 'Regeneration failed. Please try again.', 'error');
    } else {
      this.toastService.show('Document regenerated.', 'success');
    }
  }

  // ── Approve ───────────────────────────────────────────────────────────────

  /** Approves a single document — lets the user validate one at a time instead of only in bulk. */
  approveOne(documentId: string): void {
    if (this.approvingId()) return;
    this.approvingId.set(documentId);
    this.approveError.set(null);
    this.documentService.approve(this.projectId, documentId).subscribe({
      next: () => {
        this.approvingId.set(null);
        this.documents.update((list) =>
          list.map((d) => (d.documentId === documentId ? { ...d, status: 'APPROVED' as DocumentStatus } : d)),
        );
        this.toastService.show('Document approved.', 'success');
      },
      error: () => {
        this.approvingId.set(null);
        this.toastService.show('Could not approve this document. Please try again.', 'error');
      },
    });
  }

  /**
   * Approves every PENDING_APPROVAL document. Each call is wrapped with its own
   * catchError so one document failing validation (a real, expected 422/409 from
   * the backend — e.g. it's no longer PENDING_APPROVAL by the time this runs)
   * doesn't take out the whole batch: forkJoin on raw approve() calls used to
   * abort entirely on the first error, silently discarding every other
   * document's successful approval from the UI.
   */
  approveAll(): void {
    if (this.isApprovingAll()) return;
    const pendingIds = this.documents()
      .filter((d) => d.status === 'PENDING_APPROVAL')
      .map((d) => d.documentId);
    if (pendingIds.length === 0) return;

    this.isApprovingAll.set(true);
    this.approveError.set(null);

    forkJoin(
      pendingIds.map((id) =>
        this.documentService.approve(this.projectId, id).pipe(
          map(() => ({ id, ok: true as const })),
          catchError(() => of({ id, ok: false as const })),
        ),
      ),
    ).subscribe((results) => {
      this.isApprovingAll.set(false);
      const succeededIds = results.filter((r) => r.ok).map((r) => r.id);
      const failedCount = results.length - succeededIds.length;

      if (succeededIds.length > 0) {
        this.documents.update((list) =>
          list.map((d) => (succeededIds.includes(d.documentId) ? { ...d, status: 'APPROVED' as DocumentStatus } : d)),
        );
      }

      if (failedCount === 0) {
        this.toastService.show(`${succeededIds.length} document(s) approved.`, 'success');
      } else if (succeededIds.length === 0) {
        this.approveError.set('Could not approve documents. Please try again.');
        this.toastService.show('Could not approve documents. Please try again.', 'error');
      } else {
        this.approveError.set(`${failedCount} document(s) could not be approved.`);
        this.toastService.show(`${succeededIds.length} approved, ${failedCount} failed.`, 'error');
      }
    });
  }

  // ── Request changes → regenerate ─────────────────────────────────────────

  toggleChangePanel(documentId: string): void {
    if (this.openChangeId() === documentId) {
      this.cancelChangePanel();
      return;
    }
    this.openChangeId.set(documentId);
    this.changeInstruction.set('');
    this.changeError.set(null);
  }

  cancelChangePanel(): void {
    this.openChangeId.set(null);
    this.changeInstruction.set('');
    this.changeError.set(null);
  }

  onChangeInstructionInput(event: Event): void {
    this.changeInstruction.set((event.target as HTMLTextAreaElement).value);
  }

  submitChange(documentId: string): void {
    if (!this.canSubmitChange() || this.submittingChangeId()) return;
    const instructions = this.changeInstruction().trim();
    this.submittingChangeId.set(documentId);
    this.changeError.set(null);

    this.documentService.submitChangeRequest(this.projectId, documentId, instructions).subscribe({
      next: () => {
        this.documentService.regenerate(this.projectId, documentId).subscribe({
          next: (dto) => {
            this.submittingChangeId.set(null);
            this.applyRegenerateResult(documentId, dto);
            this.cancelChangePanel();
          },
          error: () => {
            this.submittingChangeId.set(null);
            this.changeError.set('Regeneration failed. Please try again.');
            this.toastService.show('Regeneration failed. Please try again.', 'error');
          },
        });
      },
      error: () => {
        this.submittingChangeId.set(null);
        this.changeError.set('Could not submit change request. Please try again.');
        this.toastService.show('Could not submit change request. Please try again.', 'error');
      },
    });
  }

  // ── Download ──────────────────────────────────────────────────────────────

  downloadDocument(item: DocumentListItem): void {
    if (this.downloadingId()) return;
    this.downloadingId.set(item.documentId);
    this.downloadError.set(null);

    this.documentService.download(this.projectId, item.documentId).subscribe({
      next: (blob) => {
        this.downloadingId.set(null);
        const label = this.documentTypeLabels[item.type];
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `${label}.docx`;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.downloadingId.set(null);
        this.toastService.show('Could not download this document. Please try again.', 'error');
      },
    });
  }
}
