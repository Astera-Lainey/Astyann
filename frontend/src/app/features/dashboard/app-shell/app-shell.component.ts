import { ChangeDetectionStrategy, Component, OnInit, computed, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { ProjectService } from '../../../core/services/project.service';
import { ProjectSummary } from '../../../core/models/project.models';

/** Mirrors `WorkspaceSection` in project-workspace.component.ts — kept local to avoid a lazy-chunk cross-import. */
const WORKSPACE_SECTIONS: { section: string; label: string }[] = [
  { section: 'requirements', label: 'Requirements' },
  { section: 'design', label: 'System Design' },
  { section: 'documents', label: 'Documentation' },
  { section: 'code', label: 'Code' },
  { section: 'deploy', label: 'Deployment' },
  { section: 'versions', label: 'Version History' },
];

const PROJECT_URL_PATTERN = /^\/app\/projects\/([^/]+)\/([^/]+)/;

/**
 * Authenticated application shell: dark sidebar (logo, primary nav, live
 * project list, contextual workspace links, settings) + light topbar
 * (search), wrapping a `<router-outlet>` for everything under `/app/**`.
 *
 * Built from the provided dashboard screenshot as a static visual
 * reference — the original `AppShell.tsx` source wasn't available, so
 * structure and styling were reconstructed from the image rather than
 * converted from source. Skinned with the same Alata/red/white/slate
 * design language already used by the landing and auth pages
 * (`_auth-page-shared.scss`'s palette), per project decision, rather than
 * the unused "Caesar" oklch theme in styles.scss.
 *
 * The WORKSPACE section (Requirements/System Design/Documents/Code/
 * Deployment/Version History) only renders while the active route is
 * `app/projects/:id/:section` — the active `projectId` is read directly
 * from the router URL (no extra `GET /projects/{id}` call here, since the
 * project workspace shell already fetches the full project for its own
 * header; duplicating that call here would be wasted work for links that
 * only need the id).
 */
@Component({
  selector: 'app-app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
})
export class AppShellComponent implements OnInit {
  readonly projects = signal<ProjectSummary[]>([]);
  readonly isLoadingProjects = signal(true);

  readonly currentUser = this.authService.currentUser;

  /** projectId extracted from the current URL, or null when not inside a project workspace. */
  private readonly activeProjectId = signal<string | null>(null);

  readonly workspaceSections = WORKSPACE_SECTIONS;
  readonly isInProjectWorkspace = computed(() => this.activeProjectId() !== null);
  readonly activeProjectIdValue = this.activeProjectId.asReadonly();

  constructor(
    private readonly authService: AuthService,
    private readonly projectService: ProjectService,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    // A small page size keeps the sidebar shortcut list focused on recent
    // projects rather than trying to render the user's entire portfolio —
    // "view all" style browsing belongs on the dashboard's own project
    // list, not duplicated here.
    this.projectService.list({ size: 6, sort: 'updatedAt,desc' }).subscribe({
      next: (page) => {
        this.projects.set(page.content);
        this.isLoadingProjects.set(false);
      },
      error: () => {
        this.isLoadingProjects.set(false);
      },
    });

    this.updateActiveProjectId(this.router.url);
    this.router
      .events.pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => this.updateActiveProjectId(e.urlAfterRedirects));
  }

  logout(): void {
    this.authService.logout().subscribe(() => {
      this.router.navigate(['/login']);
    });
  }

  private updateActiveProjectId(url: string): void {
    const match = PROJECT_URL_PATTERN.exec(url);
    this.activeProjectId.set(match ? match[1] : null);
  }
}
