// ── PCSF types (mirrors the Java Pcsf domain model) ───────────────────────────

export interface PcsfFieldValue<T> {
  value?: T | null;
  source?: string;
  status?: string;
}

export interface PcsfProject {
  name?: PcsfFieldValue<string>;
  description?: PcsfFieldValue<string>;
  displayName?: PcsfFieldValue<string>;
}

export interface PcsfActor {
  id?: string;
  name?: PcsfFieldValue<string>;
  type?: PcsfFieldValue<string>;
  description?: PcsfFieldValue<string>;
}

export interface PcsfUseCase {
  id?: string;
  name?: PcsfFieldValue<string>;
  preconditions?: PcsfFieldValue<string>;
  postconditions?: PcsfFieldValue<string>;
  mainScenario?: PcsfFieldValue<string[]>;
  alternativeScenario?: PcsfFieldValue<string>;
}

export interface PcsfModule {
  id?: string;
  name?: PcsfFieldValue<string>;
  description?: PcsfFieldValue<string>;
  crudOperations?: PcsfFieldValue<string[]>;
  useCases?: PcsfUseCase[];
}

export interface PcsfBusinessRule {
  id?: string;
  description?: PcsfFieldValue<string>;
}

// ── Entities ─────────────────────────────────────────────────────────────────

export interface PcsfConstraints {
  required?: PcsfFieldValue<boolean>;
  unique?: PcsfFieldValue<boolean>;
  minLength?: PcsfFieldValue<number>;
  maxLength?: PcsfFieldValue<number>;
  pattern?: PcsfFieldValue<string>;
}

export interface PcsfAttribute {
  id?: string;
  name?: PcsfFieldValue<string>;
  columnName?: PcsfFieldValue<string>;
  javaType?: PcsfFieldValue<string>;
  mysqlType?: PcsfFieldValue<string>;
  constraints?: PcsfConstraints;
  showInList?: PcsfFieldValue<boolean>;
  showInForm?: PcsfFieldValue<boolean>;
}

export interface PcsfEntity {
  id?: string;
  name?: PcsfFieldValue<string>;
  tableName?: PcsfFieldValue<string>;
  primaryModuleId?: string;
  auditFields?: boolean;
  primaryKeyStrategy?: string;
  softDelete?: PcsfFieldValue<boolean>;
  attributes?: PcsfAttribute[];
}

export interface PcsfRelationship {
  id?: string;
  fromEntityId?: string;
  toEntityId?: string;
  cardinality?: PcsfFieldValue<string>;
  optionality?: PcsfFieldValue<string>;
  owningEntityId?: string;
  joinColumnName?: string;
  joinTableName?: string;
  label?: PcsfFieldValue<string>;
}

// ── Business rules / status machines / access control / errors ────────────────

export interface PcsfStatusTransition {
  from?: string;
  to?: string;
  trigger?: string;
  guard?: string;
  action?: string;
}

export interface PcsfStatusMachine {
  entityId?: string;
  states?: string[];
  transitions?: PcsfStatusTransition[];
  initialState?: string;
}

export interface PcsfAccessControlRule {
  id?: string;
  moduleId?: string;
  entityId?: string;
  operation?: string;
  allowedRoles?: PcsfFieldValue<string[]>;
}

export interface PcsfErrorCode {
  id?: string;
  code?: string;
  httpStatus?: number;
  messageTemplate?: string;
  exceptionClass?: string;
  moduleId?: string;
}

// ── API endpoints (Section 5b — AI-inferred) ───────────────────────────────────

export interface PcsfApiEndpoint {
  id?: string;
  moduleId?: string;
  httpMethod?: string;
  path?: string;
  operationId?: string;
  summary?: string;
  requestBodyEntityId?: string;
  responseEntityId?: string;
  requiredRoles?: string[];
  paginated?: boolean;
  requiresAuth?: boolean;
}

// ── Non-functional requirements ────────────────────────────────────────────────

