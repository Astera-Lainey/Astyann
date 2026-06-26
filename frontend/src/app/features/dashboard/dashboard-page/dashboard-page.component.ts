import { ChangeDetectionStrategy, Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ProjectService } from '../../../core/services/project.service';
import { ProjectSummary } from '../../../core/models/project.models';
import { SparkleIconComponent } from '../../../shared/components/sparkle-icon/sparkle-icon.component';

/**
 * Dashboard page — converted from `app.index.tsx`.
 *
 * Renders inside `AppShellComponent`'s `<router-outlet>`. Loads the authenticated
 * user's project list via `ProjectService.search()` (GET /projects/search) and
 * derives the stat cards from it.
 *
 * Project status values: ANALYZING | GENERATING | COMPLETED (mirrors backend enum).
 * Date fields use `creationDate` / `updatedDate` (mirrors backend ProjectDTO).
 */
@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [CommonModule, RouterLink, SparkleIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard-page.component.html',
  styleUrl: './dashboard-page.component.scss',
})
export class DashboardPageComponent implements OnInit {
  readonly projects = signal<ProjectSummary[]>([]);
  readonly isLoading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly totalProjects = computed(() => this.projects().length);
  readonly analyzingCount = computed(
    () => this.projects().filter((p) => p.status === 'ANALYZING').length,
  );

  constructor(private readonly projectService: ProjectService) {}

  ngOnInit(): void {
    this.projectService.search().subscribe({
      next: (projects: ProjectSummary[]) => {
        this.projects.set(projects);
        this.isLoading.set(false);
      },
      error: () => {
        this.loadError.set('Could not load your projects. Please try again later.');
        this.isLoading.set(false);
      },
    });
  }

  /** Stable, deterministic avatar initial for a project — purely cosmetic, not backend data. */
  initialFor(title: string): string {
    return title.charAt(0).toUpperCase();
  }
}
