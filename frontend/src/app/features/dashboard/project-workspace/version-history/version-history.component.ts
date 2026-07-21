import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnChanges,
  SimpleChanges,
  signal,
} from '@angular/core';
import { formatDate } from '@angular/common';
import { VersionService } from '../../../../core/services/version.service';
import { DiagramService } from '../../../../core/services/diagram.service';
import { DocumentService } from '../../../../core/services/document.service';
import { ToastService } from '../../../../core/services/toast.service';
import { ArtifactType, Snapshot } from '../../../../core/models/version.models';
import { DIAGRAM_TYPE_LABELS, DiagramType } from '../../../../core/models/diagram.models';
import { DOCUMENT_TYPE_LABELS, DocumentType } from '../../../../core/models/document.models';

interface SubGroup {
  key: string;
  label: string;
  count: number;
}

const ARTIFACT_TYPE_ORDER: ArtifactType[] = ['DIAGRAM', 'DOCUMENT', 'CODE', 'DEPLOYMENT'];

const ARTIFACT_TYPE_LABELS: Record<ArtifactType, string> = {
  DIAGRAM: 'Diagrams',
  DOCUMENT: 'Documents',
  CODE: 'Code',
  DEPLOYMENT: 'Deployment',
};

const ARTIFACT_TYPE_DESCRIPTIONS: Record<ArtifactType, string> = {
  DIAGRAM: 'UML diagrams generated for this project.',
  DOCUMENT: 'Generated project documentation.',
  CODE: 'Generated source code artefacts.',
  DEPLOYMENT: 'Deployment configuration artefacts.',
};

@Component({
  selector: 'app-version-history',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './version-history.html',
  styleUrl: './version-history.scss',
})
export class VersionHistoryComponent implements OnChanges {
  @Input({ required: true }) projectId!: string;

  readonly artifactTypeLabels = ARTIFACT_TYPE_LABELS;
  readonly artifactTypeDescriptions = ARTIFACT_TYPE_DESCRIPTIONS;

  readonly snapshots = signal<Snapshot[]>([]);
  readonly isLoadingList = signal(true);
  readonly listError = signal<string | null>(null);

  readonly selectedArtifactType = signal<ArtifactType | null>(null);
  readonly selectedSubKey = signal<string | null>(null);

  readonly downloadingSnapId = signal<string | null>(null);
  readonly activatingSnapId = signal<string | null>(null);

  private lastLoadedProjectId: string | null = null;

