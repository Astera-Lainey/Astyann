import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  computed,
  signal,
} from '@angular/core';
import { RequirementsService } from '../../../../core/services/requirements.service';
import { PcsfData } from '../../../../core/models/requirement.models';
import { PcsfValidateResponse } from '../../../../core/models/project.models';

@Component({
  selector: 'app-requirements-view',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './requirements-view.html',
  styleUrl: './requirements-view.scss',
})
export class RequirementsViewComponent implements OnChanges {
  @Input({ required: true }) projectId!: string;
  @Input() pcsfStatus = 'UNDER_REVIEW';
  @Output() readonly restartPolling = new EventEmitter<void>();

  readonly pcsf = signal<PcsfData | null>(null);
  readonly isLoading = signal(true);
  readonly loadError = signal<string | null>(null);

  /** Tracks status locally so validate/approve updates are reflected immediately. */
  readonly localStatus = signal('UNDER_REVIEW');

  // Validate
  readonly isValidating = signal(false);
  readonly validateResult = signal<PcsfValidateResponse | null>(null);

  // Approve
  readonly isApproving = signal(false);
  readonly approveError = signal<string | null>(null);

  // Inline field edit
  readonly editingPath = signal<string | null>(null);
  readonly editingValue = signal('');
  readonly isSavingField = signal(false);
  readonly fieldSaveError = signal<string | null>(null);

  // Change request
  readonly instruction = signal('');
  readonly isSubmittingChange = signal(false);
  readonly changeError = signal<string | null>(null);
  readonly canSubmitChange = computed(() => this.instruction().trim().length > 0);

  readonly hasActors = computed(() => (this.pcsf()?.actors ?? []).length > 0);
  readonly hasModules = computed(() => (this.pcsf()?.modules ?? []).length > 0);
  readonly hasBusinessRules = computed(() => (this.pcsf()?.businessRules ?? []).length > 0);
  readonly hasTechStack = computed(() => !!(this.pcsf()?.databaseConfig || this.pcsf()?.apiConfig));

  constructor(private readonly requirementsService: RequirementsService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['pcsfStatus']) this.localStatus.set(this.pcsfStatus);
    if (changes['projectId'] && this.projectId) this.load();
  }

  private load(): void {
    this.isLoading.set(true);
    this.loadError.set(null);
    this.requirementsService.getPcsf(this.projectId).subscribe({
      next: (data) => {
        this.pcsf.set(data);
        this.isLoading.set(false);
      },
      error: () => {
        this.isLoading.set(false);
        this.loadError.set('Could not load requirements. Please try again.');
      },
    });
  }

  // ── Validate ────────────────────────────────────────────────────────────────

  validate(): void {
    this.isValidating.set(true);
    this.validateResult.set(null);
    this.requirementsService.validatePcsf(this.projectId).subscribe({
      next: (result) => {
        this.isValidating.set(false);
        this.validateResult.set(result);
        if (result.valid) this.localStatus.set('VALIDATED');
      },
      error: () => {
        this.isValidating.set(false);
      },
    });
  }

  // ── Approve ─────────────────────────────────────────────────────────────────

  approve(): void {
    this.approveError.set(null);
    this.isApproving.set(true);
    this.requirementsService.approve(this.projectId).subscribe({
      next: () => {
        this.isApproving.set(false);
        this.localStatus.set('APPROVED');
      },
      error: () => {
        this.isApproving.set(false);
        this.approveError.set('Could not approve. Please try again.');
      },
    });
  }

  // ── Inline field edit ────────────────────────────────────────────────────────

  startEdit(path: string, currentValue: string): void {
    this.editingPath.set(path);
    this.editingValue.set(currentValue);
    this.fieldSaveError.set(null);
  }

  cancelEdit(): void {
    this.editingPath.set(null);
    this.fieldSaveError.set(null);
  }

  onEditInput(event: Event): void {
    this.editingValue.set((event.target as HTMLInputElement | HTMLTextAreaElement).value);
  }

  saveField(): void {
    const path = this.editingPath();
    if (!path) return;
    this.isSavingField.set(true);
    this.fieldSaveError.set(null);
    this.requirementsService
      .patchField(this.projectId, { path, value: this.editingValue() })
      .subscribe({
        next: () => {
          this.isSavingField.set(false);
          this.editingPath.set(null);
          this.load();
        },
        error: () => {
          this.isSavingField.set(false);
          this.fieldSaveError.set('Could not save. Please try again.');
        },
      });
  }

  // ── Change request ───────────────────────────────────────────────────────────

  onInstructionInput(event: Event): void {
    this.instruction.set((event.target as HTMLTextAreaElement).value);
  }

  submitAndRegenerate(): void {
    if (!this.canSubmitChange()) return;
    const instructions = this.instruction().trim();
    this.changeError.set(null);
    this.isSubmittingChange.set(true);
    this.requirementsService.submitChangeRequest(this.projectId, instructions).subscribe({
      next: () => {
        this.requirementsService.regenerate(this.projectId).subscribe({
          next: () => {
            this.instruction.set('');
            this.isSubmittingChange.set(false);
            this.restartPolling.emit();
          },
          error: () => {
            this.isSubmittingChange.set(false);
            this.changeError.set('Regeneration failed. Please try again.');
          },
        });
      },
      error: () => {
        this.isSubmittingChange.set(false);
        this.changeError.set('Could not submit change request. Please try again.');
      },
    });
  }
}
