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

export interface PcsfEntity {
  id?: string;
  name?: PcsfFieldValue<string>;
}

export interface PcsfApiConfig {
  baseUrl?: string;
  authType?: string;
  endpoints?: unknown[];
}

export interface PcsfDatabaseConfig {
  type?: string;
  host?: string;
  port?: number;
  name?: string;
  user?: string;
}

export interface PcsfNavItem {
  label?: PcsfFieldValue<string>;
  route?: PcsfFieldValue<string>;
}

export interface PcsfScreen {
  name?: PcsfFieldValue<string>;
  route?: PcsfFieldValue<string>;
}

export interface PcsfUserInterface {
  screens?: PcsfScreen[];
  navItems?: PcsfNavItem[];
}

export interface PcsfData {
  project?: PcsfProject;
  actors?: PcsfActor[];
  modules?: PcsfModule[];
  entities?: PcsfEntity[];
  businessRules?: PcsfBusinessRule[];
  userInterface?: PcsfUserInterface;
  apiConfig?: PcsfApiConfig;
  databaseConfig?: PcsfDatabaseConfig;
}
