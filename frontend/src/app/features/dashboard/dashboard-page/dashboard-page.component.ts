import { ChangeDetectionStrategy, Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ProjectService } from '../../../core/services/project.service';
import { ProjectSummary } from '../../../core/models/project.models';

/**
 * Dashboard page — converted from `app.index.tsx`.
 *
 * Renders inside `AppShellComponent`'s `<router-outlet>`. Loads the real
 * project list via `ProjectService.list()` (GET /projects, API-PROJ-02) and
 * derives the stat cards from it.
 *
 * Scope notes (per project decision, since the contract only ever
 * documents `status: "ANALYZING"` on a Project and has no global
 * cross-project activity endpoint):
 *  - The "Deployed / In Progress / Validating" stat buckets from the
 *    original mockup are NOT reproduced, since those status values aren't
 *    confirmed anywhere in the API Contract. Instead this shows "Total
 *    Projects" (always derivable) and a generic "Analyzing" bucket (the
 *    one status value the contract does confirm). Extend
 *    `core/models/project.models.ts`'s `ProjectStatus` and this component
 *    together once the backend's full status enum is confirmed.
 *  - Project list cards show only contract-confirmed fields (title,
 *    description, status, updatedAt) — no version badge, no colored
 *    thumbnail status (a neutral initial-letter avatar is used purely as a
 *    frontend visual aid, not backend data).
 *  - The "Recent Activity" panel from the mockup is omitted entirely — the
 *    API Contract has no endpoint for a cross-project activity feed
 *    (`GET /projects/{projectId}/versions`, API-VER-01, is scoped to a
 *    single project).
 *  - The welcome banner's "Developer" name is static copy, same as in the
 *    original mockup — the contract's `LoginResponseData` has no display
 *    name field to personalize it with (only `userId`/`email`).
 */
@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [CommonModule, RouterLink],
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
    this.projectService.list({ size: 20, sort: 'updatedAt,desc' }).subscribe({
      next: (page) => {
        this.projects.set(page.content);
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
