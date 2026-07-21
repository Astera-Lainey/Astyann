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
import { Router } from '@angular/router';
import { RequirementsService } from '../../../../core/services/requirements.service';
import { PcsfAttribute, PcsfConditionalFeatures, PcsfData } from '../../../../core/models/requirement.models';
import { PcsfValidateResponse } from '../../../../core/models/project.models';

interface EditableActorRow { id: string; name: string; type: string; desc: string; }
interface EditableModuleRow { id: string; name: string; desc: string; }
interface EditableEntityRow { id: string; name: string; tableName: string; pkStrategy: string; auditFields: boolean; softDelete: boolean; }
interface EditableRelationshipRow { id: string; fromEntityId: string; toEntityId: string; cardinality: string; label: string; }
interface EditableAccessControlRow { id: string; moduleId: string; entityId: string; operation: string; allowedRoles: string; }
interface EditableErrorCodeRow { id: string; code: string; httpStatus: string; messageTemplate: string; exceptionClass: string; moduleId: string; }
interface EditableScreenRow { name: string; type: string; entityId: string; moduleId: string; routePath: string; requiredRoles: string; }
interface EditableNavItemRow { label: string; routePath: string; icon: string; visibleToRoles: string; moduleId: string; }

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
  readonly showApproveConfirm = signal(false);
  readonly showApproveSuccess = signal(false);

  // Inline field edit
  readonly editingPath = signal<string | null>(null);
  readonly editingValue = signal('');
  readonly isSavingField = signal(false);
  readonly fieldSaveError = signal<string | null>(null);

  // Actors edit
  readonly editingActors = signal<EditableActorRow[] | null>(null);
  readonly isSavingActors = signal(false);
  readonly actorSaveError = signal<string | null>(null);

  // Modules edit
  readonly editingModules = signal<EditableModuleRow[] | null>(null);
  readonly isSavingModules = signal(false);
  readonly moduleSaveError = signal<string | null>(null);

  // Entities edit
  readonly editingEntities = signal<EditableEntityRow[] | null>(null);
  readonly isSavingEntities = signal(false);
  readonly entitySaveError = signal<string | null>(null);

  // Relationships edit
  readonly editingRelationships = signal<EditableRelationshipRow[] | null>(null);
  readonly isSavingRelationships = signal(false);
  readonly relationshipSaveError = signal<string | null>(null);

  // Access control rules edit
  readonly editingAccessControlRules = signal<EditableAccessControlRow[] | null>(null);
  readonly isSavingAccessControlRules = signal(false);
  readonly accessControlSaveError = signal<string | null>(null);

  // Error codes edit
  readonly editingErrorCodes = signal<EditableErrorCodeRow[] | null>(null);
  readonly isSavingErrorCodes = signal(false);
  readonly errorCodeSaveError = signal<string | null>(null);

  // Screens edit
  readonly editingScreens = signal<EditableScreenRow[] | null>(null);
  readonly isSavingScreens = signal(false);
  readonly screenSaveError = signal<string | null>(null);

  // Nav items edit
  readonly editingNavItems = signal<EditableNavItemRow[] | null>(null);
  readonly isSavingNavItems = signal(false);
  readonly navItemSaveError = signal<string | null>(null);

  // Conditional features / public access toggles
  readonly savingConditionalPath = signal<string | null>(null);
  readonly savingPublicActorFlag = signal(false);

  // Change request
  readonly instruction = signal('');
  readonly isSubmittingChange = signal(false);
  readonly changeError = signal<string | null>(null);
  readonly canSubmitChange = computed(() => this.instruction().trim().length > 0);

  readonly hasActors = computed(() => (this.pcsf()?.actors ?? []).length > 0);
  readonly hasModules = computed(() => (this.pcsf()?.modules ?? []).length > 0);
  readonly hasBusinessRules = computed(() => (this.pcsf()?.businessRules ?? []).length > 0);
  readonly hasTechStack = computed(() => !!(this.pcsf()?.databaseConfig || this.pcsf()?.apiConfig));
  readonly hasConditionalFeatures = computed(() => !!this.pcsf()?.conditionalFeatures);
  readonly hasPublicAccess = computed(() => !!this.pcsf()?.publicAccess);
  readonly hasEntities = computed(() => (this.pcsf()?.entities ?? []).length > 0);
  readonly hasRelationships = computed(() => (this.pcsf()?.relationships ?? []).length > 0);
  readonly hasStatusMachines = computed(() => (this.pcsf()?.statusMachines ?? []).length > 0);
  readonly hasAccessControlRules = computed(() => (this.pcsf()?.accessControlRules ?? []).length > 0);
  readonly hasErrorCodes = computed(() => (this.pcsf()?.errorCodes ?? []).length > 0);
  readonly hasEndpoints = computed(() => (this.pcsf()?.endpoints ?? []).length > 0);
  readonly hasNfr = computed(() => !!this.pcsf()?.nonFunctionalRequirements);
  readonly hasScreens = computed(() => (this.pcsf()?.userInterface?.screens ?? []).length > 0);
  readonly hasNavItems = computed(() => (this.pcsf()?.userInterface?.navigation ?? []).length > 0);
  readonly hasInfraConfig = computed(() => !!this.pcsf()?.infrastructureConfig);
  readonly hasValidation = computed(() => !!this.pcsf()?.validation);

  readonly isApproved = computed(() => this.localStatus() === 'APPROVED');

  readonly hasAnyData = computed(() =>
    !!this.pcsf()?.project || this.hasActors() || this.hasModules() || this.hasBusinessRules()
    || this.hasTechStack() || this.hasConditionalFeatures() || this.hasPublicAccess()
    || this.hasEntities() || this.hasRelationships() || this.hasStatusMachines()
    || this.hasAccessControlRules() || this.hasErrorCodes() || this.hasEndpoints()
    || this.hasNfr() || this.hasScreens() || this.hasNavItems() || this.hasInfraConfig(),
  );

  readonly entityOptions = computed(() =>
    (this.pcsf()?.entities ?? []).map(e => ({ id: e.id ?? '', name: e.name?.value ?? e.id ?? '—' })),
  );
  readonly moduleOptions = computed(() =>
    (this.pcsf()?.modules ?? []).map(m => ({ id: m.id ?? '', name: m.name?.value ?? m.id ?? '—' })),
  );

  entityName(id: string | undefined): string {
    if (!id) return '—';
    return this.pcsf()?.entities?.find(e => e.id === id)?.name?.value ?? id;
  }

  moduleName(id: string | undefined): string {
    if (!id) return '—';
    return this.pcsf()?.modules?.find(m => m.id === id)?.name?.value ?? id;
  }

  attributeSummary(attr: PcsfAttribute): string {
    const parts: string[] = [];
    if (attr.columnName?.value) parts.push(attr.columnName.value);
    if (attr.javaType?.value) parts.push(attr.javaType.value);
    const c = attr.constraints;
    if (c?.required?.value) parts.push('required');
    if (c?.unique?.value) parts.push('unique');
    if (c?.minLength?.value != null) parts.push(`min ${c.minLength.value}`);
    if (c?.maxLength?.value != null) parts.push(`max ${c.maxLength.value}`);
    if (c?.pattern?.value) parts.push(`pattern ${c.pattern.value}`);
    return parts.join(' · ');
  }

  constructor(
    private readonly requirementsService: RequirementsService,
    private readonly router: Router,
  ) {}

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

  openApproveConfirm(): void {
    this.approveError.set(null);
    this.showApproveConfirm.set(true);
  }

  cancelApproveConfirm(): void {
    if (this.isApproving()) return;
    this.showApproveConfirm.set(false);
    this.approveError.set(null);
  }

  confirmApprove(): void {
    this.approveError.set(null);
    this.isApproving.set(true);
    this.requirementsService.approve(this.projectId).subscribe({
      next: () => {
        this.isApproving.set(false);
        this.localStatus.set('APPROVED');
        this.showApproveConfirm.set(false);
        this.showApproveSuccess.set(true);
      },
      error: () => {
        this.isApproving.set(false);
        this.approveError.set('Could not approve. Please try again.');
      },
    });
  }

  goToNextStep(): void {
    this.showApproveSuccess.set(false);
    this.router.navigate(['/app/projects', this.projectId, 'design']);
  }

  // ── Inline field edit (generic — used for all scalar PCSF fields) ───────────

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

  onFieldKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !(event.target instanceof HTMLTextAreaElement)) {
      this.saveField();
    } else if (event.key === 'Escape') {
      this.cancelEdit();
    }
  }

  // ── Actors edit ───────────────────────────────────────────────────────────────

  startEditActors(): void {
    const actors = this.pcsf()?.actors ?? [];
    this.editingActors.set(
      actors.map(a => ({
        id: a.id ?? '',
        name: a.name?.value ?? '',
        type: a.type?.value ?? 'INTERNAL',
        desc: a.description?.value ?? '',
      })),
    );
    this.actorSaveError.set(null);
  }

  cancelEditActors(): void {
    this.editingActors.set(null);
    this.actorSaveError.set(null);
  }

  addActorRow(): void {
    this.editingActors.set([...(this.editingActors() ?? []), { id: '', name: '', type: 'INTERNAL', desc: '' }]);
  }

  removeActorRow(index: number): void {
    const rows = [...(this.editingActors() ?? [])];
    rows.splice(index, 1);
    this.editingActors.set(rows);
  }

  updateActorField(index: number, field: 'name' | 'type' | 'desc', value: string): void {
    const rows = [...(this.editingActors() ?? [])];
    rows[index] = { ...rows[index], [field]: value };
    this.editingActors.set(rows);
  }

  saveActors(): void {
    const rows = this.editingActors();
    if (!rows) return;
    const value = rows
      .filter(r => r.name.trim())
      .map(r => `${r.id}|${r.name.trim()}|${r.type}|${r.desc.trim()}`)
      .join('\n');
    this.isSavingActors.set(true);
    this.actorSaveError.set(null);
    this.requirementsService.patchField(this.projectId, { path: 'actors', value }).subscribe({
      next: () => {
        this.isSavingActors.set(false);
        this.editingActors.set(null);
        this.load();
      },
      error: () => {
        this.isSavingActors.set(false);
        this.actorSaveError.set('Could not save actors. Please try again.');
      },
    });
  }

  // ── Modules edit ──────────────────────────────────────────────────────────────

  startEditModules(): void {
    const modules = this.pcsf()?.modules ?? [];
    this.editingModules.set(
      modules.map(m => ({
        id: m.id ?? '',
        name: m.name?.value ?? '',
        desc: m.description?.value ?? '',
      })),
    );
    this.moduleSaveError.set(null);
  }

  cancelEditModules(): void {
    this.editingModules.set(null);
    this.moduleSaveError.set(null);
  }

  addModuleRow(): void {
    this.editingModules.set([...(this.editingModules() ?? []), { id: '', name: '', desc: '' }]);
  }

  removeModuleRow(index: number): void {
    const rows = [...(this.editingModules() ?? [])];
    rows.splice(index, 1);
    this.editingModules.set(rows);
  }

  updateModuleField(index: number, field: 'name' | 'desc', value: string): void {
    const rows = [...(this.editingModules() ?? [])];
    rows[index] = { ...rows[index], [field]: value };
    this.editingModules.set(rows);
  }

  saveModules(): void {
    const rows = this.editingModules();
    if (!rows) return;
    const value = rows
      .filter(r => r.name.trim())
      .map(r => `${r.id}|${r.name.trim()}|${r.desc.trim()}`)
      .join('\n');
    this.isSavingModules.set(true);
    this.moduleSaveError.set(null);
    this.requirementsService.patchField(this.projectId, { path: 'modules', value }).subscribe({
      next: () => {
        this.isSavingModules.set(false);
        this.editingModules.set(null);
        this.load();
      },
      error: () => {
        this.isSavingModules.set(false);
        this.moduleSaveError.set('Could not save modules. Please try again.');
      },
    });
  }

  // ── Entities edit ─────────────────────────────────────────────────────────────

  startEditEntities(): void {
    const entities = this.pcsf()?.entities ?? [];
    this.editingEntities.set(
      entities.map(e => ({
        id: e.id ?? '',
        name: e.name?.value ?? '',
        tableName: e.tableName?.value ?? '',
        pkStrategy: e.primaryKeyStrategy ?? 'UUID',
        auditFields: e.auditFields ?? true,
        softDelete: e.softDelete?.value ?? false,
      })),
    );
    this.entitySaveError.set(null);
  }

  cancelEditEntities(): void {
    this.editingEntities.set(null);
    this.entitySaveError.set(null);
  }

  addEntityRow(): void {
    this.editingEntities.set([
      ...(this.editingEntities() ?? []),
      { id: '', name: '', tableName: '', pkStrategy: 'UUID', auditFields: true, softDelete: false },
    ]);
  }

  removeEntityRow(index: number): void {
    const rows = [...(this.editingEntities() ?? [])];
    rows.splice(index, 1);
    this.editingEntities.set(rows);
  }

  updateEntityText(index: number, field: 'name' | 'tableName' | 'pkStrategy', value: string): void {
    const rows = [...(this.editingEntities() ?? [])];
    rows[index] = { ...rows[index], [field]: value };
    this.editingEntities.set(rows);
  }

  updateEntityFlag(index: number, field: 'auditFields' | 'softDelete', value: boolean): void {
    const rows = [...(this.editingEntities() ?? [])];
    rows[index] = { ...rows[index], [field]: value };
    this.editingEntities.set(rows);
  }

  saveEntities(): void {
    const rows = this.editingEntities();
    if (!rows) return;
    const value = rows
      .filter(r => r.name.trim())
      .map(r => `${r.id}|${r.name.trim()}|${r.tableName.trim()}|${r.pkStrategy.trim()}|${r.auditFields ? 'true' : 'false'}|${r.softDelete ? 'true' : 'false'}`)
      .join('\n');
    this.isSavingEntities.set(true);
    this.entitySaveError.set(null);
    this.requirementsService.patchField(this.projectId, { path: 'entities', value }).subscribe({
      next: () => {
        this.isSavingEntities.set(false);
        this.editingEntities.set(null);
        this.load();
      },
      error: () => {
        this.isSavingEntities.set(false);
        this.entitySaveError.set('Could not save entities. Please try again.');
      },
    });
  }

  // ── Relationships edit ────────────────────────────────────────────────────────

  startEditRelationships(): void {
    const rels = this.pcsf()?.relationships ?? [];
    this.editingRelationships.set(
      rels.map(r => ({
        id: r.id ?? '',
        fromEntityId: r.fromEntityId ?? '',
        toEntityId: r.toEntityId ?? '',
        cardinality: r.cardinality?.value ?? '',
        label: r.label?.value ?? '',
      })),
    );
    this.relationshipSaveError.set(null);
  }

  cancelEditRelationships(): void {
    this.editingRelationships.set(null);
    this.relationshipSaveError.set(null);
  }

  addRelationshipRow(): void {
    const firstEntity = this.entityOptions()[0]?.id ?? '';
    this.editingRelationships.set([
      ...(this.editingRelationships() ?? []),
      { id: '', fromEntityId: firstEntity, toEntityId: firstEntity, cardinality: 'ONE_TO_MANY', label: '' },
    ]);
  }

  removeRelationshipRow(index: number): void {
    const rows = [...(this.editingRelationships() ?? [])];
    rows.splice(index, 1);
    this.editingRelationships.set(rows);
  }

  updateRelationshipField(index: number, field: 'fromEntityId' | 'toEntityId' | 'cardinality' | 'label', value: string): void {
    const rows = [...(this.editingRelationships() ?? [])];
    rows[index] = { ...rows[index], [field]: value };
    this.editingRelationships.set(rows);
  }

  saveRelationships(): void {
    const rows = this.editingRelationships();
    if (!rows) return;
    const value = rows
      .filter(r => r.fromEntityId && r.toEntityId)
      .map(r => `${r.id}|${r.fromEntityId}|${r.toEntityId}|${r.cardinality.trim()}|${r.label.trim()}`)
      .join('\n');
    this.isSavingRelationships.set(true);
    this.relationshipSaveError.set(null);
    this.requirementsService.patchField(this.projectId, { path: 'relationships', value }).subscribe({
      next: () => {
        this.isSavingRelationships.set(false);
        this.editingRelationships.set(null);
        this.load();
      },
      error: () => {
        this.isSavingRelationships.set(false);
        this.relationshipSaveError.set('Could not save relationships. Please try again.');
      },
    });
  }

  // ── Access control rules edit ─────────────────────────────────────────────────

  startEditAccessControlRules(): void {
    const rules = this.pcsf()?.accessControlRules ?? [];
    this.editingAccessControlRules.set(
      rules.map(r => ({
        id: r.id ?? '',
        moduleId: r.moduleId ?? '',
        entityId: r.entityId ?? '',
        operation: r.operation ?? 'READ',
        allowedRoles: (r.allowedRoles?.value ?? []).join(', '),
      })),
    );
    this.accessControlSaveError.set(null);
  }

  cancelEditAccessControlRules(): void {
    this.editingAccessControlRules.set(null);
    this.accessControlSaveError.set(null);
  }

  addAccessControlRow(): void {
    const firstModule = this.moduleOptions()[0]?.id ?? '';
    const firstEntity = this.entityOptions()[0]?.id ?? '';
    this.editingAccessControlRules.set([
      ...(this.editingAccessControlRules() ?? []),
      { id: '', moduleId: firstModule, entityId: firstEntity, operation: 'READ', allowedRoles: '' },
    ]);
  }

  removeAccessControlRow(index: number): void {
    const rows = [...(this.editingAccessControlRules() ?? [])];
    rows.splice(index, 1);
    this.editingAccessControlRules.set(rows);
  }

  updateAccessControlField(index: number, field: 'moduleId' | 'entityId' | 'operation' | 'allowedRoles', value: string): void {
    const rows = [...(this.editingAccessControlRules() ?? [])];
    rows[index] = { ...rows[index], [field]: value };
    this.editingAccessControlRules.set(rows);
  }

  saveAccessControlRules(): void {
    const rows = this.editingAccessControlRules();
    if (!rows) return;
    const value = rows
      .filter(r => r.moduleId || r.entityId)
      .map(r => `${r.id}|${r.moduleId}|${r.entityId}|${r.operation.trim()}|${r.allowedRoles.trim()}`)
      .join('\n');
    this.isSavingAccessControlRules.set(true);
    this.accessControlSaveError.set(null);
    this.requirementsService.patchField(this.projectId, { path: 'accessControlRules', value }).subscribe({
      next: () => {
        this.isSavingAccessControlRules.set(false);
        this.editingAccessControlRules.set(null);
        this.load();
      },
      error: () => {
        this.isSavingAccessControlRules.set(false);
        this.accessControlSaveError.set('Could not save access control rules. Please try again.');
      },
    });
  }

  // ── Error codes edit ──────────────────────────────────────────────────────────

  startEditErrorCodes(): void {
    const codes = this.pcsf()?.errorCodes ?? [];
    this.editingErrorCodes.set(
      codes.map(c => ({
        id: c.id ?? '',
        code: c.code ?? '',
        httpStatus: c.httpStatus != null ? String(c.httpStatus) : '400',
        messageTemplate: c.messageTemplate ?? '',
        exceptionClass: c.exceptionClass ?? '',
        moduleId: c.moduleId ?? '',
      })),
    );
    this.errorCodeSaveError.set(null);
  }

  cancelEditErrorCodes(): void {
    this.editingErrorCodes.set(null);
    this.errorCodeSaveError.set(null);
  }

  addErrorCodeRow(): void {
    this.editingErrorCodes.set([
      ...(this.editingErrorCodes() ?? []),
      { id: '', code: '', httpStatus: '400', messageTemplate: '', exceptionClass: '', moduleId: this.moduleOptions()[0]?.id ?? '' },
    ]);
  }

  removeErrorCodeRow(index: number): void {
    const rows = [...(this.editingErrorCodes() ?? [])];
    rows.splice(index, 1);
    this.editingErrorCodes.set(rows);
  }

  updateErrorCodeField(index: number, field: 'code' | 'httpStatus' | 'messageTemplate' | 'exceptionClass' | 'moduleId', value: string): void {
    const rows = [...(this.editingErrorCodes() ?? [])];
    rows[index] = { ...rows[index], [field]: value };
    this.editingErrorCodes.set(rows);
  }

  saveErrorCodes(): void {
    const rows = this.editingErrorCodes();
    if (!rows) return;
    const value = rows
      .filter(r => r.code.trim())
      .map(r => `${r.id}|${r.code.trim()}|${r.httpStatus.trim()}|${r.messageTemplate.trim()}|${r.exceptionClass.trim()}|${r.moduleId}`)
      .join('\n');
    this.isSavingErrorCodes.set(true);
    this.errorCodeSaveError.set(null);
    this.requirementsService.patchField(this.projectId, { path: 'errorCodes', value }).subscribe({
      next: () => {
        this.isSavingErrorCodes.set(false);
        this.editingErrorCodes.set(null);
        this.load();
      },
      error: () => {
        this.isSavingErrorCodes.set(false);
        this.errorCodeSaveError.set('Could not save error codes. Please try again.');
      },
    });
  }

  // ── Screens edit ──────────────────────────────────────────────────────────────

  startEditScreens(): void {
    const screens = this.pcsf()?.userInterface?.screens ?? [];
    this.editingScreens.set(
      screens.map(s => ({
        name: s.name ?? '',
        type: s.type ?? '',
        entityId: s.entityId ?? '',
        moduleId: s.moduleId ?? '',
        routePath: s.routePath ?? '',
        requiredRoles: (s.requiredRoles ?? []).join(', '),
      })),
    );
    this.screenSaveError.set(null);
  }

  cancelEditScreens(): void {
    this.editingScreens.set(null);
    this.screenSaveError.set(null);
  }

  addScreenRow(): void {
    this.editingScreens.set([
      ...(this.editingScreens() ?? []),
      { name: '', type: 'LIST', entityId: this.entityOptions()[0]?.id ?? '', moduleId: this.moduleOptions()[0]?.id ?? '', routePath: '', requiredRoles: '' },
    ]);
  }

  removeScreenRow(index: number): void {
    const rows = [...(this.editingScreens() ?? [])];
    rows.splice(index, 1);
    this.editingScreens.set(rows);
  }

  updateScreenField(index: number, field: 'name' | 'type' | 'entityId' | 'moduleId' | 'routePath' | 'requiredRoles', value: string): void {
    const rows = [...(this.editingScreens() ?? [])];
    rows[index] = { ...rows[index], [field]: value };
    this.editingScreens.set(rows);
  }

  saveScreens(): void {
    const rows = this.editingScreens();
    if (!rows) return;
    const value = rows
      .filter(r => r.name.trim())
      .map(r => `${r.name.trim()}|${r.type.trim()}|${r.entityId}|${r.moduleId}|${r.routePath.trim()}|${r.requiredRoles.trim()}`)
      .join('\n');
    this.isSavingScreens.set(true);
    this.screenSaveError.set(null);
    this.requirementsService.patchField(this.projectId, { path: 'userInterface.screens', value }).subscribe({
      next: () => {
        this.isSavingScreens.set(false);
        this.editingScreens.set(null);
        this.load();
      },
      error: () => {
        this.isSavingScreens.set(false);
        this.screenSaveError.set('Could not save screens. Please try again.');
      },
    });
  }

  // ── Navigation items edit ─────────────────────────────────────────────────────

  startEditNavItems(): void {
    const items = this.pcsf()?.userInterface?.navigation ?? [];
    this.editingNavItems.set(
      items.map(n => ({
        label: n.label ?? '',
        routePath: n.routePath ?? '',
        icon: n.icon ?? '',
        visibleToRoles: (n.visibleToRoles ?? []).join(', '),
        moduleId: n.moduleId ?? '',
      })),
    );
    this.navItemSaveError.set(null);
  }

  cancelEditNavItems(): void {
    this.editingNavItems.set(null);
    this.navItemSaveError.set(null);
  }

  addNavItemRow(): void {
    this.editingNavItems.set([
      ...(this.editingNavItems() ?? []),
      { label: '', routePath: '', icon: '', visibleToRoles: '', moduleId: this.moduleOptions()[0]?.id ?? '' },
    ]);
  }

  removeNavItemRow(index: number): void {
    const rows = [...(this.editingNavItems() ?? [])];
    rows.splice(index, 1);
    this.editingNavItems.set(rows);
  }

  updateNavItemField(index: number, field: 'label' | 'routePath' | 'icon' | 'visibleToRoles' | 'moduleId', value: string): void {
    const rows = [...(this.editingNavItems() ?? [])];
    rows[index] = { ...rows[index], [field]: value };
    this.editingNavItems.set(rows);
  }

  saveNavItems(): void {
    const rows = this.editingNavItems();
    if (!rows) return;
    const value = rows
      .filter(r => r.label.trim())
      .map(r => `${r.label.trim()}|${r.routePath.trim()}|${r.icon.trim()}|${r.visibleToRoles.trim()}|${r.moduleId}`)
      .join('\n');
    this.isSavingNavItems.set(true);
    this.navItemSaveError.set(null);
    this.requirementsService.patchField(this.projectId, { path: 'userInterface.navigation', value }).subscribe({
      next: () => {
        this.isSavingNavItems.set(false);
        this.editingNavItems.set(null);
        this.load();
      },
      error: () => {
        this.isSavingNavItems.set(false);
        this.navItemSaveError.set('Could not save navigation items. Please try again.');
      },
    });
  }

  // ── Conditional features ──────────────────────────────────────────────────────

  conditionalFlagValue(flag: { triggered?: boolean; required?: { value?: boolean | null } } | undefined): boolean {
    return flag?.required?.value ?? flag?.triggered ?? false;
  }

  toggleConditionalFeature(path: string, currentValue: boolean): void {
    const snapshot = this.pcsf();
    if (!snapshot) return;
    const newValue = !currentValue;
    this.pcsf.set(this.applyConditionalPatch(snapshot, path, newValue));
    this.savingConditionalPath.set(path);
    this.requirementsService
      .patchField(this.projectId, { path, value: newValue ? 'true' : 'false' })
      .subscribe({
        next: () => this.savingConditionalPath.set(null),
        error: () => {
          this.savingConditionalPath.set(null);
          this.pcsf.set(snapshot);
        },
      });
  }

  private applyConditionalPatch(data: PcsfData, path: string, value: boolean): PcsfData {
    const key = path.split('.')[1] as keyof PcsfConditionalFeatures;
    const cf = data.conditionalFeatures ?? {};
    const existing = cf[key] ?? {};
    return {
      ...data,
      conditionalFeatures: {
        ...cf,
        [key]: { ...existing, triggered: value, required: { value, source: 'QA', status: 'CONFIRMED' } },
      },
    };
  }

  // ── Public access toggle ─────────────────────────────────────────────────────

  togglePublicActor(currentValue: boolean): void {
    const snapshot = this.pcsf();
    if (!snapshot) return;
    const newValue = !currentValue;
    this.pcsf.set({
      ...snapshot,
      publicAccess: {
        ...snapshot.publicAccess,
        hasPublicActor: { value: newValue, source: 'QA', status: 'CONFIRMED' },
      },
    });
    this.savingPublicActorFlag.set(true);
    this.requirementsService
      .patchField(this.projectId, { path: 'publicAccess.hasPublicActor.value', value: newValue ? 'true' : 'false' })
      .subscribe({
        next: () => this.savingPublicActorFlag.set(false),
        error: () => {
          this.savingPublicActorFlag.set(false);
          this.pcsf.set(snapshot);
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
