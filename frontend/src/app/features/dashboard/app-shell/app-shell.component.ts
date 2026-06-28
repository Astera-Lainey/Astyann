import { ChangeDetectionStrategy, Component, ElementRef, OnInit, ViewChild, computed, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { ProjectService } from '../../../core/services/project.service';
import { ProjectSummary } from '../../../core/models/project.models';

/** Mirrors `WorkspaceSection` in project-workspace.component.ts — kept local to avoid a lazy-chunk cross-import. */
const WORKSPACE_SECTIONS: { section: string; label: string; icon: string }[] = [
  { section: 'requirements', label: 'Requirements',    icon: 'M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2M9 5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2M9 5a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2m-6 9l2 2 4-4' },
  { section: 'design',       label: 'System Design',   icon: 'M12 2L2 7l10 5 10-5-10-5M2 17l10 5 10-5M2 12l10 5 10-5' },
  { section: 'documents',    label: 'Documentation',   icon: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zM14 2v6h6M16 13H8M16 17H8M10 9H8' },
  { section: 'code',         label: 'Code',            icon: 'M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4' },
  { section: 'deploy',       label: 'Deployment',      icon: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12' },
  { section: 'versions',     label: 'Version History', icon: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 6v6l4 2' },
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

  /** Last 5 created projects displayed in the sidebar shortcut list. */
  readonly recentProjects = computed(() =>
    this.projects()
      .slice()
      .sort((a, b) => new Date(b.creationDate).getTime() - new Date(a.creationDate).getTime())
      .slice(0, 5),
  );

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

  /** section extracted from the current URL (e.g. "requirements", "review"). */
  readonly activeSection = signal<string | null>(null);

  readonly workspaceSections = WORKSPACE_SECTIONS;
  readonly isInProjectWorkspace = computed(() => this.activeProjectId() !== null);
  readonly activeProjectIdValue = this.activeProjectId.asReadonly();

  constructor(
    private readonly authService: AuthService,
    private readonly projectService: ProjectService,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    this.projectService.search().subscribe({
      next: (projects: ProjectSummary[]) => {
        this.projects.set(projects);
        this.isLoadingProjects.set(false);
      },
      error: () => {
        this.isLoadingProjects.set(false);
      },
    });

    this.projectService.projectDeleted$.subscribe((deletedId) => {
      this.projects.update((list) => list.filter((p) => p.projectId !== deletedId));
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
    this.activeSection.set(match ? match[2] : null);
  }
}
