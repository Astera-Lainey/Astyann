import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  OnInit,
  computed,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ProjectService } from '../../../core/services/project.service';
import { ProjectSummary } from '../../../core/models/project.models';
import { SparkleIconComponent } from '../../../shared/components/sparkle-icon/sparkle-icon.component';

@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, SparkleIconComponent],
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

  // ── Three-dot menu ──────────────────────────────────────────────────────────

  readonly openMenuId = signal<string | null>(null);

  @HostListener('document:click')
  onDocumentClick(): void {
    this.openMenuId.set(null);
  }

  toggleMenu(projectId: string, event: MouseEvent): void {
    event.stopPropagation();
    this.openMenuId.set(this.openMenuId() === projectId ? null : projectId);
  }

  // ── Edit modal ──────────────────────────────────────────────────────────────

  readonly editingProject = signal<ProjectSummary | null>(null);
  readonly isSaving = signal(false);
  readonly editError = signal<string | null>(null);
  editTitle = '';
  editDescription = '';

  openEditModal(project: ProjectSummary, event: MouseEvent): void {
    event.stopPropagation();
    this.openMenuId.set(null);
    this.editTitle = project.title;
    this.editDescription = project.description ?? '';
    this.editError.set(null);
    this.editingProject.set(project);
  }

  closeEditModal(): void {
    if (this.isSaving()) return;
    this.editingProject.set(null);
    this.editError.set(null);
  }

  saveEdit(): void {
    const project = this.editingProject();
    if (!project) return;
    const title = this.editTitle.trim();
    if (!title) {
      this.editError.set('Project name is required.');
      return;
    }
    this.isSaving.set(true);
    this.editError.set(null);
    this.projectService
      .update(project.projectId, { title, description: this.editDescription.trim() })
      .subscribe({
        next: (updated) => {
          this.projects.update((list) =>
            list.map((p) =>
              p.projectId === updated.projectId
                ? { ...p, title: updated.title, description: updated.description }
                : p,
            ),
          );
          this.isSaving.set(false);
          this.editingProject.set(null);
        },
        error: () => {
          this.isSaving.set(false);
          this.editError.set('Could not save changes. Please try again.');
        },
      });
  }

  // ── Delete confirmation ─────────────────────────────────────────────────────

  readonly deletingProject = signal<ProjectSummary | null>(null);
  readonly isDeleting = signal(false);
  readonly deleteError = signal<string | null>(null);

  openDeleteConfirm(project: ProjectSummary, event: MouseEvent): void {
    event.stopPropagation();
    this.openMenuId.set(null);
    this.deleteError.set(null);
    this.deletingProject.set(project);
  }

  closeDeleteConfirm(): void {
    if (this.isDeleting()) return;
    this.deletingProject.set(null);
    this.deleteError.set(null);
  }

  confirmDelete(): void {
    const project = this.deletingProject();
    if (!project) return;
    this.isDeleting.set(true);
    this.deleteError.set(null);
    this.projectService.delete(project.projectId).subscribe({
      next: () => {
        this.projects.update((list) =>
          list.filter((p) => p.projectId !== project.projectId),
        );
        this.isDeleting.set(false);
        this.deletingProject.set(null);
      },
      error: () => {
        this.isDeleting.set(false);
        this.deleteError.set('Could not delete this project. Please try again.');
      },
    });
  }

  // ── Data loading ────────────────────────────────────────────────────────────

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

  initialFor(title: string): string {
    return title.charAt(0).toUpperCase();
  }
}