  constructor(
    private readonly versionService: VersionService,
    private readonly diagramService: DiagramService,
    private readonly documentService: DocumentService,
    private readonly toastService: ToastService,
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.projectId) return;
    if (changes['projectId'] && this.projectId !== this.lastLoadedProjectId) {
      this.lastLoadedProjectId = this.projectId;
      this.loadTimeline();
    }
  }

  private loadTimeline(): void {
    this.isLoadingList.set(true);
    this.listError.set(null);
    this.versionService.getTimeline(this.projectId).subscribe({
      next: (timeline) => {
        const sorted = [...timeline.snapshots].sort(
          (a, b) => (b.versionNumber ?? 0) - (a.versionNumber ?? 0),
        );
        this.snapshots.set(sorted);
        this.isLoadingList.set(false);
        this.selectDefaultGroup(sorted);
      },
      error: () => {
        this.isLoadingList.set(false);
        this.listError.set('Could not load the version history. Please try again.');
      },
    });
  }

  retryLoadTimeline(): void {
    this.loadTimeline();
  }

  private selectDefaultGroup(items: Snapshot[]): void {
    const firstType = ARTIFACT_TYPE_ORDER.find((t) => items.some((s) => s.artifactType === t)) ?? null;
    this.selectedArtifactType.set(firstType);
    this.selectedSubKey.set(firstType ? (this.subGroupsFor(firstType)[0]?.key ?? null) : null);
  }

  // ── Zone 1: artifact-type tabs ───────────────────────────────────────────

  artifactTypeGroups(): { type: ArtifactType; count: number }[] {
    const counts: Partial<Record<ArtifactType, number>> = {};
    for (const s of this.snapshots()) {
      if (!s.artifactType) continue;
      counts[s.artifactType] = (counts[s.artifactType] ?? 0) + 1;
    }
    return ARTIFACT_TYPE_ORDER.filter((t) => (counts[t] ?? 0) > 0).map((t) => ({ type: t, count: counts[t]! }));
  }

  selectArtifactType(type: ArtifactType): void {
    if (this.selectedArtifactType() === type) return;
    this.selectedArtifactType.set(type);
    this.selectedSubKey.set(this.subGroupsFor(type)[0]?.key ?? null);
  }

  // ── Zone 2: sub-type sidebar ──────────────────────────────────────────────

  subGroupsFor(type: ArtifactType): SubGroup[] {
    const counts = new Map<string, number>();
    for (const s of this.snapshots()) {
      if (s.artifactType !== type) continue;
      const key = this.subKeyFor(s);
      counts.set(key, (counts.get(key) ?? 0) + 1);
    }
    return Array.from(counts.entries()).map(([key, count]) => ({
      key,
      count,
      label: this.subLabelFor(type, key),
    }));
  }

  selectSubGroup(key: string): void {
    this.selectedSubKey.set(key);
  }

  private subKeyFor(s: Snapshot): string {
    return s.diagramType ?? s.documentType ?? s.codeLayer ?? 'GENERAL';
  }

  private subLabelFor(type: ArtifactType, key: string): string {
    if (type === 'DIAGRAM') return DIAGRAM_TYPE_LABELS[key as DiagramType] ?? this.humanize(key);
    if (type === 'DOCUMENT') return DOCUMENT_TYPE_LABELS[key as DocumentType] ?? this.humanize(key);
    return this.humanize(key);
  }

  private humanize(key: string): string {
    return key
      .replace(/_/g, ' ')
      .toLowerCase()
      .replace(/\b\w/g, (c) => c.toUpperCase());
  }

  // ── Zone 3: version cards for the selected group ──────────────────────────

  selectedSnapshots(): Snapshot[] {
    const type = this.selectedArtifactType();
    const key = this.selectedSubKey();
    if (!type || key === null) return [];
    return this.snapshots()
      .filter((s) => s.artifactType === type && this.subKeyFor(s) === key)
      .sort((a, b) => (b.versionNumber ?? 0) - (a.versionNumber ?? 0));
  }

  selectedGroupLabel(): string {
    const type = this.selectedArtifactType();
    const key = this.selectedSubKey();
    if (!type || key === null) return '';
    return this.subLabelFor(type, key);
  }

  formatRelative(dateStr: string): string {
    const date = new Date(dateStr);
    const diffDays = Math.floor((Date.now() - date.getTime()) / (1000 * 60 * 60 * 24));
    if (diffDays <= 0) return 'today';
    if (diffDays === 1) return 'yesterday';
    if (diffDays < 30) return `${diffDays} days ago`;
    return formatDate(dateStr, 'mediumDate', 'en-US');
  }

  // ── Download (only where a real endpoint exists: diagrams and documents) ──

  canDownload(snap: Snapshot): boolean {
    return (snap.artifactType === 'DOCUMENT' && !!snap.documentId)
      || (snap.artifactType === 'DIAGRAM' && !!snap.diagramId);
  }

  downloadSnapshot(snap: Snapshot): void {
    if (this.downloadingSnapId()) return;
    const name = snap.versionName || `v${snap.versionNumber}`;

    if (snap.artifactType === 'DOCUMENT' && snap.documentId) {
      this.downloadingSnapId.set(snap.snapId);
      this.documentService.download(this.projectId, snap.documentId).subscribe({
        next: (blob) => {
          this.downloadingSnapId.set(null);
          this.saveBlob(blob, `${name}.docx`);
        },
        error: () => {
          this.downloadingSnapId.set(null);
          this.toastService.show('Could not download this document. Please try again.', 'error');
        },
      });
    } else if (snap.artifactType === 'DIAGRAM' && snap.diagramId) {
      this.downloadingSnapId.set(snap.snapId);
      this.diagramService.renderBlob(this.projectId, snap.diagramId, 'PNG').subscribe({
        next: (blob) => {
          this.downloadingSnapId.set(null);
          this.saveBlob(blob, `${name}.png`);
        },
        error: () => {
          this.downloadingSnapId.set(null);
          this.toastService.show('Could not download this diagram. Please try again.', 'error');
        },
      });
    }
  }

  // ── Activate (rollback to an earlier version) ─────────────────────────────

  activateSnapshot(snap: Snapshot): void {
    if (snap.active || this.activatingSnapId()) return;

    this.activatingSnapId.set(snap.snapId);
    this.versionService.activateSnapshot(snap.snapId).subscribe({
      next: (activated) => {
        this.activatingSnapId.set(null);
        this.snapshots.update((all) =>
          all.map((s) => {
            if (s.snapId === activated.snapId) return activated;
            if (
              s.artifactType === activated.artifactType &&
              s.artifactId === activated.artifactId &&
              s.active
            ) {
              return { ...s, active: false };
            }
            return s;
          }),
        );
        this.toastService.show(`${activated.versionName || `v${activated.versionNumber}`} is now the active version.`, 'success');
      },
      error: () => {
        this.activatingSnapId.set(null);
        this.toastService.show('Could not activate this version. Please try again.', 'error');
      },
    });
  }

  private saveBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  }
}
