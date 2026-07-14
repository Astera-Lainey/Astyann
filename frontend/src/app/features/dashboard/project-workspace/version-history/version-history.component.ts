import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnChanges,
  SimpleChanges,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { VersionService } from '../../../../core/services/version.service';
import { Snapshot } from '../../../../core/models/version.models';

@Component({
  selector: 'app-version-history',
  standalone: true,
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './version-history.html',
  styleUrl: './version-history.scss',
})
export class VersionHistoryComponent implements OnChanges {
  @Input({ required: true }) projectId!: string;

  readonly snapshots = signal<Snapshot[]>([]);
  readonly isLoadingList = signal(true);
  readonly listError = signal<string | null>(null);

  readonly selectedSnapshot = signal<Snapshot | null>(null);
  readonly selectedSnapId = signal<string | null>(null);
  readonly isLoadingDetail = signal(false);
  readonly detailError = signal<string | null>(null);

  private lastLoadedProjectId: string | null = null;

  constructor(private readonly versionService: VersionService) {}

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
        if (sorted.length > 0) this.selectSnapshot(sorted[0].snapId);
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

  selectSnapshot(snapId: string): void {
    this.selectedSnapId.set(snapId);
    this.isLoadingDetail.set(true);
    this.detailError.set(null);
    this.versionService.getSnapshot(snapId).subscribe({
      next: (snapshot) => {
        this.isLoadingDetail.set(false);
        this.selectedSnapshot.set(snapshot);
      },
      error: () => {
        this.isLoadingDetail.set(false);
        this.detailError.set('Could not load this snapshot’s detail. Please try again.');
      },
    });
  }

  retryLoadDetail(): void {
    const id = this.selectedSnapId();
    if (id) this.selectSnapshot(id);
  }
}
