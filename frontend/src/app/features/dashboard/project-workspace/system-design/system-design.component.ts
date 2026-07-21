import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild,
  computed,
  signal,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Subscription, timer, switchMap, takeWhile } from 'rxjs';
import { DiagramService } from '../../../../core/services/diagram.service';
import { ToastService } from '../../../../core/services/toast.service';
import { VersionService } from '../../../../core/services/version.service';
import {
  ALL_DIAGRAM_TYPES,
  DIAGRAM_TYPE_LABELS,
  DiagramListItem,
  DiagramStatus,
  DiagramSummary,
  DiagramType,
} from '../../../../core/models/diagram.models';
import { Snapshot } from '../../../../core/models/version.models';

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

  // ── Active version (from VersionService), keyed by diagramId ────────────────
  readonly activeVersions = signal<Map<string, Snapshot>>(new Map());

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

  /** Image top-left offset from the viewport's top-left, in CSS px — the
   *  translate half of `translate(panX, panY) scale(zoom)`. Replaces the old
   *  scrollLeft/scrollTop-based panning so pan and zoom share one coordinate
   *  space, which is what makes cursor-centered zoom solvable in closed form. */
  readonly panX = signal(0);
  readonly panY = signal(0);

  readonly zoomPercent = computed(() => Math.round(this.zoom() * 100) + '%');
  readonly viewerTransform = computed(() => `translate(${this.panX()}px, ${this.panY()}px) scale(${this.zoom()})`);

  // ── Fullscreen ────────────────────────────────────────────────────────────
  readonly isFullscreen = signal(false);
  /** Mouse-over state of the viewer — scopes keyboard shortcuts (same "hover to
   *  interact" model the existing wheel-zoom already uses) without stealing them
   *  from the rest of the page. */
  readonly isViewerHovered = signal(false);
  private previousBodyOverflow = '';

  private readonly blobUrlCache = new Map<string, string>();
  private dragStartX = 0;
  private dragStartY = 0;
  private dragStartPanX = 0;
  private dragStartPanY = 0;

  // ── Request changes / regenerate ─────────────────────────────────────────
  readonly instructions = signal('');
  readonly isRegenerating = signal(false);
  readonly regenerateError = signal<string | null>(null);
  readonly canSubmitChange = computed(() => this.instructions().trim().length > 0);

  // ── Approve ───────────────────────────────────────────────────────────────
  readonly isApproving = signal(false);
  readonly isApprovingAll = signal(false);
  readonly approveError = signal<string | null>(null);
  readonly showApproveConfirm = signal(false);
  readonly showApproveSuccess = signal(false);

  private lastLoadedProjectId: string | null = null;
  private pollSub: Subscription | null = null;

  constructor(
    private readonly diagramService: DiagramService,
    private readonly versionService: VersionService,
    private readonly toastService: ToastService,
    private readonly router: Router,
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
    if (this.isFullscreen()) document.body.style.overflow = this.previousBodyOverflow;
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
        this.loadActiveVersions();
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

  /** Refreshes which snapshot VersionService currently has flagged active per diagram. */
  private loadActiveVersions(): void {
    this.versionService.getActiveSnapshotsByArtifact(this.projectId).subscribe((map) => {
      this.activeVersions.set(map);
    });
  }

  /** Active version number for a diagram, or null if it has never been approved. */
  activeVersionNumber(diagramId: string): number | null {
    return this.activeVersions().get(diagramId)?.versionNumber ?? null;
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
    // Every fresh image load (initial view, tab switch, regenerate) should open
    // fully visible rather than clipped at 100% — reuses the same fit-to-screen
    // logic the toolbar button calls, so manual zoom afterwards is untouched.
    this.fitToScreen();
  }

  // ── Zoom controls ─────────────────────────────────────────────────────────
  //
  // The image is drawn as `translate(panX, panY) scale(zoom)` with a
  // top-left transform-origin, so a point at natural-image coordinates
  // (ix, iy) always lands on screen at (panX + ix*zoom, panY + iy*zoom)
  // relative to the viewer. Every zoom entry point below (wheel, +/- buttons,
  // fit-to-screen) funnels through `applyZoomAtPoint`, which re-solves that
  // one equation for the pan offset that keeps a chosen screen point fixed —
  // that's the whole cursor-centered-zoom trick, no separate pan step needed.

  zoomIn(): void {
    this.zoomAtViewportCenter(Math.min(ZOOM_MAX, this.zoom() * ZOOM_STEP));
  }

  zoomOut(): void {
    this.zoomAtViewportCenter(Math.max(ZOOM_MIN, this.zoom() / ZOOM_STEP));
  }

  /** Toolbar buttons have no cursor-over-the-diagram context, so they zoom
   *  around the current viewport center instead — the same "reasonable
   *  default pivot" every diagram tool uses for keyboard/button zoom. */
  private zoomAtViewportCenter(newZoom: number): void {
    const container = this.scrollContainerRef?.nativeElement;
    if (!container) {
      this.zoom.set(newZoom);
      return;
    }
    this.applyZoomAtPoint(container.clientWidth / 2, container.clientHeight / 2, newZoom);
  }

  resetZoom(): void {
    this.zoom.set(1);
    this.centerPan(1);
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
    const clamped = scale > 0 ? scale : 1;
    this.zoom.set(clamped);
    this.centerPan(clamped);
  }

  private resetZoomState(): void {
    this.zoom.set(1);
    this.panX.set(0);
    this.panY.set(0);
    this.naturalWidth.set(0);
    this.naturalHeight.set(0);
  }

  /** Centers the image at the given scale by placing its top-left corner so
   *  equal blank space surrounds it — the transform-based replacement for the
   *  old scrollLeft/scrollTop centering. Unlike the old version this needs no
   *  queueMicrotask: it reads the container's own box (unaffected by the
   *  image's transform) rather than a scrollWidth that depended on the image
   *  having already been resized in the DOM. */
  private centerPan(scale: number): void {
    const container = this.scrollContainerRef?.nativeElement;
    if (!container) return;
    const displayedW = this.naturalWidth() * scale;
    const displayedH = this.naturalHeight() * scale;
    this.panX.set((container.clientWidth - displayedW) / 2);
    this.panY.set((container.clientHeight - displayedH) / 2);
  }

  /**
   * Cursor-centered zoom core. Given a pivot point in container-relative
   * screen coordinates (pointX, pointY) and a target zoom level:
   *
   *   1. Invert the current transform to find which natural-image point is
   *      currently drawn at the pivot:      imagePoint = (point - pan) / oldZoom
   *   2. Re-apply the transform equation at the new zoom, solved for pan
   *      instead of screen position, so that same image point still lands
   *      on the pivot:                      pan = point - imagePoint * newZoom
   *
   * The viewport never needs a separate "compensating pan" step — steps 1
   * and 2 together *are* the compensation.
   */
  private applyZoomAtPoint(pointX: number, pointY: number, newZoom: number): void {
    const oldZoom = this.zoom();
    if (newZoom === oldZoom) return;

    const imagePointX = (pointX - this.panX()) / oldZoom;
    const imagePointY = (pointY - this.panY()) / oldZoom;

    const nextPanX = pointX - imagePointX * newZoom;
    const nextPanY = pointY - imagePointY * newZoom;

    const clamped = this.clampPan(nextPanX, nextPanY, newZoom);
    this.zoom.set(newZoom);
    this.panX.set(clamped.x);
    this.panY.set(clamped.y);
  }

  /** Keeps a comfortable margin of the diagram always reachable on screen
   *  instead of letting zoom/pan push it entirely out of view. */
  private clampPan(panX: number, panY: number, scale: number): { x: number; y: number } {
    const container = this.scrollContainerRef?.nativeElement;
    const nw = this.naturalWidth();
    const nh = this.naturalHeight();
    if (!container || !nw || !nh) return { x: panX, y: panY };

    const cw = container.clientWidth;
    const ch = container.clientHeight;
    const displayedW = nw * scale;
    const displayedH = nh * scale;

    const marginX = Math.min(120, displayedW / 2, cw / 2);
    const marginY = Math.min(120, displayedH / 2, ch / 2);

    return {
      x: Math.min(cw - marginX, Math.max(marginX - displayedW, panX)),
      y: Math.min(ch - marginY, Math.max(marginY - displayedH, panY)),
    };
  }

  onWheel(event: WheelEvent): void {
    event.preventDefault();
    const container = this.scrollContainerRef?.nativeElement;
    if (!container) return;

    const oldZoom = this.zoom();
    const newZoom = event.deltaY < 0
      ? Math.min(ZOOM_MAX, oldZoom * ZOOM_STEP)
      : Math.max(ZOOM_MIN, oldZoom / ZOOM_STEP);

    // Pivot is the cursor's position relative to the viewer, not the page —
    // this is what makes the zoom track the point under the mouse rather
    // than the viewport center.
    const rect = container.getBoundingClientRect();
    this.applyZoomAtPoint(event.clientX - rect.left, event.clientY - rect.top, newZoom);
  }

  // ── Drag to pan ───────────────────────────────────────────────────────────

  onViewerMouseDown(event: MouseEvent): void {
    if (event.button !== 0) return;
    const container = this.scrollContainerRef?.nativeElement;
    if (!container) return;
    event.preventDefault();
    this.dragStartX = event.clientX;
    this.dragStartY = event.clientY;
    this.dragStartPanX = this.panX();
    this.dragStartPanY = this.panY();
    this.isPanning.set(true);
    window.addEventListener('mousemove', this.onDragMove);
    window.addEventListener('mouseup', this.onDragEnd);
  }

  private onDragMove = (event: MouseEvent): void => {
    const nextPanX = this.dragStartPanX + (event.clientX - this.dragStartX);
    const nextPanY = this.dragStartPanY + (event.clientY - this.dragStartY);
    const clamped = this.clampPan(nextPanX, nextPanY, this.zoom());
    this.panX.set(clamped.x);
    this.panY.set(clamped.y);
  };

  private onDragEnd = (): void => {
    this.stopPanning();
  };

  private stopPanning(): void {
    this.isPanning.set(false);
    window.removeEventListener('mousemove', this.onDragMove);
    window.removeEventListener('mouseup', this.onDragEnd);
  }

  // ── Fullscreen ────────────────────────────────────────────────────────────
  // Not a separate viewer: the same toolbar, #scrollContainer and <img> are
  // reused as-is, just repositioned via the `sd__viewer--fullscreen` CSS
  // modifier (fixed, inset: 0) instead of duplicating any viewer markup/state.

  toggleFullscreen(): void {
    this.isFullscreen.update((v) => !v);
    this.syncFullscreenSideEffects();
  }

  exitFullscreen(): void {
    if (!this.isFullscreen()) return;
    this.isFullscreen.set(false);
    this.syncFullscreenSideEffects();
  }

  onViewerMouseEnter(): void {
    this.isViewerHovered.set(true);
  }

  onViewerMouseLeave(): void {
    this.isViewerHovered.set(false);
  }

  private syncFullscreenSideEffects(): void {
    if (this.isFullscreen()) {
      this.previousBodyOverflow = document.body.style.overflow;
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = this.previousBodyOverflow;
    }
    // Unlike the old scroll-based viewer, pan/zoom are now absolute pixel
    // offsets against the container's own box — and that box jumps
    // drastically between the ~78vh inline card and the 100vh overlay, so a
    // stale pan would leave the diagram mis-centered (or fully clamped out of
    // view) the moment the container's size actually changes. Re-fit once
    // that resize has been laid out, same queueMicrotask pattern already
    // used elsewhere here to wait out a pending DOM/style flush.
    if (this.currentImageUrl()) {
      queueMicrotask(() => this.fitToScreen());
    }
  }

  // ── Keyboard shortcuts ────────────────────────────────────────────────────
  // Scoped to when the pointer is over the viewer — the same "hover to
  // interact" model the existing wheel-zoom handler already relies on — or
  // unconditionally while fullscreen, since then the viewer *is* the page.

  @HostListener('document:keydown', ['$event'])
  onViewerKeydown(event: KeyboardEvent): void {
    if (this.isTypingTarget(event.target)) return;

    if (event.key === 'Escape') {
      if (this.isFullscreen()) {
        event.preventDefault();
        this.exitFullscreen();
      }
      return;
    }

    if (!this.isFullscreen() && !this.isViewerHovered()) return;
    if (!this.selectedDiagram() || this.isImageLoading() || !this.currentImageUrl()) return;

    switch (event.key) {
      case '+':
      case '=':
        event.preventDefault();
        this.zoomIn();
        break;
      case '-':
      case '_':
        event.preventDefault();
        this.zoomOut();
        break;
      case '0':
        event.preventDefault();
        this.fitToScreen();
        break;
      case 'f':
      case 'F':
        event.preventDefault();
        this.toggleFullscreen();
        break;
    }
  }

  private isTypingTarget(target: EventTarget | null): boolean {
    const el = target as HTMLElement | null;
    if (!el) return false;
    return el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT' || el.isContentEditable;
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

  openApproveConfirm(): void {
    if (!this.hasPendingApproval()) return;
    this.approveError.set(null);
    this.showApproveConfirm.set(true);
  }

  cancelApproveConfirm(): void {
    if (this.isApprovingAll()) return;
    this.showApproveConfirm.set(false);
    this.approveError.set(null);
  }

  confirmApprove(): void {
    if (this.isApprovingAll() || !this.hasPendingApproval()) return;
    const pendingIds = this.diagrams()
      .filter((d) => d.status === 'PENDING_APPROVAL')
      .map((d) => d.diagramId);

    this.isApprovingAll.set(true);
    this.approveError.set(null);
    this.diagramService.approve(this.projectId, {}).subscribe({
      next: () => {
        this.isApprovingAll.set(false);
        this.markApproved(pendingIds);
        this.showApproveConfirm.set(false);
        this.showApproveSuccess.set(true);
      },
      error: () => {
        this.isApprovingAll.set(false);
        this.approveError.set('Could not approve diagrams. Please try again.');
      },
    });
  }

  closeApproveSuccess(): void {
    this.showApproveSuccess.set(false);
  }

  goToNextStep(): void {
    this.showApproveSuccess.set(false);
    this.router.navigate(['/app/projects', this.projectId, 'documents']);
  }

  private markApproved(ids: string[]): void {
    this.diagrams.update((list) =>
      list.map((d) => (ids.includes(d.diagramId) ? { ...d, status: 'APPROVED' as DiagramStatus } : d)),
    );
    // Approving creates a fresh snapshot per diagram — refresh so the active-version badge updates.
    this.loadActiveVersions();
  }
}
