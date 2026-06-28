import { ChangeDetectionStrategy, Component, Input, OnChanges, SimpleChanges, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RequirementsService } from '../../../../core/services/requirements.service';
import { RequirementItem, Requirements, RequirementsStatus } from '../../../../core/models/requirement.models';

@Component({
  selector: 'app-requirements-view',
  standalone: true,
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './requirements-view.html',
  styleUrl: './requirements-view.scss',
})
export class RequirementsViewComponent implements OnChanges {
  @Input({ required: true }) projectId!: string;

  readonly requirements = signal<Requirements | null>(null);
  readonly isLoading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly isApproving = signal(false);
  readonly approveError = signal<string | null>(null);
  readonly isApproved = signal(false);
  readonly instruction = signal('');
  readonly isSubmittingChange = signal(false);
  readonly changeError = signal<string | null>(null);
  readonly canSubmitChange = computed(() => this.instruction().trim().length > 0);

  readonly groupedFunctional = computed(() =>
    groupByCategory(this.requirements()?.content?.functionalRequirements ?? []),
  );
  readonly groupedNonFunctional = computed(() =>
    groupByCategory(this.requirements()?.content?.nonFunctionalRequirements ?? []),
  );
  readonly status = computed<RequirementsStatus | null>(() => this.requirements()?.status ?? null);

  constructor(private readonly requirementsService: RequirementsService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['projectId'] && this.projectId) this.load();
  }

  private load(): void {
    this.isLoading.set(true);
    this.loadError.set(null);
    this.isApproved.set(false);
    this.requirementsService.getCurrent(this.projectId).subscribe({
      next: (data) => { this.requirements.set(data); this.isLoading.set(false); },
      error: (error: HttpErrorResponse) => {
        if (error.status === 404) {
          this.generate();
        } else {
          this.isLoading.set(false);
          this.loadError.set('Could not load requirements. Please try again later.');
        }
      },
    });
  }

  private generate(): void {
    this.requirementsService.generate(this.projectId).subscribe({
      next: (data) => { this.requirements.set(data); this.isLoading.set(false); },
      error: () => { this.isLoading.set(false); this.loadError.set('Could not generate requirements. Please try again later.'); },
    });
  }

  approveAll(): void {
    this.approveError.set(null);
    this.isApproving.set(true);
    this.requirementsService.approve(this.projectId).subscribe({
      next: () => { this.isApproving.set(false); this.isApproved.set(true); },
      error: () => { this.isApproving.set(false); this.approveError.set('Could not approve requirements. Please try again.'); },
    });
  }

  submitAndRegenerate(): void {
    if (!this.canSubmitChange()) return;
    this.changeError.set(null);
    this.isSubmittingChange.set(true);
    this.requirementsService.submitChangeRequest(this.projectId, { instructions: this.instruction().trim() }).subscribe({
      next: (changeData) => {
        this.requirementsService.regenerate(this.projectId, { changeRequestId: changeData.changeRequestId }).subscribe({
          next: () => { this.instruction.set(''); this.isSubmittingChange.set(false); this.load(); },
          error: () => { this.isSubmittingChange.set(false); this.changeError.set('Regeneration failed. Please try again.'); },
        });
      },
      error: () => { this.isSubmittingChange.set(false); this.changeError.set('Could not submit change request. Please try again.'); },
    });
  }
}

function groupByCategory(items: RequirementItem[]): { category: string; items: RequirementItem[] }[] {
  const map = new Map<string, RequirementItem[]>();
  for (const item of items) {
    const existing = map.get(item.category);
    if (existing) existing.push(item);
    else map.set(item.category, [item]);
  }
  return Array.from(map.entries()).map(([category, items]) => ({ category, items }));
}
