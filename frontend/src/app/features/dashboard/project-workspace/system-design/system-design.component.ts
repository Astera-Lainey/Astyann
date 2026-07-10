import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild,
  computed,
  signal,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription, timer, switchMap, takeWhile } from 'rxjs';
import { DiagramService } from '../../../../core/services/diagram.service';
import { ToastService } from '../../../../core/services/toast.service';
import {
  ALL_DIAGRAM_TYPES,
  DIAGRAM_TYPE_LABELS,
  DiagramListItem,
  DiagramStatus,
  DiagramSummary,
  DiagramType,
} from '../../../../core/models/diagram.models';

interface DiagramVm {
  diagramId: string;
  type: DiagramType;
  status: DiagramStatus;
  lastError: string | null;
}

const ZOOM_MIN = 0.1;
const ZOOM_MAX = 4;
const ZOOM_STEP = 1.25;

function byCanonicalOrder(a: { type: DiagramType }, b: { type: DiagramType }): number {
  return ALL_DIAGRAM_TYPES.indexOf(a.type) - ALL_DIAGRAM_TYPES.indexOf(b.type);
}

@Component({
  selector: 'app-system-design',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './system-design.html',
  styleUrl: './system-design.scss',
})
export class SystemDesignComponent implements OnChanges, OnDestroy {
  @Input({ required: true }) projectId!: string;
  @Input() pcsfApproved = false;

  @ViewChild('scrollContainer') private scrollContainerRef?: ElementRef<HTMLDivElement>;
  @ViewChild('diagramImg') private diagramImgRef?: ElementRef<HTMLImageElement>;

  readonly diagramTypeLabels = DIAGRAM_TYPE_LABELS;

  // ── List / tabs ───────────────────────────────────────────────────────────
  readonly diagrams = signal<DiagramVm[]>([]);
  readonly selectedId = signal<string | null>(null);
  readonly isLoadingList = signal(true);
  readonly listError = signal<string | null>(null);

  readonly selectedDiagram = computed(
    () => this.diagrams().find((d) => d.diagramId === this.selectedId()) ?? null,
  );
  readonly hasPendingApproval = computed(() => this.diagrams().some((d) => d.status === 'PENDING_APPROVAL'));
  readonly hasAnyDiagrams = computed(() => this.diagrams().length > 0);

  // ── Generate ──────────────────────────────────────────────────────────────
  readonly isGenerating = signal(false);
  readonly generateError = signal<string | null>(null);
  readonly partialFailureNotice = signal<string | null>(null);

  // ── Viewer / render ───────────────────────────────────────────────────────
  readonly currentImageUrl = signal<string | null>(null);
  readonly isImageLoading = signal(false);
  readonly imageError = signal<string | null>(null);
  readonly naturalWidth = signal(0);
  readonly naturalHeight = signal(0);
  readonly zoom = signal(1);
  readonly isPanning = signal(false);

  readonly zoomPercent = computed(() => Math.round(this.zoom() * 100) + '%');
  readonly imageWidth = computed(() => this.naturalWidth() * this.zoom() || undefined);
  readonly imageHeight = computed(() => this.naturalHeight() * this.zoom() || undefined);

  private readonly blobUrlCache = new Map<string, string>();
  private dragStartX = 0;
  private dragStartY = 0;
  private dragStartScrollLeft = 0;
  private dragStartScrollTop = 0;

  // ── Request changes / regenerate ─────────────────────────────────────────
  readonly instructions = signal('');
  readonly isRegenerating = signal(false);
  readonly regenerateError = signal<string | null>(null);
  readonly canSubmitChange = computed(() => this.instructions().trim().length > 0);

  // ── Approve ───────────────────────────────────────────────────────────────
  readonly isApproving = signal(false);
  readonly isApprovingAll = signal(false);
  readonly approveError = signal<string | null>(null);

  private lastLoadedProjectId: string | null = null;
  private pollSub: Subscription | null = null;