export interface PcsfNonFunctionalRequirements {
  concurrentUsers?: PcsfFieldValue<number>;
  targetResponseTimeMs?: PcsfFieldValue<number>;
  dataVolumeDescription?: PcsfFieldValue<string>;
  availabilityTarget?: PcsfFieldValue<string>;
  securityDepth?: PcsfFieldValue<string>;
  locale?: PcsfFieldValue<string>;
}

// ── Public access ────────────────────────────────────────────────────────────

export interface PcsfPublicAccess {
  hasPublicActor?: PcsfFieldValue<boolean>;
  publicPaths?: string[];
}

// ── Hardcoded technical config (Sections 8-10) ─────────────────────────────────

export interface PcsfApiConfig {
  versionPrefix?: string;
  jwtAccessTokenValidityMs?: number;
  jwtRefreshTokenValidityMs?: number;
  rateLimitPerMinute?: number;
  corsAllowedOriginsDev?: string;
  defaultPageSize?: number;
  maxPageSize?: number;
}

export interface PcsfDatabaseConfig {
  name?: string;
  user?: string;
  charset?: string;
  collation?: string;
}

export interface PcsfInfrastructureConfig {
  backendPort?: number;
  frontendPort?: number;
  diagramRenderer?: string;
  krokiInternalUrl?: string;
  deploymentTarget?: string;
}

// ── User interface ──────────────────────────────────────────────────────────

export interface PcsfColours {
  primary?: string;
  secondary?: string;
  accent?: string;
  background?: string;
  surface?: string;
  error?: string;
  warning?: string;
  success?: string;
  info?: string;
  neutral?: string;
  text?: string;
  textOnPrimary?: string;
}

export interface PcsfNavItem {
  label?: string;
  routePath?: string;
  icon?: string;
  visibleToRoles?: string[];
  moduleId?: string;
}

export interface PcsfTableColumn {
  attributeId?: string;
  headerLabel?: string;
  sortable?: boolean;
}

export interface PcsfFormField {
  attributeId?: string;
  label?: string;
  controlType?: string;
}

export interface PcsfScreen {
  name?: string;
  type?: string;
  entityId?: string;
  moduleId?: string;
  routePath?: string;
  requiredRoles?: string[];
  tableColumns?: PcsfTableColumn[];
  formFields?: PcsfFormField[];
}

export interface PcsfUserInterface {
  colours?: PcsfColours;
  screens?: PcsfScreen[];
  navigation?: PcsfNavItem[];
}

export interface PcsfConditionalFlag {
  triggered?: boolean;
  required?: PcsfFieldValue<boolean>;
}

export interface PcsfConditionalFeatures {
  fileUpload?: PcsfConditionalFlag;
  dataExport?: PcsfConditionalFlag;
  searchFilter?: PcsfConditionalFlag;
  multiTenancy?: PcsfConditionalFlag;
}

// ── Validation ───────────────────────────────────────────────────────────────

export interface PcsfValidation {
  completenessScore?: number;
  generationReady?: boolean;
  missingMandatoryItems?: string[];
  pendingConditionalItems?: string[];
  pendingInferredItems?: string[];
  warnings?: string[];
  errors?: string[];
}

export interface PcsfData {
  project?: PcsfProject;
  actors?: PcsfActor[];
  publicAccess?: PcsfPublicAccess;
  modules?: PcsfModule[];
  conditionalFeatures?: PcsfConditionalFeatures;
  entities?: PcsfEntity[];
  relationships?: PcsfRelationship[];
  businessRules?: PcsfBusinessRule[];
  statusMachines?: PcsfStatusMachine[];
  accessControlRules?: PcsfAccessControlRule[];
  errorCodes?: PcsfErrorCode[];
  endpoints?: PcsfApiEndpoint[];
  nonFunctionalRequirements?: PcsfNonFunctionalRequirements;
  userInterface?: PcsfUserInterface;
  apiConfig?: PcsfApiConfig;
  databaseConfig?: PcsfDatabaseConfig;
  infrastructureConfig?: PcsfInfrastructureConfig;
  validation?: PcsfValidation;
}
