import { ChangeDetectionStrategy, Component, ElementRef, OnInit, ViewChild, computed, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { ProjectService } from '../../../core/services/project.service';
import { Page, ProjectSummary } from '../../../core/models/project.models';

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
  @ViewChild('searchInput') searchInput!: ElementRef<HTMLInputElement>;

  readonly projects = signal<ProjectSummary[]>([]);
  readonly isLoadingProjects = signal(true);

  /** First 6 projects displayed in the sidebar shortcut list. */
  readonly recentProjects = computed(() => this.projects().slice(0, 6));

  readonly searchQuery = signal('');
  readonly isSearchOpen = signal(false);

  readonly filteredProjects = computed(() => {
    const query = this.searchQuery().toLowerCase().trim();
    if (!query) return [];
    return this.projects()
      .filter((p) => p.title.toLowerCase().includes(query))
      .sort((a, b) => {
        const aStarts = a.title.toLowerCase().startsWith(query);
        const bStarts = b.title.toLowerCase().startsWith(query);
        if (aStarts && !bStarts) return -1;
        if (!aStarts && bStarts) return 1;
        return 0;
      });
  });

  get currentUser() { return this.authService.currentUser; }

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
    this.projectService.list({ size: 50, sort: 'updatedAt,desc' }).subscribe({
      next: (page: Page<ProjectSummary>) => {
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

  onSearchInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.searchQuery.set(value);
    if (value.length > 0) {
      this.isSearchOpen.set(true);
    }
  }

  onSearchFocus(): void {
    if (this.searchQuery().length > 0) {
      this.isSearchOpen.set(true);
    }
  }

  onSearchBlur(): void {
    setTimeout(() => this.isSearchOpen.set(false), 200);
  }

  selectProject(projectId: string): void {
    this.isSearchOpen.set(false);
    this.searchQuery.set('');
    this.searchInput.nativeElement.value = '';
    this.router.navigate(['/app/projects', projectId, 'requirements']);
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
