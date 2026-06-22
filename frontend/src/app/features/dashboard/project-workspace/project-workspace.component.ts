import { ChangeDetectionStrategy, Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink, RouterLinkActive } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { switchMap } from 'rxjs';
import { ProjectService } from '../../../core/services/project.service';
import { Project } from '../../../core/models/project.models';

/** The 6 workspace sections, matching the React source's switch cases exactly. */
export type WorkspaceSection =
  | 'requirements'
  | 'design'
  | 'documents'
  | 'code'
  | 'versions'
  | 'deploy';

const SECTION_LABELS: Record<WorkspaceSection, string> = {
  requirements: 'Requirements',
  design: 'System Design',
  documents: 'Documentation',
  code: 'Code',
  versions: 'Version History',
  deploy: 'Deployment',
};

/**
 * Project Workspace shell — converted from `app.projects.$id.$section.tsx`.
 *
 * Loads the project via `GET /projects/{projectId}` (API-PROJ-03) for the
 * header/breadcrumb, then switches on the `:section` route param to render
 * one of 6 placeholder views. The originals (`RequirementsView`,
 * `SystemDesignView`, `DocumentsView`, `CodeView`, `VersionsView`,
 * `DeployView`) were referenced by the React source but never provided —
 * each is out of scope here and shown as "coming soon" until built in a
 * future batch, per project decision.
 *
 * Route params are observed reactively (`paramMap`, not a one-time
 * snapshot) because navigating between sections of the *same* project
 * re-uses this component rather than destroying/recreating it — Angular's
 * router does this by default when only params change on an otherwise
 * identical route. A snapshot would miss those in-place updates.
 */
@Component({
  selector: 'app-project-workspace-page',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './project-workspace.component.html',
  styleUrl: './project-workspace.component.scss',
})
export class ProjectWorkspaceComponent implements OnInit {
  readonly project = signal<Project | null>(null);
  readonly section = signal<WorkspaceSection | null>(null);
  readonly isLoading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly sectionLabels = SECTION_LABELS;
  readonly sectionOrder: WorkspaceSection[] = [
    'requirements',
    'design',
    'documents',
    'code',
    'versions',
    'deploy',
  ];

  constructor(
    private readonly route: ActivatedRoute,
    private readonly projectService: ProjectService,
  ) {}

  ngOnInit(): void {
    this.route.paramMap
      .pipe(
        switchMap((params) => {
          const section = params.get('section') as WorkspaceSection | null;
          this.section.set(section);
          this.isLoading.set(true);
          this.loadError.set(null);
          const projectId = params.get('id')!;
          return this.projectService.getById(projectId);
        }),
      )
      .subscribe({
        next: (project) => {
          this.project.set(project);
          this.isLoading.set(false);
        },
        error: (error: HttpErrorResponse) => {
          this.isLoading.set(false);
          this.loadError.set(
            error.status === 404
              ? 'This project could not be found.'
              : 'Could not load this project. Please try again later.',
          );
        },
      });
  }

  get currentSectionLabel(): string {
    const section = this.section();
    return section ? SECTION_LABELS[section] : '';
  }

  get isKnownSection(): boolean {
    return this.section() !== null && this.section()! in SECTION_LABELS;
  }
}
