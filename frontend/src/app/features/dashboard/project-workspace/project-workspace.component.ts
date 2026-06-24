import { ChangeDetectionStrategy, Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink, RouterLinkActive } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { switchMap } from 'rxjs';
import { ProjectService } from '../../../core/services/project.service';
import { Project } from '../../../core/models/project.models';
import { RequirementsViewComponent } from './requirements-view/requirements-view.component';

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

@Component({
  selector: 'app-project-workspace-page',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RequirementsViewComponent],
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
    'requirements', 'design', 'documents', 'code', 'versions', 'deploy',
  ];

  constructor(
    private readonly route: ActivatedRoute,
    private readonly projectService: ProjectService,
  ) {}

  ngOnInit(): void {
    this.route.paramMap
      .pipe(
        switchMap((params) => {
          this.section.set(params.get('section') as WorkspaceSection | null);
          this.isLoading.set(true);
          this.loadError.set(null);
          return this.projectService.getById(params.get('id')!);
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