  constructor(
    private readonly diagramService: DiagramService,
    private readonly toastService: ToastService,
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.projectId) return;
    const projectChanged = changes['projectId'] && this.projectId !== this.lastLoadedProjectId;
    const nowApproved = changes['pcsfApproved'] && this.pcsfApproved && this.lastLoadedProjectId === null;
    if (projectChanged || nowApproved) {
      this.lastLoadedProjectId = this.projectId;
      this.loadDiagrams();
    }
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
    this.blobUrlCache.forEach((url) => URL.revokeObjectURL(url));
    this.blobUrlCache.clear();
    this.stopPanning();
  }

  // ── Load existing diagrams ───────────────────────────────────────────────

  private loadDiagrams(): void {
    this.isLoadingList.set(true);
    this.listError.set(null);
    this.diagramService.list(this.projectId).subscribe({
      next: (items: DiagramListItem[]) => {
        const vms = items.map(this.toVm).sort(byCanonicalOrder);
        this.diagrams.set(vms);
        this.isLoadingList.set(false);
        if (vms.length > 0) this.selectTab(vms[0].diagramId);
        // Generation may already be in progress from an earlier visit (e.g.
        // the user reloaded the page mid-generation) — resume polling.
        if (vms.some((d) => d.status === 'GENERATING')) {
          this.isGenerating.set(true);
          this.pollGenerationUntilSettled();
        }
      },
      error: () => {
        this.isLoadingList.set(false);
        this.listError.set('Could not load your diagrams. Please try again.');
      },
    });
  }

  retryLoadDiagrams(): void {
    this.loadDiagrams();
  }

  private toVm = (item: DiagramListItem | DiagramSummary): DiagramVm => ({
    diagramId: item.diagramId,
    type: item.type,
    status: item.status,
    lastError: item.lastError,
  });

  // ── Generate (empty-state flow) ──────────────────────────────────────────

  generateSystemDesign(): void {
    if (this.isGenerating()) return;
    this.isGenerating.set(true);
    this.generateError.set(null);
    this.partialFailureNotice.set(null);

    this.diagramService.generate(this.projectId, { diagramTypes: ALL_DIAGRAM_TYPES, renderFormat: 'PNG' }).subscribe({
      next: (data) => {
        const placeholders = data.diagrams.map(this.toVm).sort(byCanonicalOrder);
        this.diagrams.set(placeholders);
        if (placeholders.length > 0) this.selectTab(placeholders[0].diagramId);
        this.pollGenerationUntilSettled();
      },
      error: (err: HttpErrorResponse) => {
        this.isGenerating.set(false);
        this.generateError.set(this.describeGenerateError(err));
      },
    });
  }

  /**
   * Generation runs in the background on the server — this polls the list
   * endpoint until no diagram is left in GENERATING status, refreshing tabs
   * (and the viewer, once the selected diagram settles) on every tick.
   */
  private pollGenerationUntilSettled(): void {
    this.pollSub?.unsubscribe();
    this.pollSub = timer(0, 2500)
      .pipe(
        switchMap(() => this.diagramService.list(this.projectId)),
        takeWhile((items) => items.some((d) => d.status === 'GENERATING'), true),
      )
      .subscribe({
        next: (items) => {
          const vms = items.map(this.toVm).sort(byCanonicalOrder);
          this.diagrams.set(vms);

          const stillGenerating = vms.some((d) => d.status === 'GENERATING');
          if (!stillGenerating) {
            this.isGenerating.set(false);
            const failed = vms.filter((d) => d.status === 'FAILED');
            this.partialFailureNotice.set(
              this.describePartialResult(vms.length - failed.length, failed, vms),
            );
          }

          // Once the selected tab's diagram leaves GENERATING, load its
          // rendered image so the viewer doesn't stay stuck on the empty state.
          const selected = vms.find((d) => d.diagramId === this.selectedId());
          if (selected && selected.status !== 'GENERATING' && selected.status !== 'FAILED' && !this.currentImageUrl()) {
            this.loadImageFor(selected.diagramId, false);
          }
        },
        error: () => {
          this.isGenerating.set(false);
          this.generateError.set('Lost track of generation progress. Please refresh and try again.');
        },
      });
  }

  private describeGenerateError(err: HttpErrorResponse): string {
    if (err.status === 422) return 'Requirements must be approved before diagrams can be generated.';
    if (err.status === 503) return 'All diagram generations failed. Please try again in a moment.';
    return 'Could not generate diagrams. Please try again.';
  }

  /**
   * Every requested type gets a GENERATING placeholder up front, so a type
   * silently not coming back at all should no longer happen under normal
   * operation — this check is kept as a defensive safety net regardless.
   */
  private describePartialResult(succeededCount: number, failed: DiagramVm[], merged: DiagramVm[]): string | null {
    const returnedTypes = new Set(merged.map((d) => d.type));
    const missingTypes = ALL_DIAGRAM_TYPES.filter((t) => !returnedTypes.has(t));

    const parts: string[] = [];
    if (failed.length > 0) parts.push(`${failed.length} failed and can be retried individually`);
    if (missingTypes.length > 0) {
      const names = missingTypes.map((t) => DIAGRAM_TYPE_LABELS[t]).join(', ');
      parts.push(`${missingTypes.length} type(s) didn't come back at all (${names}) — try generating again`);
    }
    if (parts.length === 0) return null;
    return `${succeededCount} of ${ALL_DIAGRAM_TYPES.length} diagrams generated — ${parts.join('; ')}.`;
  }

  // ── Tabs / viewer ─────────────────────────────────────────────────────────

  selectTab(diagramId: string): void {
    if (this.isRegenerating()) return;
    this.selectedId.set(diagramId);
    this.instructions.set('');
    this.regenerateError.set(null);
    this.resetZoomState();

    const diagram = this.diagrams().find((d) => d.diagramId === diagramId);
    if (!diagram || diagram.status === 'FAILED' || diagram.status === 'GENERATING') {
      // A FAILED diagram has no reliable rendered image server-side, and a
      // GENERATING one doesn't have one yet — show the appropriate state
      // instead of issuing a render call we expect to fail.
      this.currentImageUrl.set(null);
      this.imageError.set(null);
      return;
    }
    this.loadImageFor(diagramId, false);
  }

  private loadImageFor(diagramId: string, force: boolean): void {
    if (!force && this.blobUrlCache.has(diagramId)) {
      this.currentImageUrl.set(this.blobUrlCache.get(diagramId)!);
      this.imageError.set(null);
      return;
    }

    this.isImageLoading.set(true);
    this.imageError.set(null);
    this.diagramService.renderBlob(this.projectId, diagramId).subscribe({
      next: (blob) => {
        const previous = this.blobUrlCache.get(diagramId);
        if (previous) URL.revokeObjectURL(previous);

        const url = URL.createObjectURL(blob);
        this.blobUrlCache.set(diagramId, url);
        this.isImageLoading.set(false);
        if (this.selectedId() === diagramId) this.currentImageUrl.set(url);
      },
      error: () => {
        this.isImageLoading.set(false);
        if (this.selectedId() === diagramId) {
          this.imageError.set('Could not load this diagram’s image.');
        }
      },
    });
  }

  retryImageLoad(): void {
    const id = this.selectedId();
    if (id) this.loadImageFor(id, true);
  }

  onImageLoad(event: Event): void {
    const img = event.target as HTMLImageElement;
    this.naturalWidth.set(img.naturalWidth);
    this.naturalHeight.set(img.naturalHeight);
  }

  // ── Zoom controls ─────────────────────────────────────────────────────────

  zoomIn(): void {
    this.zoom.update((z) => Math.min(ZOOM_MAX, z * ZOOM_STEP));
  }

  zoomOut(): void {
    this.zoom.update((z) => Math.max(ZOOM_MIN, z / ZOOM_STEP));
  }

  resetZoom(): void {
    this.zoom.set(1);
    this.centerScroll();
  }

  fitToScreen(): void {
    const container = this.scrollContainerRef?.nativeElement;
    const nw = this.naturalWidth();
    const nh = this.naturalHeight();
    if (!container || !nw || !nh) {
      this.resetZoom();
      return;
    }
    const scale = Math.min(container.clientWidth / nw, container.clientHeight / nh, 1);
    this.zoom.set(scale > 0 ? scale : 1);
    this.centerScroll();
  }

  private resetZoomState(): void {
    this.zoom.set(1);
    this.naturalWidth.set(0);
    this.naturalHeight.set(0);
  }

  private centerScroll(): void {
    const container = this.scrollContainerRef?.nativeElement;
    if (!container) return;
    queueMicrotask(() => {
      container.scrollLeft = (container.scrollWidth - container.clientWidth) / 2;
      container.scrollTop = (container.scrollHeight - container.clientHeight) / 2;
    });
  }

  onWheel(event: WheelEvent): void {
    event.preventDefault();
    if (event.deltaY < 0) this.zoomIn();
    else this.zoomOut();
  }

  // ── Drag to pan ───────────────────────────────────────────────────────────

  onViewerMouseDown(event: MouseEvent): void {
    if (event.button !== 0) return;
    const container = this.scrollContainerRef?.nativeElement;
    if (!container) return;
    event.preventDefault();
    this.dragStartX = event.clientX;
    this.dragStartY = event.clientY;
    this.dragStartScrollLeft = container.scrollLeft;
    this.dragStartScrollTop = container.scrollTop;
    this.isPanning.set(true);
    window.addEventListener('mousemove', this.onDragMove);
    window.addEventListener('mouseup', this.onDragEnd);
  }

  private onDragMove = (event: MouseEvent): void => {
    const container = this.scrollContainerRef?.nativeElement;
    if (!container) return;
    container.scrollLeft = this.dragStartScrollLeft - (event.clientX - this.dragStartX);
    container.scrollTop = this.dragStartScrollTop - (event.clientY - this.dragStartY);
  };

  private onDragEnd = (): void => {
    this.stopPanning();
  };

  private stopPanning(): void {
    this.isPanning.set(false);
    window.removeEventListener('mousemove', this.onDragMove);
    window.removeEventListener('mouseup', this.onDragEnd);
  }

  // ── Request changes → regenerate ─────────────────────────────────────────

  onInstructionInput(event: Event): void {
    this.instructions.set((event.target as HTMLTextAreaElement).value);
  }

  submitAndRegenerate(): void {
    if (!this.canSubmitChange() || this.isRegenerating()) return;
    const diagram = this.selectedDiagram();
    if (!diagram) return;

    const text = this.instructions().trim();
    this.isRegenerating.set(true);
    this.regenerateError.set(null);

    this.diagramService.submitChangeRequest(this.projectId, diagram.diagramId, text).subscribe({
      next: () => this.runRegenerate(diagram.diagramId),
      error: () => {
        this.isRegenerating.set(false);
        this.regenerateError.set('Could not submit your change request. Please try again.');
      },
    });
  }

  retryDiagram(diagramId: string): void {
    if (this.isRegenerating()) return;
    this.isRegenerating.set(true);
    this.regenerateError.set(null);
    this.runRegenerate(diagramId);
  }

  private runRegenerate(diagramId: string): void {
    this.diagramService.regenerate(this.projectId, diagramId, { renderFormat: 'PNG' }).subscribe({
      next: (dto) => {
        this.isRegenerating.set(false);
        this.instructions.set('');
        this.diagrams.update((list) =>
          list.map((d) => (d.diagramId === dto.diagramId ? this.toVm(dto) : d)),
        );

        if (dto.status === 'FAILED') {
          this.regenerateError.set(dto.lastError ?? 'Regeneration failed. Please try again.');
        } else {
          this.regenerateError.set(null);
          this.loadImageFor(dto.diagramId, true);
        }
      },
      error: () => {
        this.isRegenerating.set(false);
        this.regenerateError.set('Regeneration failed. Please try again.');
      },
    });
  }

  // ── Approve ───────────────────────────────────────────────────────────────

  approveCurrent(): void {
    const diagram = this.selectedDiagram();
    if (!diagram || diagram.status !== 'PENDING_APPROVAL' || this.isApproving()) return;

    this.isApproving.set(true);
    this.approveError.set(null);
    this.diagramService.approveOne(this.projectId, diagram.diagramId).subscribe({
      next: () => {
        this.isApproving.set(false);
        this.markApproved([diagram.diagramId]);
        this.toastService.show('Diagram approved.', 'success');
      },
      error: () => {
        this.isApproving.set(false);
        this.approveError.set('Could not approve this diagram. Please try again.');
      },
    });
  }

  approveAll(): void {
    if (this.isApprovingAll() || !this.hasPendingApproval()) return;
    const pendingIds = this.diagrams()
      .filter((d) => d.status === 'PENDING_APPROVAL')
      .map((d) => d.diagramId);

    this.isApprovingAll.set(true);
    this.approveError.set(null);
    this.diagramService.approve(this.projectId, {}).subscribe({
      next: (res) => {
        this.isApprovingAll.set(false);
        this.markApproved(pendingIds);
        this.toastService.show(
          res.allDiagramsApproved ? 'All diagrams approved.' : `${res.updatedCount} diagram(s) approved.`,
          'success',
        );
      },
      error: () => {
        this.isApprovingAll.set(false);
        this.approveError.set('Could not approve diagrams. Please try again.');
      },
    });
  }

  private markApproved(ids: string[]): void {
    this.diagrams.update((list) =>
      list.map((d) => (ids.includes(d.diagramId) ? { ...d, status: 'APPROVED' as DiagramStatus } : d)),
    );
  }
}
